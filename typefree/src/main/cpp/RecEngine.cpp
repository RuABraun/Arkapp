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
#include <tensorflow/lite/c/c_api_internal.h>

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


void RecEngine::setupLex(std::string wsyms, std::string align_lex) {
    word_syms = SymbolTable::ReadText(wsyms);
    idx_sb = (int32) word_syms->Find("<sb>");
    std::vector<std::vector<int32>> lexicon;
    ReadLexiconForWordAlignBin(align_lex, &lexicon);
    lexicon_info = new WordAlignLatticeLexiconInfo(lexicon);
}

void RecEngine::setupRnnlm(std::string modeldir) {
    rnn_ready = false;
    std::string lm_to_subtract_fname = modeldir + "o3_1M.carpa",
            word_small_emb_fname = modeldir + "word_embedding_small.final.mat",
            word_med_emb_fname = modeldir + "word_embedding_med.final.mat",
            word_large_emb_fname = modeldir + "word_embedding_large.final.mat",
            rnnlm_raw_fname = modeldir + "final.raw";

    ReadKaldiObject(word_large_emb_fname, &word_emb_mat_large);
    ReadKaldiObject(word_med_emb_fname, &word_emb_mat_med);
    ReadKaldiObject(word_small_emb_fname, &word_emb_mat_small);
    BaseFloat rnn_scale = 0.9f;

    const_arpa = new ConstArpaLm();
    ReadKaldiObject(lm_to_subtract_fname, const_arpa);
    carpa_lm_to_subtract_fst = new ConstArpaLmDeterministicFst(*const_arpa);
    lm_to_subtract_det_scale = new fst::ScaleDeterministicOnDemandFst(-rnn_scale,
                                                                      carpa_lm_to_subtract_fst);

    ReadKaldiObject(rnnlm_raw_fname, &rnnlm);

    int32 rnnlm_vocab_sz = word_emb_mat_large.NumRows() + word_emb_mat_med.NumRows() + word_emb_mat_small.NumRows();
    std::vector<int32> ids;
    kaldi::readNumsFromFile(modeldir + "ids.int", ids);
    rnn_opts = new rnnlm::RnnlmComputeStateComputationOptions(ids[0], ids[1], ids[3], ids[2], ids[4], 149995, rnnlm_vocab_sz, modeldir);

    rnn_info = new rnnlm::RnnlmComputeStateInfoAdapt(*rnn_opts, rnnlm, word_emb_mat_large,
        word_emb_mat_med, word_emb_mat_small, word_emb_mat_large.NumRows(), word_emb_mat_med.NumRows());
    lm_to_add_orig = new rnnlm::KaldiRnnlmDeterministicFstAdapt(max_ngram_order, *rnn_info);

    lm_to_add = new ScaleDeterministicOnDemandFst(rnn_scale, lm_to_add_orig);

    combined_lms = new ComposeDeterministicOnDemandFst<StdArc>(lm_to_subtract_det_scale, lm_to_add);

    LOGI("done setuprnnlm");
    rnn_ready = true;
}


