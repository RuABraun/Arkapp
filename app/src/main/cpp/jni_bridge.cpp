#include <jni.h>
#include <string>
#include "logging_macros.h"
#include "RecEngine.h"
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>

extern "C" {

JNIEXPORT void JNICALL Java_ark_ark_Base_load(JNIEnv* env, jobject obj, jobject Amgr) {
    AAssetManager *mgr = AAssetManager_fromJava(env, Amgr);

    AAssetDir* assetDir = AAssetManager_openDir(mgr, "model");
    const char* filename = (const char*)NULL;
    LOGI("IN LOAD FUNCTION");
    while ((filename = AAssetDir_getNextFileName(assetDir)) != NULL) {
        LOGI("FILENAME: %s", filename);
        AAsset* file = AAssetManager_open(mgr, filename, AASSET_MODE_BUFFER);
        size_t fileLength = AAsset_getLength(file);

        char* fileContent = new char[fileLength+1];
        AAsset_read(file, fileContent, fileLength);
        fileContent[fileLength] = '\0';
        delete[] fileContent;
        AAsset_close(file);
    }
    AAssetDir_close(assetDir);
}

JNIEXPORT jlong JNICALL
Java_ark_ark_RecEngine_native_1createEngine(JNIEnv *env, jclass, jstring fname) {
    const char* cstr = env->GetStringUTFChars(fname, NULL);
    std::string fnamenew = std::string(cstr);
    RecEngine *engine = new(std::nothrow) RecEngine(fnamenew);
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