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
    void* jliHandle = nullptr;
    const std::string jliPath = jliDir + "/libjli.so";
    if (!loadLib(jliPath, &jliHandle)) return -103;

    void* jvmHandle = nullptr;
    const std::string jvmPath = serverDir + "/libjvm.so";
    if (!loadLib(jvmPath, &jvmHandle)) {
        dlclose(jliHandle);
        return -104;
    }

    auto createJvm = reinterpret_cast<JNI_CreateJavaVM_t>(dlsym(jvmHandle, "JNI_CreateJavaVM"));
    if (!createJvm) {
        fprintf(stderr, "ERROR: JNI_CreateJavaVM symbol was not found in libjvm.so\n");
        dlclose(jvmHandle);
        dlclose(jliHandle);
        return -105;
    }

    std::string classPath = "-Djava.class.path=" + jar;
    std::string javaHomeOpt = "-Djava.home=" + javaHome;
    std::string userDirOpt = "-Duser.dir=" + workDir;
    std::string tmpDir = "-Djava.io.tmpdir=" + workDir + "/tmp";
    mkdir((workDir + "/tmp").c_str(), 0755);

    JavaVMOption options[4];
    options[0].optionString = const_cast<char*>(javaHomeOpt.c_str());
    options[1].optionString = const_cast<char*>(classPath.c_str());
    options[2].optionString = const_cast<char*>(userDirOpt.c_str());
    options[3].optionString = const_cast<char*>(tmpDir.c_str());

    JavaVMInitArgs vmArgs{};
    vmArgs.version = JNI_VERSION_1_6;
    vmArgs.nOptions = 4;
    vmArgs.options = options;
    vmArgs.ignoreUnrecognized = JNI_TRUE;

    JavaVM* vm = nullptr;
    JNIEnv* jni = nullptr;
    fprintf(stdout, "Native launcher: creating Java 21 VM directly (JNI_CreateJavaVM)…\n");
    jint rc = createJvm(&vm, reinterpret_cast<void**>(&jni), &vmArgs);
    if (rc != JNI_OK || !vm || !jni) {
        fprintf(stderr, "ERROR: JNI_CreateJavaVM failed with code %d\n", rc);
        dlclose(jvmHandle);
        dlclose(jliHandle);
        return -106;
    }

    fprintf(stdout, "Native launcher: Java VM created successfully.\n");

    // Current Geyser Standalone bootstrap main class.
    jclass mainClass = jni->FindClass("org/geysermc/geyser/platform/standalone/GeyserStandaloneBootstrap");
    if (!mainClass) {
        jni->ExceptionDescribe();
        jni->ExceptionClear();
        fprintf(stderr, "ERROR: Could not find GeyserStandaloneBootstrap in Geyser jar.\n");
        vm->DestroyJavaVM();
        dlclose(jvmHandle);
        dlclose(jliHandle);
        return -107;
    }

    jmethodID mainMethod = jni->GetStaticMethodID(mainClass, "main", "([Ljava/lang/String;)V");
    if (!mainMethod) {
        jni->ExceptionDescribe();
        jni->ExceptionClear();
        fprintf(stderr, "ERROR: GeyserStandaloneBootstrap.main(String[]) was not found.\n");
        vm->DestroyJavaVM();
        dlclose(jvmHandle);
        dlclose(jliHandle);
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
        dlclose(jvmHandle);
        dlclose(jliHandle);
        return -109;
    }

    fprintf(stdout, "Geyser main returned; shutting down Java VM.\n");
    jint destroyRc = vm->DestroyJavaVM();
    dlclose(jvmHandle);
    dlclose(jliHandle);
    return destroyRc == JNI_OK ? 0 : destroyRc;
}
