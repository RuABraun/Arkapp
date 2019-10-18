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
#include "lat/word-align-lattice.h"
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
#include <atomic>
#include <condition_variable>
#include <mutex>

class RingBuffer {
public:
    RingBuffer();
    RingBuffer(int32_t size);

    ~RingBuffer();

    void Append(float_t* data, int32_t size);

    void AppendI(int16_t* data, int32_t size);

    void Add(float_t val);

    int32_t Set(int32_t* offset);

    float_t Get(int32_t index);

    void Reset();

    std::atomic<int32_t> next_index_;  // points 1 past last index used
    std::atomic<int32_t> newest_gotten_index_;
    int32_t size_;
    float_t* data_;
    std::atomic<int32_t> num_added_;
};

class RecEngine : oboe::AudioStreamCallback {
public:
    RecEngine(std::string modeldir, std::vector<int> exclusiveCores);

    ~RecEngine();

    void setupRnnlm(std::string modeldir);

    void setDeviceId(int32_t deviceId);

    const char* get_text();
    const char* get_const_text(int* size);
    void reset_text();

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *audioStream, void *audioData,
                                          int32_t numFrames);

    void transcribe_stream(std::string wavpath);

    int stop_trans_stream();

    int transcribe_file(std::string wavpath, std::string fpath);

    void finish_segment(kaldi::CompactLattice* clat, int32 num_out_frames);

    void recognition_loop();

    void run_recognition();

    void pause_stream();

    void resume_stream();

    std::string prettify_text(std::vector<int32>& words, std::vector<std::string>& words_split,
                              std::vector<int32>& indcs_kept, bool split);

    void get_text_case(std::vector<int32>* words, std::vector<int32>* casing);

    int convert_audio(const char* audiopath, const char* wavpath);

    void set_thread_affinity();

    kaldi::Timer timer;
    std::string outtext;
    std::string const_outtext;
    int tot_num_frames = 0;
    bool text_updated = false;
    int32_t paused_num_samples_;

    std::vector<int> excl_cores;
    bool thread_affinity_set = false;

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
    RingBuffer audio_buffer;

    FILE* os_ctm = NULL;
    FILE* os_txt = NULL;

    std::mutex mutex_decoding_;
    std::atomic<bool> recognition_on;
    std::atomic<bool> is_recognition_paused;
    bool is_decoding;  // AdvanceDecoding is being called
    std::condition_variable cv_decoding_;
    std::thread t_recognition;
    std::thread t_rnnlm;
    std::thread t_finishsegment;

    int32 tot_num_frames_decoded;
private:
    // ASR vars
    std::string model_dir;
    kaldi::nnet3::AmNnetSimple am_nnet;
    kaldi::nnet3::NnetSimpleLoopedComputationOptions decodable_opts;
    kaldi::OnlineSilenceWeightingConfig sil_config;
    kaldi::OnlineNnet2FeaturePipelineConfig feature_opts;
    fst::Fst<fst::StdArc>* decode_fst = NULL;
    kaldi::OnlineNnet2FeaturePipelineInfo* feature_info = NULL;
    kaldi::nnet3::DecodableNnetSimpleLoopedInfo* decodable_info = NULL;
    kaldi::SingleUtteranceNnet3Decoder* decoder = NULL;
    kaldi::OnlineNnet2FeaturePipeline* feature_pipeline = NULL;
    fst::SymbolTable* word_syms = NULL;
    kaldi::LatticeFasterDecoderConfig* decoder_opts = NULL;
    kaldi::BaseFloat fin_sample_rate_fp;
    kaldi::TransitionModel trans_model;
    kaldi::WordBoundaryInfo* wordb_info = NULL;
    kaldi::WordBoundaryInfoNewOpts opts;
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
    fst::DeterministicOnDemandFst<fst::StdArc> *carpa_lm_fst = NULL;
    fst::ScaleDeterministicOnDemandFst* carpa_lm_fst_subtract = NULL;
    kaldi::nnet3::Nnet rnnlm;
    kaldi::rnnlm::RnnlmComputeStateComputationOptions* rnn_opts = NULL;
    kaldi::rnnlm::RnnlmComputeStateInfoAdapt* rnn_info = NULL;
    kaldi::rnnlm::KaldiRnnlmDeterministicFstAdapt* lm_to_add_orig = NULL;
    fst::DeterministicOnDemandFst<fst::StdArc>* lm_to_add = NULL;
    const fst::ComposeDeterministicOnDemandFst<fst::StdArc>* combined_lms = NULL;
    const kaldi::ComposeLatticePrunedOptions* compose_opts = NULL;
    bool rnn_ready;

    // case model
    int32 CASE_INNUM = 7;
    int32 CASE_OFFSET = 1;
    std::unique_ptr<tflite::FlatBufferModel> flatbuffer_model;
    std::unique_ptr<tflite::Interpreter> interpreter;
    std::vector<int32> nid_to_caseid;  // ngram index (id) to case index
    int32 case_zero_index;  // number of case words, doubles as index to a zeroed embedding
    int32 casepos_zero_index;

    int32 run_casing(std::vector<int32> casewords, std::vector<int32> casewords_pos);

};

#endif //ARK_RECENGINE_H