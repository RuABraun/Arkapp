#include <jni.h>
#include <string>
#include "logging_macros.h"
#include "RecEngine.h"
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>

extern "C" {

//JNIEXPORT void JNICALL Java_ark_ark_RecEngine_native_1settext(JNIEnv* env, jclass jcls) {//
//
//}

JNIEXPORT void JNICALL Java_ark_ark_Base_load(JNIEnv* env, jobject obj, jobject Amgr, jstring modeldir) {

    AAssetManager *mgr = AAssetManager_fromJava(env, Amgr);

    AAssetDir* assetDir = AAssetManager_openDir(mgr, "model");
    const char* filename = (const char*)NULL;
    LOGI("IN LOAD FUNCTION");
    size_t BUFSZ = 1000 * 512;
    const char* rootdir = (env)->GetStringUTFChars(modeldir, 0);
    while ((filename = AAssetDir_getNextFileName(assetDir)) != NULL) {

        LOGI("FILENAME: %s", filename);

        char fin[7 + strlen(filename)];
        strcpy(fin, "model/");
        strcat(fin, filename);

        char fpath[1 + strlen(rootdir) + strlen(filename)];
        strcpy(fpath, rootdir);
        strcat(fpath, filename);

        int nb_read = 0;
        char* buf = new char[BUFSZ];

        AAsset* file = AAssetManager_open(mgr, fin, AASSET_MODE_STREAMING);

        FILE* out = fopen(fpath, "w");
        if (!out)
            LOGE("ERROR OPENING FILE");
        while ((nb_read = AAsset_read(file, buf, BUFSZ)) > 0) {
            fwrite(buf, nb_read, 1, out);
        }
        delete[] buf;
        fclose(out);
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

JNIEXPORT jstring JNICALL Java_ark_ark_RecEngine_native_1getText(JNIEnv* env, jclass, jlong engineHandle) {
    RecEngine *engine = (RecEngine*) engineHandle;
    if (engine == nullptr) {
        LOGE("Engine handle is invalid, call createHandle() to create a new one");
        return env->NewStringUTF("!ERROR");
    }
    jstring jstr = env->NewStringUTF(engine->get_text());
    return jstr;
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
JNIEXPORT void JNICALL
Java_ark_ark_RecEngine_native_1transcribe(JNIEnv *env, jclass, jstring jwavpath, jstring jmodeldir, jstring jctm) {
    const char* cstr = env->GetStringUTFChars(jwavpath, NULL);
    std::string wavpath = std::string(cstr);
    const char* cstr2 = env->GetStringUTFChars(jmodeldir, NULL);
    std::string modeldir = std::string(cstr2);
    const char* cstr3 = env->GetStringUTFChars(jctm, NULL);
    std::string ctm = std::string(cstr3);
    RecEngine::transcribe_file(wavpath, modeldir, ctm);
}
}