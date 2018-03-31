#include <jni.h>
#include <string>
#include "logging_macros.h"
#include "RecEngine.h"


extern "C" {

JNIEXPORT jlong JNICALL
Java_ark_ark_RecEngine_native_1createEngine(JNIEnv *env, jclass) {
    RecEngine *engine = new(std::nothrow) RecEngine();
    return (jlong) engine;
}

JNIEXPORT void JNICALL
Java_ark_ark_RecEngine_native_1deleteEngine(
        JNIEnv *env,
        jclass,
        jlong engineHandle) {

    delete (RecEngine *) engineHandle;
}

JNIEXPORT void JNICALL
Java_ark_ark_RecEngine_native_1setAudioDeviceId(
        JNIEnv *env,
        jclass,
        jlong engineHandle,
        jint deviceId) {

    RecEngine *engine = (RecEngine *) engineHandle;
    if (engine == nullptr) {
        LOGE("Engine handle is invalid, call createHandle() to create a new one");
        return;
    }
    engine->setDeviceId(deviceId);
}
}