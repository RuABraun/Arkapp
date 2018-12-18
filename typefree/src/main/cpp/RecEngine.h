#ifndef ARK_RECENGINE_H
#define ARK_RECENGINE_H
//#define CAFFE2_USE_LITE_PROTO 1

#include <oboe/Oboe.h>
#include <thread>
#include <array>
#include <fstream>
#include <jni.h>
#include "feat/wave-reader.h"
#include "fstext/fstext-lib.h"
#include "lat/lattice-functions.h"
#include "util/kaldi-thread.h"
#include "util/simple-io-funcs.h"
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
#include <memory>
#include "tensorflow/lite/model.h"
#include "tensorflow/lite/interpreter.h"
#include "tensorflow/lite/kernels/register.h"
#include "tensorflow/lite/string_util.h"


class RecEngine : oboe::AudioStreamCallback {
public:
    RecEngine(std::string modeldir);

    ~RecEngine();

    void setupLex(std::string wsyms, std::string align_lex);

    void setupRnnlm(std::string modeldir);

    void setDeviceId(int32_t deviceId);

    const char* get_text();
    const char* get_const_text();
    void reset_text();

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *audioStream, void *audioData,
                                          int32_t numFrames);

    void transcribe_stream(std::string wavpath);

    int stop_trans_stream();

    void transcribe_file(std::string wavpath, std::string fpath);

    void finish_segment(kaldi::CompactLattice* clat, int32 num_out_frames);

    void recognition_loop();

    std::string prettify_text(std::vector<int32>& words, std::vector<std::string>& words_split,
                              std::vector<int32>& indcs_kept, bool split);

    void get_text_case(std::vector<int32>* words, std::vector<int32>* casing);

private:

    kaldi::Timer timer;
    std::string outtext;
    std::string const_outtext;
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
    int16_t* int_audio; // is in int16 range

    FILE* os_ctm;
    FILE* os_txt;

    std::atomic<bool> recognition_on;
    std::atomic<bool> do_recognition;
    std::thread t_recognition;
    std::thread t_rnnlm;
    std::thread t_finishsegment;

    kaldi::BaseFloat amplitude_delta_avg = 0.5;

    int32 tot_num_frames_decoded;

    // ASR vars
    std::string model_dir;
    kaldi::nnet3::AmNnetSimple am_nnet;
    kaldi::nnet3::NnetSimpleLoopedComputationOptions decodable_opts;
    kaldi::OnlineSilenceWeightingConfig sil_config;
    kaldi::OnlineNnet2FeaturePipelineConfig feature_opts;
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
    std::string silence_phones;
    kaldi::CompactLattice finish_seg_clat;

    // RNN vars
    int32 max_ngram_order = 4;
    kaldi::CuMatrix<kaldi::BaseFloat> word_emb_mat_large;
    kaldi::CuMatrix<kaldi::BaseFloat> word_emb_mat_med;
    kaldi::CuMatrix<kaldi::BaseFloat> word_emb_mat_small;
    kaldi::ConstArpaLm* const_arpa = NULL;
    fst::DeterministicOnDemandFst<fst::StdArc> *carpa_lm_to_subtract_fst = NULL;
    fst::ScaleDeterministicOnDemandFst* lm_to_subtract_det_scale = NULL;
    kaldi::nnet3::Nnet rnnlm;
    kaldi::rnnlm::RnnlmComputeStateComputationOptions* rnn_opts;
    kaldi::rnnlm::RnnlmComputeStateInfoAdapt* rnn_info;
    kaldi::rnnlm::KaldiRnnlmDeterministicFstAdapt* lm_to_add_orig;
    fst::DeterministicOnDemandFst<fst::StdArc>* lm_to_add;
    const fst::ComposeDeterministicOnDemandFst<fst::StdArc>* combined_lms;
    const kaldi::ComposeLatticePrunedOptions* compose_opts;
    bool rnn_ready;

    // case model
    int32 CASE_INNUM = 8;
    int32 CASE_OFFSET = 1;
    std::unique_ptr<tflite::FlatBufferModel> flatbuffer_model;
    std::unique_ptr<tflite::Interpreter> interpreter;
    std::vector<int32> nid_to_caseid;  // ngram index (id) to case index
    int32 case_zero_index;  // number of case words, doubles as index to a zeroed embedding
    int32 casepos_zero_index;

    int32 run_casing(std::vector<int32> casewords, std::vector<int32> casewords_pos);

    void write_to_wav(int32 num_frames);

};

#endif //ARK_RECENGINE_H