RecEngine::RecEngine(std::string modeldir): decodable_opts(1.0, 51, 3),
                                            sil_config(0.001f, ""),
                                            feature_opts(modeldir + "mfcc.conf", "mfcc", "", sil_config, ""),
                                            tot_num_frames_decoded(0) {
    // ! -- ASR setup begin
    fin_sample_rate_fp = (BaseFloat) fin_sample_rate;
    LOGI("Constructing rec");
    start_logger();
    model_dir = modeldir;
    std::string nnet3_rxfilename = modeldir + "final.mdl",
            fst_rxfilename = modeldir + "HCLG.fst",
            align_lex = modeldir + "align_lexicon.bin",
            wsyms = modeldir + "words.txt";

    std::thread t(&RecEngine::setupLex, this, wsyms, align_lex);
    t_rnnlm = std::thread(&RecEngine::setupRnnlm, this, modeldir);

    decode_fst = ReadFstKaldiGeneric(fst_rxfilename);
    {
        bool binary;
        Input ki(nnet3_rxfilename, &binary);
        trans_model.Read(ki.Stream(), binary);
        am_nnet.Read(ki.Stream(), binary);

        SetBatchnormTestMode(true, &(am_nnet.GetNnet()));
        SetDropoutTestMode(true, &(am_nnet.GetNnet()));
        nnet3::CollapseModel(nnet3::CollapseModelConfig(), &(am_nnet.GetNnet()));
    }

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

    // ! -- AM setup end, doing RNN setup

    compose_opts = new ComposeLatticePrunedOptions(2.0, 200, 1.25, 75);

    oboe::DefaultStreamValues::FramesPerBurst = mFramesPerBurst;

    fp_audio = static_cast<float_t*>(calloc(mFramesPerBurst, sizeof(float_t)));
    int_audio = static_cast<int16_t*>(calloc(mFramesPerBurst, sizeof(int16_t)));

    recognition_on = false;
    do_recognition = false;

    // Case model
    LOGI("Doing case");
    std::string casemodel_path = modeldir + "tf_model.tflite";
    flatbuffer_model  = tflite::FlatBufferModel::BuildFromFile(casemodel_path.c_str());

    tflite::ops::builtin::BuiltinOpResolver resolver;
    tflite::InterpreterBuilder builder(*flatbuffer_model.get(), resolver);
    builder(&interpreter);
    std::vector<int32> dims = {1, 7};
    interpreter->ResizeInputTensor(0, dims);
    if (interpreter->AllocateTensors() != kTfLiteOk) {
        LOGE("FAILED TO ALLOCATE");
    }
    kaldi::readNumsFromFile(modeldir + "word2tag.int", nid_to_caseid);
    case_zero_index = 10422;
    casepos_zero_index = CASE_INNUM;

    t.join();
    LOGI("Finished constructing rec");
}

RecEngine::~RecEngine() {
    free(fp_audio);
    free(int_audio);
    delete word_syms;
    delete decode_fst;
    delete feature_pipeline;
    delete decoder;
    delete decodable_info;
    delete feature_info;
    delete lexicon_info;
    delete decoder_opts;
    delete os_ctm, os_txt;
}

const char* RecEngine::get_text(){
    return outtext.c_str();
}

const char* RecEngine::get_const_text(){
    return const_outtext.c_str();
}

void RecEngine::reset_text() {
    outtext = "";
    const_outtext = "";
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
//        int16_t ext_size = 0;
//        write_word(f, ext_size, 2);

//        f << "fact";
//        int32_t chk_sz = 4;
//        write_word(f, chk_sz, 4);
//        fact_chunk_pos = f.tellp();
//        f << "----";

        data_chunk_pos = f.tellp();
        f << "data----";  // (chunk size to be filled in later)

        // ---------------- Setting up ASR vars
        decoder_opts = new LatticeFasterDecoderConfig(10.0, 3000, 5.0, 40, 3.0);
        decoder_opts->determinize_lattice = true;

        feature_pipeline = new OnlineNnet2FeaturePipeline(*feature_info);
        decoder = new SingleUtteranceNnet3Decoder(*decoder_opts, trans_model,
                                                         *decodable_info, *decode_fst, feature_pipeline);

        // Text output
        std::string ctmpath = fpath + "_timed.txt";
        os_ctm = fopen(ctmpath.c_str(), "wt");
        std::string txtpath = fpath + ".txt";
        os_txt = fopen(txtpath.c_str(), "wt");

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
    timer.Reset();
}

