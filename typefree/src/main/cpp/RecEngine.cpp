#include <stdlib.h>
#include <cstring>
#include <string>
#include <cmath>
#include <iostream>
#include <fstream>
#include "logging_macros.h"
#include "RecEngine.h"
#include <stdio.h>
#include <chrono>
#include "qnnpack.h"

extern "C"
{
    #include <zlib.h>
    #include <zconf.h>
    #include "libavutil/opt.h"
    #include "libavcodec/avcodec.h"
    #include "libavformat/avformat.h"
    #include "libswresample/swresample.h"
}

using namespace kaldi;
using namespace fst;

namespace little_endian_io {
    template <typename Word>
    std::ostream& write_word_old( std::ostream& outs, Word value, unsigned size = sizeof( Word ) ) {
        for (; size; --size, value >>= 8)
            outs.put( static_cast <char> (value & 0xFF) );
        return outs;
    }

    template <typename Word>
    void write_word(std::ostream& outs, Word value, unsigned size = sizeof(Word)) {
        outs.write((char*) &value, size);
    }
}
using namespace little_endian_io;

static int pfd[2];
static pthread_t thr;
static const char *tag = "myapp";

static void *thread_func(void*)
{
    ssize_t rdsz;
    char buf[128];
    for(;;) {
        if((rdsz = read(pfd[0], buf, sizeof buf - 1)) > 0) {
            if(buf[rdsz - 1] == '\n') --rdsz;
            buf[rdsz] = 0;
            __android_log_write(ANDROID_LOG_DEBUG, tag, buf);
        }
    }
    return 0;
}

int start_logger()
{

    setvbuf(stdout, 0, _IOLBF, 0);
    setvbuf(stderr, 0, _IONBF, 0);

    //
    pipe(pfd);
    dup2(pfd[1], 1);
    dup2(pfd[1], 2);


    if(pthread_create(&thr, 0, thread_func, 0) == -1)
        return -1;
    pthread_detach(thr);
    return 0;
}


void RecEngine::setupRnnlm(std::string modeldir) {
    rnn_ready = false;

    std::string wsyms = modeldir + "words.txt",
            word_boundary_f = modeldir + "word_boundary.int";
    word_syms = SymbolTable::ReadText(wsyms);
    idx_sb = (int32_t) word_syms->Find("<sb>");
    unk_index_ = (int32_t) word_syms->Find("<unk>");
    wordb_info = new WordBoundaryInfo(opts, word_boundary_f);

    LOGI("Done lexicons.");

    std::string lm_to_subtract_fname = modeldir + "o3_2p5M.carpa";
//            word_small_emb_fname = modeldir + "word_embedding_small.final.mat",
//            word_med_emb_fname = modeldir + "word_embedding_med.final.mat",
//            word_large_emb_fname = modeldir + "word_embedding_large.final.mat",
//            rnnlm_raw_fname = modeldir + "final.raw";

//    ReadKaldiObject(word_large_emb_fname, &word_emb_mat_large);
//    ReadKaldiObject(word_med_emb_fname, &word_emb_mat_med);
//    ReadKaldiObject(word_small_emb_fname, &word_emb_mat_small);
//    BaseFloat rnn_scale = 0.8f;
    const_arpa = new ConstArpaLm();
    ReadKaldiObject(lm_to_subtract_fname, const_arpa);
    carpa_lm_fst = new ConstArpaLmDeterministicFst(*const_arpa);
//    carpa_lm_fst_subtract = new fst::ScaleDeterministicOnDemandFst(-rnn_scale,
//                                                                   carpa_lm_fst);
//    {
//        bool binary;
//        Input ki(rnnlm_raw_fname, &binary);
//        rnnlm.Read(ki.Stream(), binary, true);
//        SetBatchnormTestMode(true, &(rnnlm));
//        SetDropoutTestMode(true, &(rnnlm));
//        SetQuantTestMode(true, &(rnnlm));
//    }

//    int32 rnnlm_vocab_sz = word_emb_mat_large.NumRows() + word_emb_mat_med.NumRows() + word_emb_mat_small.NumRows();
//    std::vector<int32> ids;
//    kaldi::readNumsFromFile(modeldir + "ids.int", ids);
//    rnn_opts = new rnnlm::RnnlmComputeStateComputationOptions(ids[0], ids[1], ids[3], ids[2], ids[4], 150005, rnnlm_vocab_sz, modeldir);
//
//    rnn_info = new rnnlm::RnnlmComputeStateInfoAdapt(*rnn_opts, rnnlm, word_emb_mat_large,
//        word_emb_mat_med, word_emb_mat_small, word_emb_mat_large.NumRows(), word_emb_mat_med.NumRows());
//    lm_to_add_orig = new rnnlm::KaldiRnnlmDeterministicFstAdapt(max_ngram_order, *rnn_info);
//    lm_to_add = new ScaleDeterministicOnDemandFst(rnn_scale, lm_to_add_orig);
//    combined_lms = new ComposeDeterministicOnDemandFst<StdArc>(carpa_lm_fst_subtract, lm_to_add);
//
//    compose_opts = new ComposeLatticePrunedOptions(4.0, 900, 1.25, 75);

    LOGI("done setuprnnlm");

    // Case model
    LOGI("Doing case");

    case_module = torch::jit::load(modeldir + "traced_model.pt");

    case_zero_index = 10246;  // TODO: remove constant!
//    nid_to_caseid.push_back(case_zero_index);
    kaldi::readNumsFromFile(modeldir + "word2tag.int", nid_to_caseid);
    casepos_zero_index = CASE_INNUM;

    LOGI("All ASR setup complete.");
    rnn_ready = true;
}


