#include <stdlib.h>
#include <cstring>
#include <string>
#include <cmath>
#include <iostream>
#include "logging_macros.h"
#include "RecEngine.h"



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

void write_ctm(kaldi::CompactLattice* clat, kaldi::TransitionModel* trans_model,
               kaldi::WordAlignLatticeLexiconInfo lexicon_info, kaldi::WordAlignLatticeLexiconOpts opts,
               kaldi::BaseFloat frame_shift, fst::SymbolTable* word_syms, kaldi::Output* ko);

RecEngine::RecEngine(std::string modeldir): decodable_opts(1.0, 20), feature_opts(modeldir + "fbank.conf", "fbank") {
    // ! -- ASR setup begin
    LOGI("Constructing rec");
    model_dir = modeldir;
    std::string nnet3_rxfilename = modeldir + "final.mdl",
            fst_rxfilename = modeldir + "HCLG.fst",
            align_lex = modeldir + "align_lexicon.int",
            wsyms = modeldir + "words.txt";

    {
        bool binary;
        kaldi::Input ki(nnet3_rxfilename, &binary);
        LOGI("Reading nnet");
        trans_model.Read(ki.Stream(), binary);
        am_nnet.Read(ki.Stream(), binary);
        SetBatchnormTestMode(true, &(am_nnet.GetNnet()));
        SetDropoutTestMode(true, &(am_nnet.GetNnet()));
        kaldi::nnet3::CollapseModel(kaldi::nnet3::CollapseModelConfig(), &(am_nnet.GetNnet()));
    }

    decodable_info = new kaldi::nnet3::DecodableNnetSimpleLoopedInfo(decodable_opts, &am_nnet);
    feature_info = new kaldi::OnlineNnet2FeaturePipelineInfo(feature_opts);

    decode_fst = fst::ReadFstKaldiGeneric(fst_rxfilename);
    word_syms = fst::SymbolTable::ReadText(wsyms);

    std::vector<std::vector<int32>> lexicon;
    {
        std::ifstream is(align_lex, std::ifstream::in);
        kaldi::ReadLexiconForWordAlign(is, &lexicon);
    }
    //kaldi::WordAlignLatticeLexiconInfo lexicon_info(lexicon);
    //kaldi::WordAlignLatticeLexiconOpts opts;

    //ko.Open(ctm_wxfilename, false, true);
    //ko.Stream() << std::fixed;
    //ko.Stream().precision(2);
    //kaldi::BaseFloat frame_shift = 0.03;

    // ! -- ASR setup end
}

RecEngine::~RecEngine() {
    delete word_syms;
    delete decode_fst;
    delete feature_pipeline;
    delete decoder;
    delete decodable_info;
    delete feature_info;
}

const char* RecEngine::get_text(){
    return outtext.c_str();
}

void RecEngine::transcribe_stream(std::string wavpath){
    //start_logger();
    oboe::AudioStreamBuilder builder;
    builder.setSharingMode(oboe::SharingMode::Exclusive);
    builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
    builder.setCallback(this);
    builder.setDirection(oboe::Direction::Input);
    builder.setFramesPerCallback(mFramesPerBurst);
    //builder.setSampleRate(fin_sample_rate);

    oboe::Result result = builder.openStream(&mRecStream);

    oboe::AudioFormat mFormat;
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

        fp_audio_in = static_cast<float*>(malloc(sizeof(float) * mFramesPerBurst));
        frames_out = static_cast<size_t>(mFramesPerBurst * fin_sample_rate / mSampleRate + .5);
        LOGI("Frames out %d", frames_out);
        frames_min = (size_t) (((float) frames_out) * 0.5);

        resamp_audio = static_cast<float*>(calloc(frames_out, sizeof(float)));

        // ---------------- Soxr prep
        const unsigned num_filechannels = 1;
        soxr = soxr_create(mSampleRate, fin_sample_rate, num_filechannels,
                           &soxr_error, NULL, NULL, NULL);
        if (soxr_error) LOGE("Error creating soxr resampler.");

        // ---------------- Wav header
        LOGI("FPATH OUT %s", wavpath.c_str());
        f.open(wavpath.c_str(), std::ios::binary);

        f << "RIFF----WAVEfmt ";
        write_word(f, 16, 4);  // no extension data
        int16_t bytes_perSample = 2;
        write_word(f, 1, 2);  // PCM - int samples
        write_word(f, num_filechannels, 2);
        write_word(f, fin_sample_rate, 4);
        write_word(f, fin_sample_rate * num_filechannels * bytes_perSample, 4 );  // (Sample Rate * Channels * BytesPerSample)
        write_word(f, num_filechannels * bytes_perSample, 2);  // data block size (size of two integer samples, one for each channel, in bytes)
        write_word(f, bytes_perSample * 8, 2);  // number of bits per sample (use a multiple of 8)
        data_chunk_pos = f.tellp();
        f << "data----";  // (chunk size to be filled in later)

        // ---------------- Setting up ASR vars
        kaldi::LatticeFasterDecoderConfig decoder_opts(7.0, 3000, 6.0, 25);

        feature_pipeline = new kaldi::OnlineNnet2FeaturePipeline(*feature_info);
        decoder = new kaldi::SingleUtteranceNnet3Decoder(decoder_opts, trans_model,
                                                         *decodable_info, *decode_fst, feature_pipeline);
        fin_sample_rate_fp = (kaldi::BaseFloat) fin_sample_rate;

        // ---------------- Done

        callb_cnt = 0;
        result = mRecStream->requestStart();
        if (result != oboe::Result::OK) {
            LOGE("Error starting stream. %s", oboe::convertToText(result));
        }

    } else {
        LOGE("Failed to create stream. Error: %s", oboe::convertToText(result));
    }
}