int RecEngine::stop_trans_stream() {
    float tt = (float) timer.Elapsed();
    LOGI("Time taken %f", tt);
    LOGI("Tot num samples %d, time in s %d", tot_num_frames, tot_num_frames / 16000);
    if (mRecStream != nullptr) {
        KALDI_LOG << "IN STOP TRANS";

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

        // Finishing wav write
        size_t file_length = f.tellp();
        f.seekp(data_chunk_pos + 4);
        int32 num_bytes = file_length - data_chunk_pos - 8;
        write_word(f, num_bytes, 4);

//        int32 num_frames = (file_length - data_chunk_pos - 8) / sizeof(float);
//        f.seekp(fact_chunk_pos);
//        write_word(f, num_frames, 4);

        f.seekp(4);
        write_word(f, file_length - 8, 4);
        f.close();
        return num_out_frames;
    }
    return 0;
}

void RecEngine::write_to_wav(int32 num_frames) {
    f.write((char*) &fp_audio[0], num_frames * 4);
}

void RecEngine::recognition_loop() {
    Timer timer_rnn;
    Timer tt;
    std::vector<std::string> dummy;
    std::vector<int32> dummyb;
    while(recognition_on) {
        bool did_rnn = false;
        if (do_recognition) {
            tt.Reset();
            decoder->AdvanceDecoding();
            LOGI("Decode time %f", tt.Elapsed());

            if (decoder->isPruneTime()) {
                did_rnn = true;
                CompactLattice clat_to_rescore, clat_rescored, clat_bestpath;
                decoder->GetLattice(false, &clat_to_rescore);
                TopSortCompactLatticeIfNeeded(&clat_to_rescore);

                if (t_rnnlm.joinable()) t_rnnlm.join();
                if (t_finishsegment.joinable()) t_finishsegment.join();
                timer_rnn.Reset();
                ComposeCompactLatticePrunedB(*compose_opts, clat_to_rescore,
                                             const_cast<ComposeDeterministicOnDemandFst<StdArc> *>(combined_lms),
                                             &clat_rescored, max_ngram_order, false);
                double tt = timer_rnn.Elapsed();
                KALDI_LOG << "RESCORE TIME TAKEN " << tt;
                CompactLatticeShortestPath(clat_rescored, &clat_bestpath);
                decoder->AdjustCostsWithClatCorrect(&clat_bestpath);
                decoder->StrictPrune();
            }

            if (decoder->isStopTime(silence_phones, trans_model, 3)) {
                int32 num_frames_decoded = decoder->NumFramesDecoded();
                tot_num_frames_decoded += num_frames_decoded;
                LOGI("Stopped %d", num_frames_decoded);
                if (t_finishsegment.joinable()) t_finishsegment.join();
                decoder->GetLattice(false, &finish_seg_clat);

                t_finishsegment = std::thread(&RecEngine::finish_segment, this, &finish_seg_clat, num_frames_decoded);

                decoder->InitDecoding(true);
            }

            if (decoder->NumFramesDecoded() > 0) {
                Lattice olat;
                decoder->GetBestPath(false, &olat);

                std::vector<int32> words;

                if (!GetLinearWordSequence(olat, &words)) LOGE("Failed get linear seq");

                std::string tmpstr = prettify_text(words, dummy, dummyb, false);

                outtext = tmpstr;
            }

            do_recognition = false;
        }
        /*if (!did_rnn) {
            std::this_thread::sleep_for(std::chrono::milliseconds(100));
            did_rnn = false;
        }*/
    }
}