RecEngine::RecEngine(std::string modeldir, std::vector<int> exclusiveCores):
                                            decodable_opts(1.0, 51, 3),
                                            sil_config(0.001f, ""),
                                            feature_opts(modeldir + "mfcc.conf", "mfcc", "", sil_config, "", modeldir + "cmvn.conf"),
                                            tot_num_frames_decoded(0),
                                            audio_buffer(16000 * 8) {
    start_logger();  // log stdout and stderr
    excl_cores = exclusiveCores;
    // ! -- ASR setup begin
    fin_sample_rate_fp = (BaseFloat) fin_sample_rate;
    LOGI("Constructing rec");
    model_dir = modeldir;
    std::string nnet3_rxfilename = modeldir + "final.mdl",
            fst_rxfilename = modeldir + "HCLG.fst";

    t_rnnlm = std::thread(&RecEngine::setupRnnlm, this, modeldir);

    decode_fst = ReadFstKaldiGeneric(fst_rxfilename);
    LOGI("Read HCLG");
    {
        bool binary;
        Input ki(nnet3_rxfilename, &binary);
        trans_model.Read(ki.Stream(), binary);
        am_nnet.Read(ki.Stream(), binary, true);

        SetBatchnormTestMode(true, &(am_nnet.GetNnet()));
        SetDropoutTestMode(true, &(am_nnet.GetNnet()));
        SetQuantTestMode(true, &(am_nnet.GetNnet()));
    }
    LOGI("Done AM model.");
    left_context = am_nnet.LeftContext();
    right_context = am_nnet.RightContext();
    mFramesPerBurst = int32_t(1.5 * 0.01f * fin_sample_rate_fp * ((float) (left_context + right_context) / 2 + 3));

    decodable_info = new nnet3::DecodableNnetSimpleLoopedInfo(decodable_opts, &am_nnet);
    feature_info = new OnlineNnet2FeaturePipelineInfo(feature_opts);

    {
        std::ifstream is(modeldir + "silence.csl", std::ifstream::in);
        if (!is.is_open()) KALDI_ERR << "No silence phones file";
        while (std::getline(is, silence_phones)) {
            break;
        }
    }
    // ! -- AM setup end
    oboe::DefaultStreamValues::FramesPerBurst = mFramesPerBurst;

    recognition_on = false;  // when off recognition loop won't run
    is_recognition_paused = false;  // when off only features extracted

    LOGI("Finished constructing rec");

    LOGI("OKAY!");
}

RecEngine::~RecEngine() {
    delete word_syms;
    delete decode_fst;
    delete feature_pipeline;
    delete decoder;
    delete decodable_info;
    delete feature_info;
    delete wordb_info;
    delete decoder_opts;
    delete os_ctm, os_txt;
    delete const_arpa;
//    delete rnn_info;
}

const char* RecEngine::get_text(){
    return outtext.c_str();
}

const char* RecEngine::get_const_text(int* size){
    if (!text_updated) {
        *size = -1;
    } else {
        *size = (int) const_outtext.size();
        text_updated = false;
    }
    return const_outtext.c_str();
}

void RecEngine::reset_text() {
    outtext = "";
    const_outtext = "";
    text_updated = true;
}