void RecEngine::stop_trans_stream() {
    if (mRecStream != nullptr) {

        oboe::Result result = mRecStream->requestStop();

        if (result != oboe::Result::OK) {
            LOGE("Error stopping output stream. %s", oboe::convertToText(result));
        }

        result = mRecStream->close();
        LOGI("CLOSED");
        if (result != oboe::Result::OK) {
            LOGE("Error closing output stream. %s", oboe::convertToText(result));
        }

        soxr_delete(soxr);
        free(resamp_audio);
        free(fp_audio_in);

        feature_pipeline->InputFinished();
        decoder->AdvanceDecoding();
        decoder->FinalizeDecoding();

        // Finishing wav write
        size_t file_length = f.tellp();
        f.seekp(data_chunk_pos + 4);
        write_word(f, file_length - data_chunk_pos - 8, 4);
        f.seekp(4);
        write_word(f, file_length - 8, 4);
        f.close();

    }
}

oboe::DataCallbackResult RecEngine::onAudioReady(oboe::AudioStream *audioStream, void *audioData, int32_t numFrames) {

    //if (callb_cnt == 2) start_logger();
    const float mul = 32768.0f;
    const float cnt_channel_fp = static_cast<float>(mChannelCount);
    float val;
    int32_t num_samples_in = numFrames * mChannelCount;

    if(mIsfloat) {
        float* audio_data = static_cast<float*>(audioData);
        int k = 0;
        for (int i = 0; i < num_samples_in; i += mChannelCount) {
            val = 0.0f;
            for (int j = 0; j < mChannelCount; j++) {
                val += audio_data[i + j];
            }

            fp_audio_in[k] = (val / cnt_channel_fp);
            k++;
        }

        soxr_error = soxr_process(soxr, fp_audio_in, numFrames, NULL, resamp_audio, frames_out,
                                  &odone);
    } else {
        const float mul = 1.0f / 32768.0f;
        int16_t* audio_data = static_cast<int16_t*>(audioData);
        for (int i = 0; i < num_samples_in; i += mChannelCount) {
            val = 0.0f;
            for (int j = 0; j < mChannelCount; j++) {
                val += (static_cast<float>(audio_data[i + j]) * mul);
            }
            fp_audio_in[i] = (val / cnt_channel_fp);
        }
        soxr_error = soxr_process(soxr, fp_audio_in, numFrames, NULL, resamp_audio, frames_out,
                                  &odone);
    }
    float resamp_int[frames_out];
    for (int i = 0; i < frames_out; i++) {
        val = resamp_audio[i];
        val *= mul;
        resamp_int[i] = val;
        int16_t valint = static_cast<int16_t>(val);
        write_word(f, valint, 2);
    }

    if (odone > frames_min || callb_cnt > 0) {

        kaldi::SubVector<float> data(resamp_int, frames_out);

        feature_pipeline->AcceptWaveform(fin_sample_rate_fp, data);
        decoder->AdvanceDecoding();

        if ((callb_cnt + 1) % 3 == 0) {

            kaldi::Lattice olat;
            decoder->GetBestPath(false, &olat);

            std::vector<int32> words, tmpa;
            kaldi::LatticeWeight tmpb;
            if (!GetLinearSymbolSequence(olat, &tmpa, &words, &tmpb)) LOGE("Failed get linear seq");

            std::string tmpstr = "";
            for (size_t j = 0; j < words.size(); j++) {
                tmpstr += (word_syms->Find(words[j]) + " ");
            }

            outtext = tmpstr;

        }
    } else {
        callb_cnt--;
    }
    //int32_t bufferSize = mRecStream->getBufferSizeInFrames();
    //int32_t underrunCount = audioStream->getXRunCount();

    //LOGI("numFrames %d, Underruns %d, buffer size %d, outframes %zu",
    //     numFrames, underrunCount, bufferSize, odone);

    memset(resamp_audio, 0, frames_out);
    callb_cnt++;
    return oboe::DataCallbackResult::Continue;
}

