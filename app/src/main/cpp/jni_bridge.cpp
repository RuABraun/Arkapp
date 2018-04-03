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

    const char* rootdir = "/sdcard/Ark/";
    while ((filename = AAssetDir_getNextFileName(assetDir)) != NULL) {

        LOGI("FILENAME: %s", filename);

        if (strcmp(filename, "fbank.conf") == 0) {
            char fin[6 + strlen(filename)];

            strcpy(fin, "model/");
            strcat(fin, filename);
            AAsset* file = AAssetManager_open(mgr, fin, AASSET_MODE_BUFFER);
            size_t fileLength = AAsset_getLength(file);
            char* buf = new char[fileLength+1];
            AAsset_read(file, buf, fileLength);
            buf[fileLength] = '\0';
            LOGI("%s", buf);
            delete[] buf;
            AAsset_close(file);


            /*char fpath[strlen(rootdir) + strlen(filename)];
            strcpy(fpath, rootdir);
            strcat(fpath, filename);
            LOGI("FILEPATH: %s", fpath);

            int nb_read = 0;
            size_t BUFSZ = 1000 * 512;
            char* buf = new char[BUFSZ];
            LOGI("test");
            AAsset* file = AAssetManager_open(mgr, filename, AASSET_MODE_BUFFER);
            LOGI("test1");
            FILE* out = fopen(fpath, "w");
            if (!out)
                LOGE("ERROR OPENING FILE");
            LOGI("test2");
            AAsset_read(file, buf, BUFSZ);
            LOGI("test2a");
            LOGI("%d", nb_read);
            fwrite(buf, nb_read, 1, out);
            while (nb_read > 0) {
                nb_read = AAsset_read(file, buf, BUFSZ);
                LOGI("test2b");
                LOGI("%d", nb_read);
                fwrite(buf, nb_read, 1, out);
            }
            LOGI("test3");
            delete[] buf;
            fclose(out);
            AAsset_close(file);*/
        }
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