void RecEngine::transcribe_stream(std::string fpath){

    reset_text();
    oboe::AudioStreamBuilder builder;
    builder.setSharingMode(oboe::SharingMode::Exclusive);
    builder.setPerformanceMode(oboe::PerformanceMode::PowerSaving);
    builder.setCallback(this);
    builder.setDirection(oboe::Direction::Input);
    builder.setChannelCount(1);
//    builder.setFramesPerCallback(mFramesPerBurst);
    builder.setSampleRate(fin_sample_rate);

    oboe::Result result = builder.openStream(&mRecStream);

    oboe::AudioFormat mFormat;
    tot_num_frames_decoded = 0;
    if (result == oboe::Result::OK && mRecStream != nullptr) {
        mSampleRate = mRecStream->getSampleRate();
        mFormat = mRecStream->getFormat();
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

        const int16_t num_filechannels = 1;

        // ---------------- Wav header  -- Reference: http://www-mmsp.ece.mcgill.ca/Documents/AudioFormats/WAVE/WAVE.html
        std::string wavpath = fpath + ".wav";
        LOGI("FPATH OUT %s", wavpath.c_str());
        f.open(wavpath.c_str(), std::ios::binary);

        f << "RIFF----WAVEfmt ";
        int32_t format_chk_sz = 16;
        write_word(f, format_chk_sz, 4);  // is (empty) extension data
        int16_t bytes_perSample = 2;
        int16_t format_int = 1;  // 1 is PCM, 3 is float
        write_word(f, format_int, 2);
        write_word(f, num_filechannels, 2);
        write_word(f, fin_sample_rate, 4);
        write_word(f, fin_sample_rate * (int32_t) num_filechannels * (int32_t) bytes_perSample, 4 );  // (Sample Rate * Channels * BytesPerSample)
        write_word(f, num_filechannels * bytes_perSample, 2);  // data block size (size of two integer samples, one for each channel, in bytes)
        int16_t bits_per_byte = 8;
        write_word(f, bytes_perSample * bits_per_byte, 2);  // number of bits per sample (use a multiple of 8)

        data_chunk_pos = f.tellp();
        f << "data----";  // (chunk size to be filled in later)

        // ---------------- Setting up ASR vars
        decoder_opts = new LatticeFasterDecoderConfig(7.0, 8000, 6.0, 30, 6.0);
        decoder_opts->determinize_lattice = true;

        feature_pipeline = new OnlineNnet2FeaturePipeline(*feature_info);
        decoder = new SingleUtteranceNnet3Decoder(*decoder_opts, trans_model,
                                                         *decodable_info, *decode_fst, feature_pipeline);

        tot_num_frames_decoded = 0;
        // Text output
        std::string ctmpath = fpath + "_timed.txt";
        os_ctm = fopen(ctmpath.c_str(), "wt");
        std::string txtpath = fpath + ".txt";
        os_txt = fopen(txtpath.c_str(), "wt");

        audio_buffer.Reset();

        // ---------------- Done

        result = mRecStream->requestStart();
        recognition_on = true;
        t_recognition = std::thread(&RecEngine::recognition_loop, this);
        if (result != oboe::Result::OK) {
            LOGE("Error starting stream. %s", oboe::convertToText(result));
        }
        LOGI("Starting to read input");
    } else {
        LOGE("Failed to create stream. Error: %s", oboe::convertToText(result));
    }
}

int RecEngine::stop_trans_stream() {
    if (mRecStream != nullptr) {

        oboe::Result result = mRecStream->requestStop();

        if (result != oboe::Result::OK) {
            LOGE("Error stopping output stream. %s", oboe::convertToText(result));
        }

        result = mRecStream->close();
        if (result != oboe::Result::OK) {
            LOGE("Error closing output stream. %s", oboe::convertToText(result));
        }
        recognition_on = false;
        t_recognition.join();
        if (!is_recognition_paused) {
            run_recognition();
        }
        is_recognition_paused = false;

        feature_pipeline->InputFinished();
        decoder->AdvanceDecoding();
        decoder->FinalizeDecoding();

        CompactLattice olat;
        decoder->GetLattice(true, &olat);

        int32 num_out_frames = tot_num_frames_decoded + decoder->NumFramesDecoded();
        if (t_finishsegment.joinable()) t_finishsegment.join();
        finish_segment(&olat, num_out_frames);
        fclose(os_txt);
        fclose(os_ctm);
//         Finishing wav write
        int32_t array_size = wav_data_array.size();
        int16_t factor = static_cast<int16_t>(std::numeric_limits<int16_t>::max() / max_number_);
        for (int32_t i = 0; i < array_size; i++) {
            int16 val = wav_data_array[i] * factor;
            f.write((char *) &val, sizeof(int16_t));
        }

        size_t file_length = f.tellp();
        f.seekp(data_chunk_pos + 4);
        int32 num_bytes = file_length - data_chunk_pos - 8;
        write_word(f, num_bytes, 4);
        f.seekp(4);
        write_word(f, file_length - 8, 4);
        f.close();
        return num_out_frames;
    }
    return 0;
}