oboe::DataCallbackResult RecEngine::onAudioReady(oboe::AudioStream *audioStream, void *audioData, int32_t numFrames) {

    const float mul = 32768.0f;
    const float cnt_channel_fp = static_cast<float>(mChannelCount);
    float val;
    int32_t num_samples_in = numFrames * mChannelCount;
    tot_num_frames += numFrames;
    // Note we put data in floating point format but in PCM (int) range
    if(mIsfloat) {
        float* audio_data = static_cast<float*>(audioData);
        int k = 0;
        for (int i = 0; i < num_samples_in ; i += mChannelCount) {
            val = 0.0f;
            for (int j = 0; j < mChannelCount; j++) {
                val += audio_data[i + j];
            }
            fp_audio[k] = val * mul;
            k++;
        }
    } else {
        const float mul_div = 1.0f / 32768.0f;
        int16_t* audio_data = static_cast<int16_t*>(audioData);
        int k = 0;
        for (int i = 0; i < num_samples_in; i += mChannelCount) {
            val = 0.0f;
            for (int j = 0; j < mChannelCount; j++) {
                val += static_cast<float>(audio_data[i + j]);
            }
            fp_audio[k] = (val / cnt_channel_fp);
            k++;
        }
    }

    do_recognition = false;  // this is to prevent recognition from starting while appending data
    SubVector<float> data(fp_audio, numFrames);
    feature_pipeline->AcceptWaveform(fin_sample_rate_fp, data);
    do_recognition = true;

    for (int i = 0; i < numFrames; i++) {
        val = fp_audio[i];
        int_audio[i] = static_cast<int16_t>(val + 0.5f);
    }

    f.write((char*) &int_audio[0], numFrames * 2);

//    int32_t bufferSize = mRecStream->getBufferSizeInFrames();
//    auto underrunCount = audioStream->getXRunCount();

//    LOGI("numFrames %d, actual numF %d, Underruns %d, buffer size %d", mFramesPerBurst, numFrames, underrunCount, bufferSize);

    if (numFrames < mFramesPerBurst) {
        for(int i = 0; i < mFramesPerBurst; i++) {
            int_audio[i] = 0;
        }
    }

    return oboe::DataCallbackResult::Continue;
}

void RecEngine::setDeviceId(int32_t deviceId) {
    mRecDeviceId = deviceId;
}


void RecEngine::finish_segment(CompactLattice* clat, int32 num_out_frames) {

    TopSortCompactLatticeIfNeeded(clat);

    CompactLattice best_path;
    if (num_out_frames > 100) {
        if (t_rnnlm.joinable()) t_rnnlm.join();
        CompactLattice clat_rescored;
        ComposeCompactLatticePrunedB(*compose_opts, *clat,
                                     const_cast<ComposeDeterministicOnDemandFst<StdArc> *>(combined_lms),
                                     &clat_rescored, max_ngram_order, true);
        CompactLatticeShortestPath(clat_rescored, &best_path);
    } else {
        CompactLatticeShortestPath(*clat, &best_path);
    }

    CompactLattice aligned_clat;
    WordAlignLatticeLexicon(best_path, trans_model, *lexicon_info, opts, &aligned_clat);

    std::vector<int32> words, times, lengths;
    CompactLatticeToWordAlignment(aligned_clat, &words, &times, &lengths);

    std::vector<std::string> words_split;
    std::vector<int32> indcs_kept;
    std::string text = prettify_text(words, words_split, indcs_kept, true);

    outtext = "";
    const_outtext = const_outtext + text;
    fwrite(text.c_str(), 1, text.size(), os_txt);

    int32 num_words = words_split.size();
    for(size_t j = 0; j < num_words; j++) {
        std::string word = words_split[j];
        fwrite(word.c_str(), 1, word.size(), os_ctm);
        fwrite(" ", 1, 1, os_ctm);
        std::string wtime = std::to_string(frame_shift * times[indcs_kept[j]]);
        size_t sz = wtime.find('.') + 2;
        fwrite(wtime.c_str(), 1, sz, os_ctm);
        fwrite("\n", 1, 1, os_ctm);
    }

    if (rnn_ready) {
        lm_to_add_orig->Clear();
    }  // TODO: check why ClearToContinue is worse


}

