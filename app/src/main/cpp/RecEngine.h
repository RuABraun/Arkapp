#ifndef ARK_RECENGINE_H
#define ARK_RECENGINE_H


#include <oboe/Oboe.h>
#include <thread>
#include <array>
#include <fstream>
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
#include "lat/compose-lattice-pruned.h"
#include "rnnlm/rnnlm-lattice-rescoring.h"
#include "rnnlm/rnnlm-utils.h"
#include "lm/const-arpa-lm.h"
#include "nnet3/nnet-utils.h"
#include <thread>
#include "base/timer.h"


class RecEngine : oboe::AudioStreamCallback {
public:
    RecEngine(std::string modeldir);

    ~RecEngine();

    void setupLex(std::string wsyms, std::string align_lex);

    void setupRnnlm(std::string modeldir);

    void setDeviceId(int32_t deviceId);

    const char* get_text();
    void reset_text();

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *audioStream, void *audioData,
                                          int32_t numFrames);

    void transcribe_stream(std::string wavpath);

    int stop_trans_stream();

    void transcribe_file(std::string wavpath, std::string ctm);

    std::string prettify_text(std::vector<int32>& words, std::vector<std::string>& words_split, bool splitwords);

private:
    kaldi::Timer timer;
    std::string outtext;
    int tot_num_frames = 0;

    int32_t mRecDeviceId = oboe::kUnspecified;
    int32_t mSampleRate;
    int32_t mFramesPerBurst;
    unsigned mChannelCount;
    bool mIsfloat;
    oboe::AudioStream *mRecStream;
    
    size_t data_chunk_pos;
    size_t fact_chunk_pos;
    std::ofstream f;
    const static int32_t fin_sample_rate = 16000;
    float_t* fp_audio;
    float_t* int_audio; // is in int16 range

    FILE* os_ctm;
    FILE* os_txt;

    std::atomic<bool> recognition_on;
    std::atomic<bool> do_recognition;
    std::thread t_recognition;
    std::thread t_rnnlm;

    kaldi::BaseFloat amplitude_delta_avg = 0.5;

    // ASR vars
    std::string model_dir;
    kaldi::nnet3::AmNnetSimple am_nnet;
    kaldi::OnlineNnet2FeaturePipelineConfig feature_opts;
    kaldi::nnet3::NnetSimpleLoopedComputationOptions decodable_opts;
    fst::Fst<fst::StdArc>* decode_fst;
    kaldi::OnlineNnet2FeaturePipelineInfo* feature_info = NULL;
    kaldi::nnet3::DecodableNnetSimpleLoopedInfo* decodable_info = NULL;
    kaldi::SingleUtteranceNnet3Decoder* decoder = NULL;
    kaldi::OnlineNnet2FeaturePipeline* feature_pipeline = NULL;
    fst::SymbolTable* word_syms;
    kaldi::LatticeFasterDecoderConfig* decoder_opts;
    kaldi::BaseFloat fin_sample_rate_fp;
    kaldi::TransitionModel trans_model;
    kaldi::WordAlignLatticeLexiconInfo* lexicon_info;
    kaldi::WordAlignLatticeLexiconOpts opts;
    int32 left_context;
    int32 right_context;
    kaldi::BaseFloat frame_shift = 0.03;
    int32 idx_sb;

    // RNN vars
    int32 max_ngram_order = 4;
    kaldi::CuMatrix<kaldi::BaseFloat> feat_emb_mat;
    kaldi::CuSparseMatrix<kaldi::BaseFloat> word_feat;
    kaldi::CuMatrix<kaldi::BaseFloat>* word_emb_mat = NULL;
    kaldi::ConstArpaLm* const_arpa = NULL;
    fst::DeterministicOnDemandFst<fst::StdArc> *carpa_lm_to_subtract_fst = NULL;
    fst::ScaleDeterministicOnDemandFst* lm_to_subtract_det_scale = NULL;
    kaldi::nnet3::Nnet rnnlm;
    kaldi::rnnlm::RnnlmComputeStateComputationOptions* rnn_opts;
    kaldi::rnnlm::RnnlmComputeStateInfo* rnn_info;
    kaldi::rnnlm::KaldiRnnlmDeterministicFst* lm_to_add_orig;
    fst::DeterministicOnDemandFst<fst::StdArc>* lm_to_add;
    const fst::ComposeDeterministicOnDemandFst<fst::StdArc>* combined_lms;
    const kaldi::ComposeLatticePrunedOptions* compose_opts;
    bool rnn_ready;

    void finish_segment(kaldi::CompactLattice* clat, int32 num_out_frames);

    void write_to_wav(int32 num_frames);

    void recognition_loop();
};

#endif //ARK_RECENGINE_H