void RecEngine::run_recognition() {
    int32_t offset = 0, size = 0;
    std::vector<std::string> dummy;
    std::vector<int32> dummyb;
    size = audio_buffer.Set(&offset);
    if (size > 0) {
        Vector<float> data(size);
        for(int32_t i = 0; i < size; i++) {
            data(i) = audio_buffer.Get(offset + i);
        }
        if (!is_recognition_paused.load()) {
            {
                std::unique_lock<std::mutex> lock(mutex_decoding_);
                is_decoding = true;
                feature_pipeline->AcceptWaveform(fin_sample_rate_fp, data);
                decoder->AdvanceDecoding();

                if (decoder->isStopTime(silence_phones, trans_model, 3)) {
                    int32 num_frames_decoded = decoder->NumFramesDecoded();

                    decoder->GetLattice(false, &finish_seg_clat);

                    if (t_rnnlm.joinable()) t_rnnlm.join();
                    if (t_finishsegment.joinable()) t_finishsegment.join();

                    t_finishsegment = std::thread(&RecEngine::finish_segment, this,
                                                  &finish_seg_clat, num_frames_decoded);

                    decoder->InitDecoding(tot_num_frames_decoded + num_frames_decoded);
                } else if (decoder->NumFramesDecoded() > 0) {
                    Lattice olat;
                    decoder->GetBestPath(false, &olat);

                    std::vector<int32> words;

                    if (!GetLinearWordSequence(olat, &words)) LOGE("Failed get linear seq");
                    outtext = prettify_text(words, dummy, dummyb, false);
                }
                is_decoding = false;
                cv_decoding_.notify_one();
            }

            if (wav_data_array.size() == 0 || wav_data_array.size() + 2 * 16000 > wav_data_array.capacity() ) {
                wav_data_array.reserve(10 * wav_data_array.capacity() + 60 * 16000);
            }

            for (int32_t i = 0; i < size; i++) {
                int16_t vali = static_cast<int16_t>(data(i));
                if (vali > max_number_) max_number_ = vali;
                wav_data_array.push_back(vali);
            }

        } else {
            std::this_thread::sleep_for(std::chrono::milliseconds(100));
        }
    }
}


void RecEngine::recognition_loop() {
    set_thread_affinity();
//    Timer tt;
    LOGI("TID: %d", std::this_thread::get_id());
    std::vector<std::string> dummy;
    std::vector<int32> dummyb;
    std::this_thread::sleep_for(std::chrono::milliseconds(50));
    int32_t offset = 0, size = 0;
    while(recognition_on.load()) {
        run_recognition();
    }
}

oboe::DataCallbackResult RecEngine::onAudioReady(oboe::AudioStream *audioStream, void *audioData, int32_t numFrames) {
    tot_num_frames += numFrames;
    // Note we put data in floating point format but in PCM (int) range
    if(mIsfloat) {
        float* audio_data = static_cast<float*>(audioData);
        audio_buffer.Append(audio_data, numFrames);
    } else {
        int16_t* audio_data = static_cast<int16_t*>(audioData);
        audio_buffer.AppendI(audio_data, numFrames);
    }

    return oboe::DataCallbackResult::Continue;
}

void RecEngine::pause_stream() {
    if (!recognition_on.load()) return;
    is_recognition_paused = true;
    paused_num_samples_ = audio_buffer.num_added_.load();
    {
        std::unique_lock<std::mutex> lock(mutex_decoding_);
        this->cv_decoding_.wait_for(lock, std::chrono::seconds(5), [this] { return !is_decoding; });
        int32 num_frames_decoded = decoder->NumFramesDecoded();

        decoder->GetLattice(false, &finish_seg_clat);

        if (t_rnnlm.joinable()) t_rnnlm.join();
        if (t_finishsegment.joinable()) t_finishsegment.join();
        t_finishsegment = std::thread(&RecEngine::finish_segment, this,
                                      &finish_seg_clat, num_frames_decoded);

        decoder->InitDecoding(tot_num_frames_decoded + num_frames_decoded);
    }
}