int32 RecEngine::run_casing(std::vector<int32> casewords, std::vector<int32> casewords_pos) {
    int32 sz = casewords.size();
    if (sz != CASE_INNUM) {
        LOGE("SIZE IS DIFFERENT THAN CASE_INNUM, SOMETHING WENT WRONG");
    }

    int32* input = interpreter->typed_input_tensor<int32>(0);
    int32* input_pos = interpreter->typed_input_tensor<int32>(1);
    for(int i = 0; i < CASE_INNUM; i++) {
        input[i] = casewords[i];
        input_pos[i] = casewords_pos[i];
    }

    interpreter->Invoke();
    float* output = interpreter->typed_output_tensor<float>(0);
    int32 argmax = -1;
    float maxprob = 0.f;

    for(int32 i = 0; i < 3; i++) {
        float out = output[i];
        if (out > maxprob) {
            maxprob = out;
            argmax = i;
        }
    }
    if (output[0] > 0.1f) argmax = 0;
    return argmax;
}

void RecEngine::get_text_case(std::vector<int32>* words, std::vector<int32>* casing) {
    int32 sz = words->size();
    if (sz < 6) {
        casing->resize(sz, 0);
        return;
    }

    std::vector<int32> words_nosil;

    words_nosil.reserve(sz);
    for(int32 i = 0; i < sz; i++) {  // removes epsilon words
        int32 w = (*words)[i];
        if (w != 0) words_nosil.push_back(w);
    }

    // Convert ids
    sz = words_nosil.size();
    std::vector<int32> casewords(sz);
    for(int32 i = 0; i < sz; i++) {
        int32 id = nid_to_caseid[words_nosil[i]];
        casewords[i] = id;
    }

    std::vector<int32> casewords_sentence(CASE_INNUM, case_zero_index);
    std::vector<int32> casepos_sentence(CASE_INNUM, casepos_zero_index);
    casing->resize(sz, 0);
    for(int32 i = 1; i < sz; i++) {
        int32 startidx = i - (CASE_INNUM - CASE_OFFSET - 1);
        if (startidx < 0) startidx = 0;
        int32 endidx = i + CASE_OFFSET;

        int32 k = CASE_INNUM - 1;
        for(int32 j = endidx; j >= startidx; j--, k--) {
            int32 wid, pos;
            if (j < sz) {
                wid = casewords[j];
                pos = k;
            } else {
                wid = case_zero_index;
                pos = casepos_zero_index;
            }
            casewords_sentence[k] = wid;
            casepos_sentence[k] = pos;
        }

        (*casing)[i] = run_casing(casewords_sentence, casepos_sentence);
    }
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
        if (w == 0) continue;

        std::string word = word_syms->Find(w);

        if (word == "<unk>") word = "[unknown]";

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

void RecEngine::transcribe_file(std::string wavpath, std::string fpath) {
    start_logger();

    try {
        LOGI("Transcribing file");
        using namespace kaldi;
        using namespace fst;

        typedef int32 int32;
        typedef int64 int64;

        BaseFloat chunk_length_secs = 0.72;

        decoder_opts = new LatticeFasterDecoderConfig(10.0, 3000, 5.0, 40, 3.0);
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
        int32 chunk_length;

        if (chunk_length_secs > 0) {
            chunk_length = int32(samp_freq * chunk_length_secs);
            if (chunk_length == 0) chunk_length = 1;
        } else {
            chunk_length = std::numeric_limits<int32>::max();
        }

        int32 samp_offset = 0;
        LOGI("Starting decoding.");

        while (samp_offset < data.Dim()) {
            int32 samp_remaining = data.Dim() - samp_offset;
            int32 num_samp = chunk_length < samp_remaining ? chunk_length : samp_remaining;

            SubVector<BaseFloat> wave_part(data, samp_offset, num_samp);

            feature_pipeline->AcceptWaveform(samp_freq, wave_part);

            samp_offset += num_samp;
            if (samp_offset == data.Dim()) {
                // no more input. flush out last frames
                feature_pipeline->InputFinished();
            }
            decoder->AdvanceDecoding();

        }
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
        throw;
    }

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