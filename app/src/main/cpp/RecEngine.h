#ifndef ARK_RECENGINE_H
#define ARK_RECENGINE_H


#include<oboe/Oboe.h>
#include <thread>
#include <array>
#include <fstream>
#include <soxr.h>
#include <jni.h>

class RecEngine : oboe::AudioStreamCallback {
public:
    RecEngine(std::string fname);

    ~RecEngine();

    void setDeviceId(int32_t deviceId);

    const char* get_text();

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *audioStream, void *audioData,
                                          int32_t numFrames);

    static void transcribe_file(std::string wavpath, std::string modeldir, std::string ctm);

private:
    std::string outtext;
    int nrmb = 0;

    int32_t mRecDeviceId = oboe::kUnspecified;
    int32_t mSampleRate;
    int32_t mFramesPerBurst = 8000;
    unsigned mChannelCount;
    bool mIsfloat;
    oboe::AudioStream *mRecStream;

    size_t data_chunk_pos, odone, frames_out, frames_min;
    std::ofstream f;
    const static int32_t fin_sample_rate = 16000;
    float* fp_audio_in;
    float* resamp_audio;  // resampled audio data
    soxr_error_t soxr_error;
    soxr_t soxr;

    void createRecStream(std::string fname);
    void closeOutputStream();
};

#endif //ARK_RECENGINE_H