void RecEngine::resume_stream() {
    std::unique_lock<std::mutex> lock(mutex_decoding_);
    int32_t pause_duration = audio_buffer.num_added_.load() - paused_num_samples_;
    Vector<float> data;
    const int32_t num_prepend = 8000;
    if (pause_duration > num_prepend) {
        data.Resize(num_prepend);
        int32_t next_index = audio_buffer.next_index_.load();
        int32_t start = next_index - num_prepend;
        for (int32_t i = 0; i < num_prepend; i++) {
            int32_t j = start + i;
            if (j < 0) j = audio_buffer.size_ + j;
            data(i) = ((float) (1.f / (num_prepend - i))) * audio_buffer.Get(j);
        }
    } else {
        data.Resize(pause_duration);
        int32_t next_index = audio_buffer.next_index_.load();
        int32_t start = next_index - pause_duration;
        for (int32_t i = 0; i < pause_duration; i++) {
            int32_t j = start + i;
            if (j < 0) j = audio_buffer.size_ + j;
            data(i) = ((float) (1.f / (pause_duration - i))) * audio_buffer.Get(j);
        }
    }
    feature_pipeline->AcceptWaveform(fin_sample_rate_fp, data);
    is_recognition_paused = false;
}

void RecEngine::setDeviceId(int32_t deviceId) {
    mRecDeviceId = deviceId;
}

void RecEngine::finish_segment(CompactLattice* clat, int32 num_out_frames) {

    TopSortCompactLatticeIfNeeded(clat);

    CompactLattice clat_composed, best_path;

    AddWordInsPenToCompactLattice(0.5, clat);

    fst::ScaleLattice(fst::GraphLatticeScale(0.), clat);
    ComposeCompactLatticeDeterministic(*clat, carpa_lm_fst, &clat_composed);

    if (t_rnnlm.joinable()) t_rnnlm.join();
//    CompactLattice clat_rescored;
    Timer timer;
//    ComposeCompactLatticePrunedB(*compose_opts, determinized_clat,
//                                 const_cast<ComposeDeterministicOnDemandFst<StdArc> *>(combined_lms),
//                                 &clat_rescored, max_ngram_order, true);
//    double timetaken = timer.Elapsed();
//    KALDI_LOG << "TIME TAKEN " << timetaken;
    CompactLatticeShortestPath(clat_composed, &best_path);

    CompactLattice aligned_clat;
    WordAlignLattice(best_path, trans_model, *wordb_info, 0, &aligned_clat);

    std::vector<int32> words, times, lengths;
    CompactLatticeToWordAlignment(aligned_clat, &words, &times, &lengths);

    if (t_rnnlm.joinable()) t_rnnlm.join();
    std::vector<std::string> words_split;
    std::vector<int32> indcs_kept;
    std::string text = prettify_text(words, words_split, indcs_kept, true);

    outtext = "";
    if (const_outtext != "") {
        const_outtext = const_outtext + '\n' + text;
    } else {
        const_outtext = text;
    }
    text_updated = true;

    fwrite(text.c_str(), 1, text.size(), os_txt);
    fwrite("\n", 1, 1, os_txt);

    int32 num_words = words_split.size();
    int32 time_offset_seconds = static_cast<int32>(tot_num_frames_decoded * 3 / 100.f);
    for(size_t j = 0; j < num_words; j++) {
        std::string word = words_split[j];
        fwrite(word.c_str(), 1, word.size(), os_ctm);
        fwrite(" ", 1, 1, os_ctm);
        std::string wtime = std::to_string(time_offset_seconds + frame_shift * times[indcs_kept[j]]);
        size_t sz = wtime.find('.') + 2;
        fwrite(wtime.c_str(), 1, sz, os_ctm);
        fwrite("\n", 1, 1, os_ctm);
    }

    tot_num_frames_decoded += num_out_frames;
//    if (rnn_ready) {
//        lm_to_add_orig->Clear();
//    }  // TODO: check why ClearToContinue is worse

}

int32 RecEngine::run_casing(std::vector<long> casewords) {
    int32 sz = casewords.size();
    if (sz != CASE_INNUM) {
        LOGE("SIZE IS DIFFERENT THAN CASE_INNUM, SOMETHING WENT WRONG");
    }

    std::vector<torch::jit::IValue> inputs;
    auto input_tensor = torch::from_blob(casewords.data(), {1, 7}, at::ScalarType::Long);
    inputs.push_back(input_tensor);

    torch::autograd::AutoGradMode guard(false);
    at::Tensor output = case_module.forward(inputs).toTensor();

    int32 argmax = -1;
    float maxprob = -100.f;

    auto out_ptr = output.data_ptr<float>();
    for(int32 i = 0; i < 3; i++) {
        float out = out_ptr[i];
        if (out > maxprob) {
            maxprob = out;
            argmax = i;
        }
    }
    if (out_ptr[0] > -1.6) {
        argmax = 0;
    }
    return argmax;
}

