#include <jni.h>
#include <android/log.h>

#define LOG_TAG "NativePatcher"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// HDiffPatch “hpatchz” C API entry (from source build)
extern "C" int hpatchz(int argc, const char* argv[]);

extern "C"
JNIEXPORT jint JNICALL
Java_com_sohan_diutransportschedule_sync_ApkDownloadReceiver_00024NativePatcher_applyPatch(
        JNIEnv* env,
        jobject /*thiz*/,
        jstring oldApkPath,
        jstring patchPath,
        jstring outApkPath) {

    if (!oldApkPath || !patchPath || !outApkPath) return -10;

    const char* oldC = env->GetStringUTFChars(oldApkPath, nullptr);
    const char* patchC = env->GetStringUTFChars(patchPath, nullptr);
    const char* outC = env->GetStringUTFChars(outApkPath, nullptr);

    if (!oldC || !patchC || !outC) {
        if (oldC) env->ReleaseStringUTFChars(oldApkPath, oldC);
        if (patchC) env->ReleaseStringUTFChars(patchPath, patchC);
        if (outC) env->ReleaseStringUTFChars(outApkPath, outC);
        return -11;
    }

    // argv: hpatchz old.apk patch.patch out.apk
    const char* argv[4];
    argv[0] = "hpatchz";
    argv[1] = oldC;
    argv[2] = patchC;
    argv[3] = outC;

    int rc = -1;
    rc = hpatchz(4, argv);   // expect 0 on success

    env->ReleaseStringUTFChars(oldApkPath, oldC);
    env->ReleaseStringUTFChars(patchPath, patchC);
    env->ReleaseStringUTFChars(outApkPath, outC);

    if (rc != 0) LOGE("hpatchz failed rc=%d", rc);
    return (jint)rc;
}