void RecEngine::setDeviceId(int32_t deviceId) {
    mRecDeviceId = deviceId;
}

void RecEngine::transcribe_file(std::string wavpath, std::string ctm) {
    //start_logger();
    try {
        LOGI("Transcribing file");
        using namespace kaldi;
        using namespace fst;

        typedef kaldi::int32 int32;
        typedef kaldi::int64 int64;

        BaseFloat chunk_length_secs = 0.18;

        kaldi::LatticeFasterDecoderConfig decoder_opts(7.0, 3000, 6.0, 25);

        feature_pipeline = new kaldi::OnlineNnet2FeaturePipeline(*feature_info);
        decoder = new kaldi::SingleUtteranceNnet3Decoder(decoder_opts, trans_model,
                                                         *decodable_info, *decode_fst, feature_pipeline);
        fin_sample_rate_fp = (kaldi::BaseFloat) fin_sample_rate;

        std::string wav_rspecifier = wavpath, ctm_wxfilename = ctm;
        std::string align_lex = model_dir + "align_lexicon.int";

        std::vector<std::vector<int32>> lexicon;
        {
            std::ifstream is(align_lex, std::ifstream::in);
            kaldi::ReadLexiconForWordAlign(is, &lexicon);
        }
        kaldi::WordAlignLatticeLexiconInfo lexicon_info(lexicon);
        kaldi::WordAlignLatticeLexiconOpts opts;

        Output ko(ctm_wxfilename, false);
        ko.Stream() << std::fixed;
        ko.Stream().precision(2);
        BaseFloat frame_shift = 0.03;

        kaldi::WaveHolder wavholder;
        std::ifstream wavis(wavpath, std::ios::binary);
        wavholder.Read(wavis);
        const kaldi::WaveData &wave_data = wavholder.Value();
        kaldi::SubVector<kaldi::BaseFloat> data(wave_data.Data(), 0);

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

        write_ctm(&clat, &trans_model, lexicon_info, opts, frame_shift, word_syms, &ko);

        LOGI("Decoded file.");

    } catch(const std::exception& e) {
        LOGE("FAILED FAILED FAILED");
        LOGE("ERROR %s",  e.what());
        throw;
    }
}

void write_ctm(kaldi::CompactLattice* clat, kaldi::TransitionModel* trans_model,
               kaldi::WordAlignLatticeLexiconInfo lexicon_info, kaldi::WordAlignLatticeLexiconOpts opts,
               kaldi::BaseFloat frame_shift, fst::SymbolTable* word_syms, kaldi::Output* ko) {

    kaldi::CompactLattice aligned_clat;
    WordAlignLatticeLexicon(*clat, *trans_model, lexicon_info, opts, &aligned_clat);

    kaldi::CompactLattice best_path;
    kaldi::CompactLatticeShortestPath(aligned_clat, &best_path);

    std::vector<int32> words, times, lengths;
    kaldi::CompactLatticeToWordAlignment(best_path, &words, &times, &lengths);

    for(size_t j=0; j < words.size(); j++) {
        if(words[j] == 0)
            continue;
        ko->Stream() << word_syms->Find(words[j]) << ' ' << (frame_shift * times[j]) << ' '
                    << (frame_shift * lengths[j]) << std::endl;
    }
}