void RecEngine::get_text_case(std::vector<int32>* words, std::vector<int32>* casing) {
    LOGI("Starting casing.");
    int32 sz = words->size();
    if (sz < 5) {
        casing->resize(sz, 0);
        return;
    }

    std::vector<int32> words_nosil;

    words_nosil.reserve(sz);
    for(int32 i = 0; i < sz; i++) {  // removes epsilon and <sb> words
        int32 w = (*words)[i];
        if (w != 0 && w != idx_sb && w != unk_index_) words_nosil.push_back(w);
    }

    // Convert ids
    sz = words_nosil.size();
    std::vector<int32> casewords(sz);
    for(int32 i = 0; i < sz; i++) {
        int32 id = nid_to_caseid[words_nosil[i]];
//        LOGI("Ids %d %d", words_nosil[i], id);
        casewords[i] = id;
    }

    std::vector<long> casewords_sentence(CASE_INNUM, case_zero_index);
    casing->resize(sz, 0);
    for(int32 i = 1; i < sz; i++) {
        int32 startidx = i - (CASE_INNUM - CASE_OFFSET - 1);
        if (startidx < 0) startidx = 0;
        int32 endidx = i + CASE_OFFSET;

        int32 k = CASE_INNUM - 1;
        for(int32 j = endidx; j >= startidx; j--, k--) {
            long wid;
            if (j < sz) {
                wid = (long) casewords[j];
            } else {
                wid = (long) case_zero_index;
            }
            //LOGI("Ids %d %d", wid, pos);
            casewords_sentence[k] = wid;
        }
        (*casing)[i] = run_casing(casewords_sentence);
    }
    LOGI("Done casing.");
}

std::string RecEngine::prettify_text(std::vector<int32>& words, std::vector<std::string>& words_split,
                                     std::vector<int32>& indcs_kept, bool split) {
    /*   Does casing, converts integer ids to words, adds . and replaces <unk>.
     *
     */
    int32 num_words = words.size();
    std::vector<int32> case_decs;  // case decisions
    if (split) {
        get_text_case(&words, &case_decs);
    } else {
        case_decs.resize(num_words, 0);
    }

    std::string text = "";
    bool doupper = false;
    int32 wcnt = 0;
    int32 real_num_word = case_decs.size();
    for(size_t j = 0; j < num_words; j++) {

        int32 w = words[j];
        if (w == 0 || w == idx_sb || w == unk_index_) continue;

        std::string word = word_syms->Find(w);

        if (split && case_decs[wcnt] > 0) doupper = true;
        if (wcnt == 0 || doupper) {
            word[0] = (char) toupper((int) word[0]);  // if this next if should be false
            doupper = false;
        }

        std::string wplus;

        if ((split && wcnt != case_decs.size() - 1 && case_decs[wcnt+1] == 2) || (split && wcnt == real_num_word - 1)) {
            wplus = word + ". ";
        } else if (j != num_words - 1 && word_syms->Find(words[j+1]) == "'s") {
            wplus = word;
        } else {
            wplus = word + " ";
        }
        wcnt++;
        if (split) {
            words_split.push_back(word);
            indcs_kept.push_back(j);
        }
        text += wplus;

    }
    return text;
}

