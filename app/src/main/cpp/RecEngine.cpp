#include <stdlib.h>
#include <cstring>
#include <string>
#include <cmath>
#include <iostream>
#include "logging_macros.h"
#include "RecEngine.h"
#include <android/log.h>
#include "feat/wave-reader.h"
/*#include "online2/online-nnet3-decoding.h"
#include "online2/online-nnet2-feature-pipeline.h"
#include "online2/onlinebin-util.h"
#include "online2/online-timing.h"
#include "fstext/fstext-lib.h"
#include "lat/lattice-functions.h"
#include "lat/word-align-lattice-lexicon.h"
#include "util/kaldi-thread.h"
#include "nnet3/nnet-utils.h"*/


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

RecEngine::RecEngine(std::string fname) {

    createRecStream(fname);
}

RecEngine::~RecEngine() {
    closeOutputStream();
}

void RecEngine::createRecStream(std::string fname) {
    oboe::AudioStreamBuilder builder;
    builder.setSharingMode(oboe::SharingMode::Exclusive);
    builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
    builder.setCallback(this);
    builder.setDirection(oboe::Direction::Input);
    builder.setFramesPerCallback(mFramesPerBurst);

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

        if (!mIsfloat)
            fp_audio_in = static_cast<float*>(malloc(sizeof(float) * mFramesPerBurst * mChannelCount));
        frames_out = static_cast<size_t>(mFramesPerBurst * fin_sample_rate / mSampleRate + .5);

        resamp_audio = static_cast<float*>(calloc(frames_out * mChannelCount, sizeof(float)));

        // Soxr prep
        soxr = soxr_create(mSampleRate, fin_sample_rate, mChannelCount,
                           &soxr_error, NULL, NULL, NULL);
        if (soxr_error) LOGE("Error creating soxr resampler.");

        // Wav header
        LOGI("FPATH OUT %s", fname.c_str());
        f.open(fname.c_str(), std::ios::binary);

        f << "RIFF----WAVEfmt ";
        const unsigned num_filechannels = 1;
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

        if (result != oboe::Result::OK) {
            LOGE("Error stopping output stream. %s", oboe::convertToText(result));
        }

        soxr_delete(soxr);
        free(resamp_audio);
        if(!mIsfloat) {
            free(fp_audio_in);
        }

        // Finishing wav write
        size_t file_length = f.tellp();
        f.seekp(data_chunk_pos + 4);
        write_word(f, file_length - data_chunk_pos + 8, 4);
        f.seekp(4);
        write_word(f, file_length - 8, 4);
        f.close();

        result = mRecStream->close();
        if (result != oboe::Result::OK) {
            LOGE("Error closing output stream. %s", oboe::convertToText(result));
        }
    }
}

oboe::DataCallbackResult RecEngine::onAudioReady(oboe::AudioStream *audioStream, void *audioData, int32_t numFrames) {

    if(mIsfloat) {
        float* audio_data = static_cast<float*>(audioData);
        //resamp_audio = audio_data;
        soxr_error = soxr_process(soxr, audio_data, numFrames, NULL, resamp_audio, frames_out,
                                  &odone);
    } else {
        const float mul = 1.0f / 32768.0f;
        int16_t* audio_data = static_cast<int16_t*>(audioData);
        int32_t num_samples_in = numFrames * mChannelCount;
        for (int i = 0; i < num_samples_in; i++) {
            float val = static_cast<float>(audio_data[i]);
            fp_audio_in[i] = (val * mul);
        }
        soxr_error = soxr_process(soxr, fp_audio_in, numFrames, NULL, resamp_audio, frames_out,
                                  &odone);
    }

    const float mul = 32768.0f;
    const float nmul = -32768.0f;
    const float cnt_channel_fp = static_cast<float>(mChannelCount);
    const float num_samples = frames_out * mChannelCount;
    float val;
    for(int i=0;i < num_samples;i+=mChannelCount) {
        val = 0.0f;
        for(int j=0;j<mChannelCount;j++) {
            val += resamp_audio[i+j];
        }

        val = val / cnt_channel_fp;
        val = val * mul;
        if (val > mul) val = mul;
        if (val < nmul) {
            val = nmul;
        }
        int16_t valint = static_cast<int16_t>(val);
        write_word(f, valint, 2);
    }

    int32_t bufferSize = mRecStream->getBufferSizeInFrames();
    int32_t underrunCount = audioStream->getXRunCount();

    //LOGI("numFrames %d, Underruns %d, buffer size %d, outframes %zu",
    //     numFrames, underrunCount, bufferSize, odone);

    return oboe::DataCallbackResult::Continue;
}

void RecEngine::setDeviceId(int32_t deviceId) {
    mRecDeviceId = deviceId;
}

