#include <jni.h>
#include <dlfcn.h>
#include <unistd.h>
#include <fcntl.h>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>
#include <cstdio>

using JLI_Launch_t = int (*)(int, char**, int, const char**, int, const char**, const char*, const char*, const char*, const char*, int, int, int, int);

static std::string jstr(JNIEnv* env, jstring value) {
    if (!value) return {};
    const char* s = env->GetStringUTFChars(value, nullptr);
    std::string out = s ? s : "";
    if (s) env->ReleaseStringUTFChars(value, s);
    return out;
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

    if (javaHome.empty()) return -100;

    std::string libDir = javaHome + "/lib";
    std::string jliDir = javaHome + "/lib/jli";
    std::string serverDir = javaHome + "/lib/server";
    std::string ld = jliDir + ":" + serverDir + ":" + libDir;
    const char* oldLd = getenv("LD_LIBRARY_PATH");
    if (oldLd && *oldLd) ld += ":" + std::string(oldLd);

    setenv("JAVA_HOME", javaHome.c_str(), 1);
    setenv("LD_LIBRARY_PATH", ld.c_str(), 1);
    setenv("PATH", (javaHome + "/bin:/system/bin:/system/xbin").c_str(), 1);

    if (!logFile.empty()) {
        int fd = open(logFile.c_str(), O_CREAT | O_WRONLY | O_TRUNC, 0644);
        if (fd < 0) return -101;
        dup2(fd, STDOUT_FILENO);
        dup2(fd, STDERR_FILENO);
        close(fd);
    }

    const std::string jvmPath = serverDir + "/libjvm.so";
    void* jvmHandle = dlopen(jvmPath.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (!jvmHandle) {
        const char* err = dlerror();
        fprintf(stderr, "ERROR: failed to load libjvm.so: %s\n", err ? err : "unknown dlopen error");
        return -102;
    }

    const std::string jliPath = jliDir + "/libjli.so";
    void* handle = dlopen(jliPath.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (!handle) {
        const char* err = dlerror();
        fprintf(stderr, "ERROR: failed to load libjli.so: %s\n", err ? err : "unknown dlopen error");
        dlclose(jvmHandle);
        return -103;
    }

    fprintf(stdout, "Native launcher: libjvm.so loaded\n");
    fprintf(stdout, "Native launcher: libjli.so loaded\n");

    auto launch = reinterpret_cast<JLI_Launch_t>(dlsym(handle, "JLI_Launch"));
    if (!launch) {
        fprintf(stderr, "ERROR: JLI_Launch symbol was not found in libjli.so\n");
        dlclose(handle);
        dlclose(jvmHandle);
        return -104;
    }

    const jsize count = argsJ ? env->GetArrayLength(argsJ) : 0;
    std::vector<std::string> storage;
    std::vector<char*> argv;
    storage.reserve(static_cast<size_t>(count) + 1);
    argv.reserve(static_cast<size_t>(count) + 2);
    storage.emplace_back("java");
    argv.push_back(const_cast<char*>(storage.back().c_str()));

    for (jsize i = 0; i < count; ++i) {
        auto s = (jstring)env->GetObjectArrayElement(argsJ, i);
        storage.push_back(jstr(env, s));
        env->DeleteLocalRef(s);
        argv.push_back(const_cast<char*>(storage.back().c_str()));
    }

    if (!jar.empty()) {
        // Caller supplies -jar <path> in args; jar is kept as a dedicated
        // parameter so the native launcher can validate it before launch.
        if (access(jar.c_str(), R_OK) != 0) {
            dlclose(handle);
            dlclose(jvmHandle);
            return -105;
        }
    }

    argv.push_back(nullptr);

    const int result = launch(
        static_cast<int>(argv.size() - 1), argv.data(),
        0, nullptr, 0, nullptr,
        "21", "21", "java", "java",
        0, 0, 0, 0);

    dlclose(handle);
    dlclose(jvmHandle);
    return result;
}
