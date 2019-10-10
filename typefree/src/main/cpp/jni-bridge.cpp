#include <jni.h>
#include <string>
#include "logging_macros.h"
#include "RecEngine.h"
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <bugsnag.h>


extern "C" {

JNIEXPORT void JNICALL Java_typefree_typefree_MainActivity_native_1load(JNIEnv* env, jobject, jobject Amgr, jstring modeldir) {

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
Java_typefree_typefree_RecEngine_native_1createEngine(JNIEnv *env, jobject, jstring jmodeldir,
                                                      jintArray exclusiveCores) {
//    exclusiveCores.
    std::vector<int> v;
    jsize length = env->GetArrayLength(exclusiveCores);
    jboolean isCopy;
    jint* elements = env->GetIntArrayElements(exclusiveCores, &isCopy);
    for (int i = 0; i < length; i++) {
        v.push_back(elements[i]);
    }

    const char* cstrb = env->GetStringUTFChars(jmodeldir, NULL);
    std::string modeldir = std::string(cstrb);
    RecEngine* engine = new (std::nothrow) RecEngine(modeldir, v);
    return (jlong) engine;
}

JNIEXPORT void JNICALL
Java_typefree_typefree_RecEngine_native_1deleteEngine(
        JNIEnv *env,
        jobject,
        jlong engineHandle) {

    delete (RecEngine *) engineHandle;
}

JNIEXPORT void JNICALL
Java_typefree_typefree_RecEngine_native_1transcribe_1stream(JNIEnv *env, jobject, jlong engineHandle, jstring jwavpath) {
    const char* cstr = env->GetStringUTFChars(jwavpath, NULL);
    std::string wavpath = std::string(cstr);

    RecEngine* engine = (RecEngine*) engineHandle;
    if (engine == nullptr) {
        LOGE("Engine handle is invalid");
        return;
    }

    engine->transcribe_stream(wavpath);
}

JNIEXPORT jlong JNICALL
Java_typefree_typefree_RecEngine_native_1stop_1trans_1stream(JNIEnv *env, jobject, jlong engineHandle) {
    RecEngine* engine = (RecEngine*) engineHandle;
    if (engine == nullptr) {
        LOGE("Engine handle is invalid");
        return 0;
    }
    int num_out_frames = engine->stop_trans_stream();
    return num_out_frames;
}

JNIEXPORT jint JNICALL
Java_typefree_typefree_RecEngine_native_1transcribe_1file(JNIEnv *env, jobject, jlong engineHandle, jstring jwavpath, jstring jfpath) {
    const char* cstr = env->GetStringUTFChars(jwavpath, NULL);
    std::string wavpath = std::string(cstr);
    const char* cstr3 = env->GetStringUTFChars(jfpath, NULL);
    std::string fpath = std::string(cstr3);
    RecEngine* engine = (RecEngine*) engineHandle;
    if (engine == nullptr) {
        LOGE("Engine handle is invalid");
        return 1;
    }
    int return_code = engine->transcribe_file(wavpath, fpath);
    if (return_code != 0) {
        bugsnag_notify_env(env, "Transcription error", "Failed to transcribe file output path", BSG_SEVERITY_WARN);
    }
    return return_code;
}

JNIEXPORT jstring JNICALL Java_typefree_typefree_RecEngine_native_1getText(JNIEnv* env, jobject, jlong engineHandle) {
    RecEngine *engine = (RecEngine*) engineHandle;
    if (engine == nullptr) {
        LOGE("Engine handle is invalid, call createHandle() to create a new one");
        return env->NewStringUTF("!ERROR");
    }
    jstring jstr = env->NewStringUTF(engine->get_text());
    return jstr;
}

JNIEXPORT jstring JNICALL Java_typefree_typefree_RecEngine_native_1getConstText(JNIEnv* env, jobject, jlong engineHandle) {
    RecEngine *engine = (RecEngine *) engineHandle;
    if (engine == nullptr) {
        LOGE("Engine handle is invalid, call createHandle() to create a new one");
        return env->NewStringUTF("!ERROR");
    }
    int size = 0;
    const char *ctext = engine->get_const_text(&size);
    jstring jstr;
    if (size == -1) {
        const char *str = "";
        jstr = env->NewStringUTF(str);
    } else {
        jstr = env->NewStringUTF(ctext);
    }
    return jstr;
}

JNIEXPORT void JNICALL
Java_typefree_typefree_RecEngine_native_1setAudioDeviceId(
        JNIEnv *env,
        jobject,
        jlong engineHandle,
        jint deviceId) {

    RecEngine *engine = (RecEngine *) engineHandle;
    if (engine == nullptr) {
        LOGE("Engine handle is invalid, call createHandle() to create a new one");
        return;
    }
    engine->setDeviceId(deviceId);
}

JNIEXPORT jint JNICALL
Java_typefree_typefree_RecEngine_native_1convertAudio(
        JNIEnv *env,
        jobject,
        jlong engineHandle,
        jstring jaudiopath,
        jstring jwavpath) {

    RecEngine *engine = (RecEngine *) engineHandle;
    if (engine == nullptr) {
        LOGE("Engine handle is invalid, call createHandle() to create a new one");
        return -1;
    }
    const char* audiopath = env->GetStringUTFChars(jaudiopath, NULL);
    const char* wavpath = env->GetStringUTFChars(jwavpath, NULL);
    return engine->convert_audio(audiopath, wavpath);

}

JNIEXPORT jint JNICALL
Java_typefree_typefree_RecEngine_native_1pauseSwitch(JNIEnv *env, jobject, jlong engineHandle) {
    RecEngine *engine = (RecEngine *) engineHandle;
    if (engine == nullptr) {
        LOGE("Engine handle is invalid, call createHandle() to create a new one");
        return -1;
    }
    if (!engine->is_recognition_paused.load()) {
        engine->pause_stream();
    } else {
        engine->resume_stream();
    }
    return 0;
}
}