/*void RecEngine::transcribe_file(std::string wavpath, std::string ctm) {

    try {
        using namespace kaldi;
        using namespace fst;

        typedef kaldi::int32 int32;
        typedef kaldi::int64 int64;

        // feature_opts includes configuration for the iVector adaptation,
        // as well as the basic features.
        OnlineNnet2FeaturePipelineConfig feature_opts();
        nnet3::NnetSimpleLoopedComputationOptions decodable_opts;
        LatticeFasterDecoderConfig decoder_opts;

        BaseFloat chunk_length_secs = 0.18;
        bool online = true;


        feature_opts.Register(&po);
        decodable_opts.Register(&po);
        decoder_opts.Register(&po);

        std::string nnet3_rxfilename = po.GetArg(1),
        fst_rxfilename = po.GetArg(2),
        wav_rspecifier = po.GetArg(3),
        align_lex = po.GetArg(4),
        wsyms = po.GetArg(5),
        ctm_wxfilename = po.GetArg(6);

        OnlineNnet2FeaturePipelineInfo feature_info(feature_opts);

        TransitionModel trans_model;
        nnet3::AmNnetSimple am_nnet;
        {
        bool binary;
        Input ki(nnet3_rxfilename, &binary);
        trans_model.Read(ki.Stream(), binary);
        am_nnet.Read(ki.Stream(), binary);
        SetBatchnormTestMode(true, &(am_nnet.GetNnet()));
        SetDropoutTestMode(true, &(am_nnet.GetNnet()));
        nnet3::CollapseModel(nnet3::CollapseModelConfig(), &(am_nnet.GetNnet()));
        }

        // this object contains precomputed stuff that is used by all decodable
        // objects.    It takes a pointer to am_nnet because if it has iVectors it has
        // to modify the nnet to accept iVectors at intervals.
        nnet3::DecodableNnetSimpleLoopedInfo decodable_info(decodable_opts, &am_nnet);


        fst::Fst<fst::StdArc> *decode_fst = ReadFstKaldiGeneric(fst_rxfilename);

        fst::SymbolTable *word_syms = NULL;
        if (word_syms_rxfilename != "")
        if (!(word_syms = fst::SymbolTable::ReadText(word_syms_rxfilename)))
        KALDI_ERR << "Could not read symbol table from file " << word_syms_rxfilename;

        OnlineTimingStats timing_stats;

        std::ifstream is(align_lex, std::ifstream::in);
        std::vector<std::vector<int32>> lexicon;
        ReadLexiconForWordAlign(is, &lexicon);
        WordAlignLatticeLexiconInfo lexicon_info(lexicon);
        WordAlignLatticeLexiconOpts opts;

        Output ko(ctm_wxfilename, false);
        ko.Stream() << std::fixed;
        ko.Stream().precision(2);
        BaseFloat frame_shift = 0.03;
        word_syms = fst::SymbolTable::ReadText(wsyms);

        WaveHolder wavholder;
        std::ifstream wavis(wav_rspecifier, std::ios::binary);
        wavholder.Read(wavis);
        const WaveData &wave_data = wavholder.Value();
        // get the data for channel zero (if the signal is not mono, we only
        // take the first channel).
        SubVector<BaseFloat> data(wave_data.Data(), 0);

        OnlineNnet2FeaturePipeline feature_pipeline(feature_info);

        OnlineSilenceWeighting silence_weighting(
        trans_model,
        feature_info.silence_weighting_config,
        decodable_opts.frame_subsampling_factor);

        SingleUtteranceNnet3Decoder decoder(decoder_opts, trans_model,
        decodable_info, *decode_fst, &feature_pipeline);
        OnlineTimer decoding_timer("wavfile");

        BaseFloat samp_freq = wave_data.SampFreq();
        int32 chunk_length;
        if (chunk_length_secs > 0) {
        chunk_length = int32(samp_freq * chunk_length_secs);
        if (chunk_length == 0) chunk_length = 1;
        } else {
        chunk_length = std::numeric_limits<int32>::max();
        }

        int32 samp_offset = 0;
        std::vector<std::pair<int32, BaseFloat> > delta_weights;

        while (samp_offset < data.Dim()) {
        int32 samp_remaining = data.Dim() - samp_offset;
        int32 num_samp = chunk_length < samp_remaining ? chunk_length : samp_remaining;

        SubVector<BaseFloat> wave_part(data, samp_offset, num_samp);
        feature_pipeline.AcceptWaveform(samp_freq, wave_part);

        samp_offset += num_samp;
        decoding_timer.WaitUntil(samp_offset / samp_freq);
        if (samp_offset == data.Dim()) {
        // no more input. flush out last frames
        feature_pipeline.InputFinished();
        }

        if (silence_weighting.Active() &&
        feature_pipeline.IvectorFeature() != NULL) {
        silence_weighting.ComputeCurrentTraceback(decoder.Decoder());
        silence_weighting.GetDeltaWeights(feature_pipeline.NumFramesReady(), &delta_weights);
        feature_pipeline.IvectorFeature()->UpdateFrameWeights(delta_weights);
        }

        decoder.AdvanceDecoding();
        }
        decoder.FinalizeDecoding();

        CompactLattice clat;
        bool end_of_utterance = true;
        decoder.GetLattice(end_of_utterance, &clat);

        decoding_timer.OutputStats(&timing_stats);

        CompactLattice aligned_clat;
        WordAlignLatticeLexicon(clat, trans_model, lexicon_info, opts, &aligned_clat);

        CompactLattice best_path;
        CompactLatticeShortestPath(aligned_clat, &best_path);

        std::vector<int32> words, times, lengths;
        CompactLatticeToWordAlignment(best_path, &words, &times, &lengths);

        for(size_t j=0; j < words.size(); j++) {
        if(words[j] == 0)
        continue;
        ko.Stream() << word_syms->Find(words[j]) << ' ' << (frame_shift * times[j]) << ' '
        << (frame_shift * lengths[j]) << std::endl;
        }
        KALDI_LOG << "Decoded file";
        timing_stats.Print(online);

        delete decode_fst;
        delete word_syms; // will delete if non-NULL.
        return 0;
    } catch(const std::exception& e) {
        std::cerr << e.what();
        return -1;
    }
}*/

