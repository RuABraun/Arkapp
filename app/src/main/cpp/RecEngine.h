#ifndef ARK_RECENGINE_H
#define ARK_RECENGINE_H


#include<oboe/Oboe.h>
#include <thread>
#include <array>
#include <fstream>
#include <soxr.h>
#include <jni.h>
#include "feat/wave-reader.h"
#include "fstext/fstext-lib.h"
#include "lat/lattice-functions.h"
#include "util/kaldi-thread.h"
#include "online2/online-nnet3-decoding.h"
#include "online2/online-nnet2-feature-pipeline.h"
#include "online2/onlinebin-util.h"
#include "online2/online-timing.h"
#include "lat/word-align-lattice-lexicon.h"
#include "nnet3/nnet-utils.h"

class RecEngine : oboe::AudioStreamCallback {
public:
    RecEngine(std::string fname, std::string modeldir);

    ~RecEngine();

    void setDeviceId(int32_t deviceId);

    const char* get_text();

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *audioStream, void *audioData,
                                          int32_t numFrames);

    static void transcribe_file(std::string wavpath, std::string modeldir, std::string ctm);

private:
    std::string outtext;
    int callb_cnt = 0;

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

    // ASR vars
    kaldi::nnet3::AmNnetSimple am_nnet;
    kaldi::OnlineNnet2FeaturePipelineConfig feature_opts;
    kaldi::nnet3::NnetSimpleLoopedComputationOptions decodable_opts;
    kaldi::LatticeFasterDecoderConfig decoder_opts;
    fst::Fst<fst::StdArc>* decode_fst;
    kaldi::OnlineNnet2FeaturePipelineInfo* feature_info = NULL;
    kaldi::nnet3::DecodableNnetSimpleLoopedInfo* decodable_info = NULL;
    kaldi::SingleUtteranceNnet3Decoder* decoder = NULL;
    kaldi::OnlineNnet2FeaturePipeline* feature_pipeline = NULL;
    fst::SymbolTable* word_syms;
    kaldi::BaseFloat fin_sample_rate_fp;
    kaldi::TransitionModel trans_model;

    void createRecStream(std::string fname, std::string modeldir);
    void closeOutputStream();
};

#endif //ARK_RECENGINE_H
