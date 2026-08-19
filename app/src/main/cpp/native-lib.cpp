// Bridge JNI tra Kotlin e libprojectM.
// La scelta di QUALE preset caricare e QUANDO è gestita interamente in
// Kotlin (PlaybackController): qui ci limitiamo a inizializzare projectM,
// renderizzare i frame e caricare il file preset che ci viene indicato.

#include <jni.h>
#include <android/log.h>
#include <projectM-4/projectM.h>

#define LOG_TAG "ProjectMOverlay"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {
projectm_handle gProjectM = nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_asfaltosonoro_projectmoverlay_ProjectMBridge_nativeInit(JNIEnv *, jobject,
                                                                  jint width, jint height) {
    gProjectM = projectm_create();
    projectm_set_window_size(gProjectM, width, height);
    LOGI("projectM inizializzato (%dx%d)", width, height);
}

extern "C" JNIEXPORT void JNICALL
Java_com_asfaltosonoro_projectmoverlay_ProjectMBridge_nativeResize(JNIEnv *, jobject,
                                                                    jint width, jint height) {
    if (gProjectM) projectm_set_window_size(gProjectM, width, height);
}

extern "C" JNIEXPORT void JNICALL
Java_com_asfaltosonoro_projectmoverlay_ProjectMBridge_nativeRenderFrame(JNIEnv *, jobject) {
    if (gProjectM) projectm_opengl_render_frame(gProjectM);
}

extern "C" JNIEXPORT void JNICALL
Java_com_asfaltosonoro_projectmoverlay_ProjectMBridge_nativeLoadPresetFile(JNIEnv *env, jobject,
                                                                            jstring path,
                                                                            jboolean smooth) {
    if (!gProjectM) return;
    const char *p = env->GetStringUTFChars(path, nullptr);
    projectm_load_preset_file(gProjectM, p, smooth);
    env->ReleaseStringUTFChars(path, p);
}

// Durata (in secondi) del crossfade tra un preset e il successivo.
extern "C" JNIEXPORT void JNICALL
Java_com_asfaltosonoro_projectmoverlay_ProjectMBridge_nativeSetTransitionDuration(JNIEnv *, jobject,
                                                                                   jfloat seconds) {
    if (gProjectM) projectm_set_soft_cut_duration(gProjectM, seconds);
}

// samples: PCM int16 interleaved
extern "C" JNIEXPORT void JNICALL
Java_com_asfaltosonoro_projectmoverlay_ProjectMBridge_nativePcmAdd(JNIEnv *env, jobject,
                                                                    jshortArray samples,
                                                                    jint channels) {
    if (!gProjectM) return;
    jsize len = env->GetArrayLength(samples);
    jshort *buf = env->GetShortArrayElements(samples, nullptr);
    projectm_pcm_add_int16(gProjectM, reinterpret_cast<int16_t *>(buf),
                            len / channels, static_cast<projectm_channels>(channels));
    env->ReleaseShortArrayElements(samples, buf, JNI_ABORT);
}

extern "C" JNIEXPORT void JNICALL
Java_com_asfaltosonoro_projectmoverlay_ProjectMBridge_nativeDestroy(JNIEnv *, jobject) {
    if (gProjectM) projectm_destroy(gProjectM);
    gProjectM = nullptr;
}