int RecEngine::transcribe_file(std::string wavpath, std::string fpath) {
    set_thread_affinity();
    reset_text();
    try {
        LOGI("Transcribing file");
        using namespace kaldi;
        using namespace fst;

        typedef int32 int32;
        typedef int64 int64;

        BaseFloat chunk_length_secs = 0.72;

        decoder_opts = new LatticeFasterDecoderConfig(7.0, 1500, 6.0, 600, 6.0);
        decoder_opts->determinize_lattice = true;

        feature_pipeline = new OnlineNnet2FeaturePipeline(*feature_info);
        decoder = new SingleUtteranceNnet3Decoder(*decoder_opts, trans_model,
                                                  *decodable_info, *decode_fst, feature_pipeline);

        WaveHolder wavholder;
        std::ifstream wavis(wavpath, std::ios::binary);
        wavholder.Read(wavis);
        const WaveData &wave_data = wavholder.Value();
        SubVector<BaseFloat> data(wave_data.Data(), 0);

        BaseFloat samp_freq = wave_data.SampFreq();

        LOGI("Starting decoding.");

        feature_pipeline->AcceptWaveform(samp_freq, data);

        feature_pipeline->InputFinished();
        Timer timer;
//        decoder->AdvanceDecoding();
        std::thread t = std::thread(&SingleUtteranceNnet3Decoder::AdvanceDecodingLooped,
                        std::ref(*decoder));

        decoder->FinishedLoopedDecoding();
        t.join();
        double timetaken = timer.Elapsed();
        KALDI_LOG << "AM TIME TAKEN " << timetaken;

        decoder->FinalizeDecoding();
        CompactLattice clat;
        bool end_of_utterance = true;
        decoder->GetLattice(end_of_utterance, &clat);
        int32 num_out_frames = decoder->NumFramesDecoded();

        std::string ctmpath = fpath + "_timed.txt";
        os_ctm = fopen(ctmpath.c_str(), "wt");
        std::string txtpath = fpath + ".txt";
        os_txt = fopen(txtpath.c_str(), "wt");

        finish_segment(&clat, num_out_frames);

        fclose(os_ctm);
        fclose(os_txt);

        LOGI("Decoded file.");

    } catch(const std::exception& e) {
        LOGE("FAILED FAILED FAILED");
        LOGE("ERROR %s",  e.what());
        return 1;
    }
    return 0;
}
extern "C" {
int decode_audio_file(const char *path, const int fs, signed short **data, int *size) {

    //av_register_all();
    AVFormatContext *format = avformat_alloc_context();

    if (avformat_open_input(&format, path, NULL, NULL) != 0) {
        fprintf(stderr, "Could not open file");
        return -1;
    }

    if (avformat_find_stream_info(format, NULL) < 0) {
        fprintf(stderr, "Could not get stream info");
        return -1;
    }

    int stream_idx = -1;
    for (int i = 0; i < format->nb_streams; i++) {
        if (format->streams[i]->codec->codec_type == AVMEDIA_TYPE_AUDIO) {
            stream_idx = i;
            break;
        }
    }

    if (stream_idx == -1) {
        fprintf(stderr, "Failed");
        return -1;
    }

    AVStream *stream = format->streams[stream_idx];

    AVCodecContext *codec = stream->codec;

    if (avcodec_open2(codec, avcodec_find_decoder(codec->codec_id), NULL) < 0) {
        fprintf(stderr, "Failed to open decoder");
        return -1;
    }

    struct SwrContext *swr = swr_alloc();
    av_opt_set_int(swr, "in_channel_count", codec->channels, 0);
    av_opt_set_int(swr, "out_channel_count", 1, 0);
    av_opt_set_int(swr, "in_channel_layout", codec->channel_layout, 0);
    av_opt_set_int(swr, "out_channel_layout", AV_CH_LAYOUT_MONO, 0);
    av_opt_set_int(swr, "in_sample_rate", codec->sample_rate, 0);
    av_opt_set_int(swr, "out_sample_rate", fs, 0);
    av_opt_set_sample_fmt(swr, "in_sample_fmt", codec->sample_fmt, 0);
    av_opt_set_sample_fmt(swr, "out_sample_fmt", AV_SAMPLE_FMT_S16, 0);
    swr_init(swr);

    if (!swr_is_initialized(swr)) {
        fprintf(stderr, "failed");
        return -1;
    }

    AVPacket packet;
    av_init_packet(&packet);
    AVFrame *frame = av_frame_alloc();
    if (!frame) {
        fprintf(stderr, "Error allocating the frame\n");
        return -1;
    }

    *data = NULL;
    *size = 0;
    printf("Sizeof %zu\n", sizeof(short));

    while (av_read_frame(format, &packet) >= 0) {
        int gotFrame;
        if (avcodec_decode_audio4(codec, frame, &gotFrame, &packet) < 0) {
            break;
        }
        if (!gotFrame) {
            continue;
        }

        signed short *buffer;
        av_samples_alloc((uint8_t * *) & buffer, NULL, 1, frame->nb_samples, AV_SAMPLE_FMT_S16, 0);
        int frame_count = swr_convert(swr, (uint8_t * *) & buffer, frame->nb_samples,
                                      (const uint8_t **) frame->data, frame->nb_samples);
        *data = (signed short *) realloc(*data, (*size + frame->nb_samples) * sizeof(short));
        memcpy(*data + *size, buffer, frame_count * sizeof(short));
        *size += frame_count;
    }

    av_frame_free(&frame);
    swr_free(&swr);
    avcodec_close(codec);
    avformat_free_context(format);

    return 0;
}
}

