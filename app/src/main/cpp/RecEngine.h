#ifndef ARK_RECENGINE_H
#define ARK_RECENGINE_H


#include<oboe/Oboe.h>
#include <thread>
#include <array>
#include <mutex>
#include <fstream>
#include <soxr.h>

class RecEngine : oboe::AudioStreamCallback {
public:
    RecEngine();

    ~RecEngine();

    void setDeviceId(int32_t deviceId);

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *audioStream, void *audioData,
                                          int32_t numFrames);

private:
    int32_t mRecDeviceId = oboe::kUnspecified;
    int32_t mSampleRate;
    int32_t mFramesPerBurst = 2048;
    unsigned mChannelCount;
    bool mIsfloat;
    oboe::AudioStream *mRecStream;

    size_t data_chunk_pos, odone, frames_out;
    std::ofstream f;
    int32_t fin_sample_rate = 16000;
    float* fp_audio_in;
    float* resamp_audio;  // resampled audio data
    soxr_error_t soxr_error;
    soxr_t soxr;

    std::ofstream f2;
    size_t data_chunk_pos2;

    std::mutex mRestartingLock;

    void createRecStream();
    void closeOutputStream();
    void restartStream();
};

#endif //ARK_RECENGINE_H
