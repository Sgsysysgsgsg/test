#include <jni.h>
#include <dlfcn.h>
#include <unistd.h>
#include <fcntl.h>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>
#include <cstdio>
#include <limits.h>
#include <sys/stat.h>
#include <dirent.h>
#include <set>
#include <algorithm>

using JNI_CreateJavaVM_t = jint (*)(JavaVM **pvm, void **penv, void *args);
using JNI_GetDefaultJavaVMInitArgs_t = jint (*)(void *args);

static std::string jstr(JNIEnv* env, jstring value) {
    if (!value) return {};
    const char* s = env->GetStringUTFChars(value, nullptr);
    std::string out = s ? s : "";
    if (s) env->ReleaseStringUTFChars(value, s);
    return out;
}

static bool loadLib(const std::string& path, void** handleOut) {
    void* h = dlopen(path.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (!h) {
        const char* err = dlerror();
        fprintf(stderr, "ERROR: dlopen failed for %s: %s\n", path.c_str(), err ? err : "unknown");
        return false;
    }
    *handleOut = h;
    fprintf(stdout, "Native launcher: loaded %s\n", path.c_str());
    return true;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_eyad_geysermobile_GeyserService_nativeLaunchJava(
        JNIEnv* env, jobject,
        jstring javaHomeJ,
        jstring jarJ,
        jstring logFileJ,
        jobjectArray argsJ) {
    const std::string javaHome = jstr(env, javaHomeJ);
    const std::string jar = jstr(env, jarJ);
    const std::string logFile = jstr(env, logFileJ);

    if (javaHome.empty() || jar.empty()) return -100;

    if (!logFile.empty()) {
        int fd = open(logFile.c_str(), O_CREAT | O_WRONLY | O_TRUNC, 0644);
        if (fd < 0) return -101;
        dup2(fd, STDOUT_FILENO);
        dup2(fd, STDERR_FILENO);
        close(fd);
    }

    // Geyser expects its config and generated files relative to the JAR directory.
    std::string workDir = jar.substr(0, jar.find_last_of('/'));
    if (workDir.empty()) workDir = ".";
    chdir(workDir.c_str());

    const std::string libDir = javaHome + "/lib";
    const std::string serverDir = javaHome + "/lib/server";
    const std::string jliDir = javaHome + "/lib";

    std::string ld = jliDir + ":" + serverDir + ":" + libDir;
    const char* oldLd = getenv("LD_LIBRARY_PATH");
    if (oldLd && *oldLd) ld += ":" + std::string(oldLd);
    setenv("JAVA_HOME", javaHome.c_str(), 1);
    setenv("LD_LIBRARY_PATH", ld.c_str(), 1);
    setenv("PATH", (javaHome + "/bin:/system/bin:/system/xbin").c_str(), 1);

    // IMPORTANT: Do NOT call JLI_Launch here. OpenJDK's JLI launcher may exec()
    // the java executable again while preparing LD_LIBRARY_PATH. Android blocks
    // exec of files under /data/user/0/.../files, producing Permission denied.
    // Instead, create the JVM directly through JNI_CreateJavaVM. This stays inside
    // the already-running native process and never executes runtime/bin/java.
    // Android's linker namespace does not automatically search the extracted JRE
    // directory for DT_NEEDED libraries.  In particular libnio.so needs libnet.so.
    // Pre-load the JRE's native libraries with RTLD_GLOBAL so their SONAMEs are
    // already visible when later libraries are resolved.
    std::vector<void*> nativeHandles;
    auto preload = [&](const std::string& path) -> bool {
        void* h = nullptr;
        if (loadLib(path, &h)) {
            nativeHandles.push_back(h);
            return true;
        }
        return false;
    };

    const std::string jliPath = jliDir + "/libjli.so";
    const std::string jvmPath = serverDir + "/libjvm.so";

    if (!preload(jliPath)) return -103;
    if (!preload(jvmPath)) return -104;

    // Core JDK libraries first; this resolves the common chain
    // libjvm -> libjava -> libnet -> libnio.
    const char* priority[] = {
        "libjava.so", "libverify.so", "libzip.so", "libjimage.so",
        "libnet.so", "libnio.so", "libextnet.so", "libsyslookup.so",
        "libmanagement.so", "libmanagement_ext.so", "libinstrument.so",
        "libjdwp.so", "libdt_socket.so", "libdt_shmem.so"
    };
    std::set<std::string> loadedNames = {"libjli.so", "libjvm.so"};
    for (const char* name : priority) {
        std::string path = libDir + "/" + name;
        struct stat st{};
        if (stat(path.c_str(), &st) == 0) {
            if (preload(path)) loadedNames.insert(name);
        }
    }

    // Some JRE builds contain additional native modules. Load any remaining
    // .so files in a few passes so dependencies become available before a
    // dependent library is retried. Failed optional modules are harmless.
    for (int pass = 0; pass < 4; ++pass) {
        bool progress = false;
        DIR* dir = opendir(libDir.c_str());
        if (!dir) break;
        while (dirent* ent = readdir(dir)) {
            std::string name = ent->d_name;
            if (name.size() < 3 || name.rfind(".so") != name.size() - 3) continue;
            if (loadedNames.count(name)) continue;
            std::string path = libDir + "/" + name;
            struct stat st{};
            if (stat(path.c_str(), &st) != 0) continue;
            if (preload(path)) {
                loadedNames.insert(name);
                progress = true;
            }
        }
        closedir(dir);
        if (!progress) break;
    }

    void* jvmHandle = nativeHandles.size() > 1 ? nativeHandles[1] : nullptr;
    if (!jvmHandle) return -104;

    auto createJvm = reinterpret_cast<JNI_CreateJavaVM_t>(dlsym(jvmHandle, "JNI_CreateJavaVM"));
    if (!createJvm) {
        fprintf(stderr, "ERROR: JNI_CreateJavaVM symbol was not found in libjvm.so\n");
        return -105;
    }

    std::string classPath = "-Djava.class.path=" + jar;
    std::string javaHomeOpt = "-Djava.home=" + javaHome;
    std::string userDirOpt = "-Duser.dir=" + workDir;
    std::string tmpDir = "-Djava.io.tmpdir=" + workDir + "/tmp";
    std::string nativeLibPath = "-Djava.library.path=" + libDir + ":" + serverDir;
    mkdir((workDir + "/tmp").c_str(), 0755);

    // Keep the embedded JVM conservative on Android. Geyser is a bridge, not
    // the Minecraft world server itself, so an unbounded desktop-style heap can
    // waste RAM and make OEM Android builds reclaim the service under pressure.
    const std::string heapMin = "-Xms64m";
    const std::string heapMax = "-Xmx512m";
    const std::string activeCpu = "-XX:ActiveProcessorCount=4";

    JavaVMOption options[8];
    options[0].optionString = const_cast<char*>(javaHomeOpt.c_str());
    options[1].optionString = const_cast<char*>(classPath.c_str());
    options[2].optionString = const_cast<char*>(userDirOpt.c_str());
    options[3].optionString = const_cast<char*>(tmpDir.c_str());
    options[4].optionString = const_cast<char*>(nativeLibPath.c_str());
    options[5].optionString = const_cast<char*>(heapMin.c_str());
    options[6].optionString = const_cast<char*>(heapMax.c_str());
    options[7].optionString = const_cast<char*>(activeCpu.c_str());

    JavaVMInitArgs vmArgs{};

    vmArgs.version = JNI_VERSION_1_6;
    vmArgs.nOptions = 8;
    vmArgs.options = options;
    vmArgs.ignoreUnrecognized = JNI_TRUE;

    JavaVM* vm = nullptr;
    JNIEnv* jni = nullptr;
    fprintf(stdout, "Native launcher: creating Java 21 VM directly (JNI_CreateJavaVM)…\n");
    jint rc = createJvm(&vm, reinterpret_cast<void**>(&jni), &vmArgs);
    if (rc != JNI_OK || !vm || !jni) {
        fprintf(stderr, "ERROR: JNI_CreateJavaVM failed with code %d\n", rc);
        return -106;
    }

    fprintf(stdout, "Native launcher: Java VM created successfully.\n");

    // Current Geyser Standalone bootstrap main class.
    jclass mainClass = jni->FindClass("org/geysermc/geyser/platform/standalone/GeyserStandaloneBootstrap");
    if (!mainClass) {
        fprintf(stderr, "ERROR: FindClass failed; this may be a classpath or native-library dependency error.\n");
        jni->ExceptionDescribe();
        jni->ExceptionClear();
        fprintf(stderr, "ERROR: Could not find GeyserStandaloneBootstrap in Geyser jar.\n");
        vm->DestroyJavaVM();
        return -107;
    }

    jmethodID mainMethod = jni->GetStaticMethodID(mainClass, "main", "([Ljava/lang/String;)V");
    if (!mainMethod) {
        jni->ExceptionDescribe();
        jni->ExceptionClear();
        fprintf(stderr, "ERROR: GeyserStandaloneBootstrap.main(String[]) was not found.\n");
        vm->DestroyJavaVM();
        return -108;
    }

    jclass stringClass = jni->FindClass("java/lang/String");
    jsize count = argsJ ? env->GetArrayLength(argsJ) : 0;
    // Ignore the launcher-style -jar <path> arguments because the JVM is already
    // loaded with the Geyser JAR as its classpath. Keep only application arguments.
    std::vector<std::string> appArgs;
    for (jsize i = 0; i < count; ++i) {
        jstring s = (jstring)env->GetObjectArrayElement(argsJ, i);
        std::string value = jstr(env, s);
        env->DeleteLocalRef(s);
        if (value == "-jar" && i + 1 < count) {
            ++i;
            continue;
        }
        if (value == jar) continue;
        appArgs.push_back(value);
    }

    jobjectArray mainArgs = jni->NewObjectArray(static_cast<jsize>(appArgs.size()), stringClass, nullptr);
    for (jsize i = 0; i < static_cast<jsize>(appArgs.size()); ++i) {
        jstring s = jni->NewStringUTF(appArgs[i].c_str());
        jni->SetObjectArrayElement(mainArgs, i, s);
        jni->DeleteLocalRef(s);
    }

    fprintf(stdout, "Native launcher: starting GeyserStandaloneBootstrap.main…\n");
    jni->CallStaticVoidMethod(mainClass, mainMethod, mainArgs);

    if (jni->ExceptionCheck()) {
        fprintf(stderr, "ERROR: Geyser Java main threw an exception:\n");
        jni->ExceptionDescribe();
        jni->ExceptionClear();
        vm->DestroyJavaVM();
        return -109;
    }

    fprintf(stdout, "Geyser main returned; shutting down Java VM.\n");
    jint destroyRc = vm->DestroyJavaVM();
    // Keep JRE native libraries loaded until the VM is fully destroyed.
    nativeHandles.clear();
    return destroyRc == JNI_OK ? 0 : destroyRc;
}