int write_wav(const char* newf, signed short* data, int size, int fs) {
    FILE* os_wav;
    os_wav = fopen(newf, "wb");
    fprintf(os_wav, "RIFF");
    int fsize = 44 - 8 + size * 2;
    printf("fsize %d : %zu\n", fsize, sizeof(fsize));
    fflush(os_wav);
    fwrite(&fsize, 4, 1, os_wav);
    fprintf(os_wav, "WAVEfmt ");
    fflush(os_wav);
    int format_chk_sz = 16;
    signed short bytes_perSample = 2;
    signed short format_int = 1;
    signed short num_c = 1;
    fwrite(&format_chk_sz, 4, 1, os_wav);
    fwrite(&format_int, 2, 1, os_wav);
    fwrite(&num_c, 2, 1, os_wav);
    fwrite(&fs, 4, 1, os_wav);
    int num_bytes_persec = fs * (int) num_c * (int) bytes_perSample;
    fwrite(&num_bytes_persec, 4, 1, os_wav);
    signed short block_sz = num_c * bytes_perSample;
    fwrite(&block_sz, 2, 1, os_wav);
    signed short bit_blocksz = 8 * bytes_perSample;
    fwrite(&bit_blocksz, 2, 1, os_wav);
    fprintf(os_wav, "data");
    fflush(os_wav);
    int num_b = (int) num_c * size * (int) bytes_perSample;
    printf("Num b %d\n", num_b);
    fwrite(&num_b, 4, 1, os_wav);
    fwrite(data, 2, size, os_wav);
    fclose(os_wav);
    return 0;
}

int RecEngine::convert_audio(const char* audiopath, const char* wavpath) {
    signed short* data;
    int size;
    if (decode_audio_file(audiopath, fin_sample_rate, &data, &size) != 0) {
        return -1;
    }
    write_wav(wavpath, data, size, fin_sample_rate);
    free(data);
    return 0;
}

void RecEngine::set_thread_affinity() {
    if (excl_cores.size() == 0) return;
    pid_t current_thread_id = gettid();
    cpu_set_t cpu_set;
    CPU_ZERO(&cpu_set);

    for (int i = 0; i < excl_cores.size(); i++) {
        int cpu_id = excl_cores[i];
        CPU_SET(cpu_id, &cpu_set);
    }

    int result = sched_setaffinity(current_thread_id, sizeof(cpu_set_t), &cpu_set);
    if (result == 0) {
        LOGI("APP", "Thread affinity set");
    } else {
        LOGE("APP", "Error setting thread affinity!");
    }
    thread_affinity_set = true;
}

RingBuffer::RingBuffer() {
}

RingBuffer::RingBuffer(int32_t size) {
    data_ = new float_t[size]();
    next_index_ = 0;
    size_ = size;
    newest_gotten_index_ = 0;
    num_added_ = 0;
}

RingBuffer::~RingBuffer() {
    delete[] data_;
}

void RingBuffer::Append(float_t* data, int32_t size) {
    int32_t next_index = next_index_.load();
    for(int32_t i = 0; i < size; i++) {
        data_[next_index + i] = data[i] * 32768.0f;
    }
    next_index += size;
    num_added_ += size;
    next_index %= size_;
    next_index_ = next_index;
}

void RingBuffer::AppendI(int16_t* data, int32_t size) {
    int32_t next_index = next_index_.load();
    for(int32_t i = 0; i < size; i++) {
        data_[next_index + i] = static_cast<float_t>(data[i]);
    }
    next_index += size;
    num_added_ += size;
    next_index %= size_;
    next_index_ = next_index;
}

void RingBuffer::Add(float_t val) {
    int32_t next_index = next_index_.load();
    data_[next_index] = val;
    next_index++;
    if (next_index >= size_) next_index = 0;
    next_index_ = next_index;
    num_added_++;
}

int32_t RingBuffer::Set(int32_t* offset) {
    int32_t next_index = next_index_.load();
    int32_t size;
    int32_t newest_gotten_index = newest_gotten_index_.load();
    if (next_index >= newest_gotten_index_) {
        size = next_index - newest_gotten_index_;
    } else {
        size = (size_ - newest_gotten_index_) + next_index;
    }
    *offset = newest_gotten_index_;
    newest_gotten_index_ = next_index;
    //KALDI_LOG << "offset " << *offset << " size " << size;
    return size;
}

float_t RingBuffer::Get(int32_t index) {
    index %= size_;
    return data_[index];
}

void RingBuffer::Reset() {
    next_index_ = 0;
    newest_gotten_index_ = 0;
    num_added_ = 0;
}