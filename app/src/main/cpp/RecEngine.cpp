#include <cstring>
#include <string>
#include <cmath>
#include <iostream>
#include "logging_macros.h"
#include "RecEngine.h"
#include <android/log.h>


namespace little_endian_io {
    template <typename Word>
    std::ostream& write_word( std::ostream& outs, Word value, unsigned size = sizeof( Word ) ) {
        for (; size; --size, value >>= 8)
            outs.put( static_cast <char> (value & 0xFF) );
        return outs;
    }

    std::ostream& write_float(std::ostream& outs, float value, unsigned size) {
        outs.write((char*) &value, size);
        return outs;
    }
}
using namespace little_endian_io;

RecEngine::RecEngine() {
    createRecStream();
}

RecEngine::~RecEngine() {
    closeOutputStream();
}

void RecEngine::createRecStream() {
    oboe::AudioStreamBuilder builder;
    builder.setSharingMode(oboe::SharingMode::Exclusive);
    builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
    builder.setCallback(this);
    builder.setDirection(oboe::Direction::Input);
    builder.setFramesPerCallback(mFramesPerBurst);

    oboe::Result result = builder.openStream(&mRecStream);

    if (result == oboe::Result::OK && mRecStream != nullptr) {
        mSampleRate = mRecStream->getSampleRate();
        oboe::AudioFormat mFormat = mRecStream->getFormat();
        mChannelCount = mRecStream->getChannelCount();

        LOGI("Input sample rate: %d", mSampleRate);
        LOGI("AudioStream input format is %s", oboe::convertToText(mFormat));
        LOGI("Channel count: %d", mChannelCount);

        int32_t bufferSize = mRecStream->getBufferSizeInFrames();
        LOGI("mFramesPerBurst %d, bufferSize %d", mFramesPerBurst, bufferSize);

        if (mFormat == oboe::AudioFormat::Float) {
            mIsfloat = true;
        } else {
            mIsfloat = false;
        }

        // Soxr prep
        soxr = soxr_create(mSampleRate, fin_sample_rate, mChannelCount,
                           &soxr_error, NULL, NULL, NULL);
        if (soxr_error) LOGE("Error creating soxr resampler.");

        if (!mIsfloat)
            fp_audio_in = static_cast<float*>(malloc(sizeof(float) * mFramesPerBurst * mChannelCount));
        frames_out = static_cast<size_t>(mFramesPerBurst * fin_sample_rate / mSampleRate + .5);
        resamp_audio = static_cast<float*>(malloc(sizeof(float) * frames_out * mChannelCount));

        // Wav header
        f.open("/storage/emulated/0/Android/data/ark.ark/files/Music/example.wav", std::ios::binary);
        f << "RIFF----WAVEfmt ";
        write_word(f, 16, 4);  // no extension data
        int16_t bytes_perSample = 2;
        write_word(f, 1, 2);  // PCM - int samples
        write_word(f, mChannelCount, 2);
        write_word(f, fin_sample_rate, 4);
        write_word(f, fin_sample_rate * mChannelCount * bytes_perSample, 4 );  // (Sample Rate * Channels * BytesPerSample)
        write_word(f, mChannelCount * bytes_perSample, 2);  // data block size (size of two integer samples, one for each channel, in bytes)
        write_word(f, bytes_perSample * 8, 2);  // number of bits per sample (use a multiple of 8)
        data_chunk_pos = f.tellp();
        f << "data----";  // (chunk size to be filled in later)

        result = mRecStream->requestStart();
        if (result != oboe::Result::OK) {
            LOGE("Error starting stream. %s", oboe::convertToText(result));
        }
    } else {
        LOGE("Failed to create stream. Error: %s", oboe::convertToText(result));
    }
}

void RecEngine::closeOutputStream() {

    if (mRecStream != nullptr) {

        oboe::Result result = mRecStream->requestStop();

        soxr_delete(soxr);
        free(resamp_audio);
        if(!mIsfloat)
            free(fp_audio_in);

        // Finishing wav write
        size_t file_length = f.tellp();
        f.seekp(data_chunk_pos + 4);
        write_word(f, file_length - data_chunk_pos + 8);
        f.seekp(0 + 4);
        write_word(f, file_length - 8, 4);
        f.close();

        if (result != oboe::Result::OK) {
            LOGE("Error stopping output stream. %s", oboe::convertToText(result));
        }

        result = mRecStream->close();
        if (result != oboe::Result::OK) {
            LOGE("Error closing output stream. %s", oboe::convertToText(result));
        }
    }
}

oboe::DataCallbackResult RecEngine::onAudioReady(oboe::AudioStream *audioStream, void *audioData, int32_t numFrames) {


    if(mIsfloat) {
        float* audio_data = static_cast<float*>(audioData);
        soxr_error = soxr_process(soxr, audio_data, numFrames, NULL, resamp_audio, frames_out,
                                  &odone);
    } else {
        int16_t* audio_data = static_cast<int16_t*>(audioData);
        int32_t num_samples_in = numFrames * mChannelCount;
        for (int i = 0; i < num_samples_in; i++) {
            fp_audio_in[i] = static_cast<float>(audio_data[i]);
        }
        soxr_error = soxr_process(soxr, fp_audio_in, numFrames, NULL, resamp_audio, frames_out,
                                  &odone);
    }

    int32_t num_samples_out = static_cast<int32_t>(frames_out*mChannelCount);
    for(int i=0;i < num_samples_out;i++) {
        int16_t val = static_cast<int16_t>(resamp_audio[i]);
        write_word(f, val, 2);
    }

    int32_t bufferSize = mRecStream->getBufferSizeInFrames();
    int32_t underrunCount = audioStream->getXRunCount();

    LOGI("numFrames %d, Underruns %d, buffer size %d, outframes %zu",
         numFrames, underrunCount, bufferSize, frames_out);

    return oboe::DataCallbackResult::Continue;
}

void RecEngine::setDeviceId(int32_t deviceId) {

    mRecDeviceId = deviceId;

    // If this is a different device from the one currently in use then restart the stream
    int32_t currentDeviceId = mRecStream->getDeviceId();
    if (deviceId != currentDeviceId) restartStream();
}

void RecEngine::restartStream() {

    LOGI("Restarting stream");

    if (mRestartingLock.try_lock()) {
        closeOutputStream();
        createRecStream();
        mRestartingLock.unlock();
    } else {
        LOGW("Restart stream operation already in progress - ignoring this request");
        // We were unable to obtain the restarting lock which means the restart operation is currently
        // active. This is probably because we received successive "stream disconnected" events.
        // Internal issue b/63087953
    }
}