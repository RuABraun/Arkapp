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
    RecEngine(std::string fname);

    ~RecEngine();

    void setDeviceId(int32_t deviceId);

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *audioStream, void *audioData,
                                          int32_t numFrames);

    static void transcribe_file(std::string wavpath, std::string modeldir, std::string ctm);

private:
    int32_t mRecDeviceId = oboe::kUnspecified;
    int32_t mSampleRate;
    int32_t mFramesPerBurst = 8000;
    unsigned mChannelCount;
    bool mIsfloat;
    oboe::AudioStream *mRecStream;

    size_t data_chunk_pos, odone, frames_out;
    std::ofstream f;
    const static int32_t fin_sample_rate = 16000;
    float* fp_audio_in;
    float* resamp_audio;  // resampled audio data
    soxr_error_t soxr_error;
    soxr_t soxr;
    int32_t frames_written = 0;

    std::mutex mRestartingLock;

    void createRecStream(std::string fname);
    void closeOutputStream();
};

#endif //ARK_RECENGINE_H
