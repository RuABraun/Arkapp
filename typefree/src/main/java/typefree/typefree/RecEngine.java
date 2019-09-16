package typefree.typefree;


import android.util.Log;

import java.util.concurrent.Semaphore;


public class RecEngine {

    private static RecEngine instance;
    private static boolean doing_creation = false;
    public static Semaphore available = new Semaphore(1);

    static long mEngineHandle = 0;
    static boolean isready = false;
    static boolean isrunning = false;

    private RecEngine(final String modeldir, int[] exclusiveCores) {
        doing_creation = true;
        Log.i("APP", "Creating engine " + Long.toString(mEngineHandle));
        if (mEngineHandle == 0) {
            mEngineHandle = native_createEngine(modeldir, exclusiveCores);
            Log.i("APP", "New engine handle " + Long.toString(mEngineHandle));
        }
        isready = true;
        doing_creation = false;
    }

    public static RecEngine getInstance(String modeldir, int[] exclusiveCores) {

        if (instance == null && !doing_creation) {
            instance = new RecEngine(modeldir, exclusiveCores);
        }
        return instance;
    }

    public void delete() {
        if (mEngineHandle != 0 && isready == true)
        native_deleteEngine(mEngineHandle);
        isready = false;
        mEngineHandle = 0;
    }

    public void transcribe_stream(String wavpath) {
        Log.i("APP", String.format("Using EngineHandle: %d", mEngineHandle));
        if (mEngineHandle != 0) {
            isrunning = true;
            native_transcribe_stream(mEngineHandle, wavpath);
        }
    }

    public long stop_trans_stream() {
        long handle = native_stop_trans_stream(mEngineHandle);
        return handle;
    }


    public String get_text() {
        if (mEngineHandle != 0) {
            return native_getText(mEngineHandle);
        } else {
            return "!ERROR";
        }
    }

    public String get_const_text() {
        if (mEngineHandle != 0) {
            return native_getConstText(mEngineHandle);
        } else {
            return "!ERROR";
        }
    }

    public void setAudioDeviceId(int deviceId){
        if (mEngineHandle != 0) native_setAudioDeviceId(mEngineHandle, deviceId);
    }

    public int transcribe_file(String wavpath, String fpath) {
        Log.i("APP", String.format("Using EngineHandle: %d", mEngineHandle));
        if (mEngineHandle != 0) {
            isrunning = true;
            int return_code = native_transcribe_file(mEngineHandle, wavpath, fpath);
            isrunning = false;
            return return_code;
        }
        return 1;
    }

    public int convert_audio(String audiopath, String wavpath) {
        int n = -1;
        if (mEngineHandle != 0) {
            n = native_convertAudio(mEngineHandle, audiopath, wavpath);
        }
        return n;
    }

    public static native long native_createEngine(String modeldir, int exclusiveCores[]);
    public static native void native_deleteEngine(long engineHandle);
    public static native void native_setAudioDeviceId(long engineHandle, int deviceId);
    public static native String native_getText(long engineHandle);
    public static native String native_getConstText(long engineHandle);
    public static native void native_transcribe_stream(long engineHandle, String wavpath);
    public static native long native_stop_trans_stream(long engineHandle);
    public static native int native_transcribe_file(long engineHandle, String wavpath, String ctm);
    public static native int native_convertAudio(long engineHandle, String audiopath, String wavpath);
}
