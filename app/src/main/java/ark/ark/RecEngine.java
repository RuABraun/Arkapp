package ark.ark;


import android.util.Log;

class CreateRecEngRunnable implements Runnable {
    String mdir;
    CreateRecEngRunnable(String mdir) {
        this.mdir = mdir;
    }

    @Override
    public void run() {
        if (Base.available.tryAcquire()) {
            RecEngine.create(this.mdir);
            RecEngine.isready = true;
            Base.available.release();
        }
    }
}

public class RecEngine {

    static long mEngineHandle = 0;
    static long cnt_start = 0;
    static boolean isready = false;

    static {
        System.loadLibrary("rec-engine");
    }

    static long create(String modeldir){
        cnt_start++;
        if (mEngineHandle == 0){
            mEngineHandle = native_createEngine(modeldir);
        }
        return mEngineHandle;
    }

    static void delete(){
        cnt_start--;

        if (mEngineHandle != 0 && cnt_start == 0){
            native_deleteEngine(mEngineHandle);
            mEngineHandle = 0;
            isready = false;
        }
    }

    static void transcribe_stream(String wavpath) {
        Log.i("APP", String.format("Using %d", mEngineHandle));
        if (mEngineHandle != 0) {
            native_transcribe_stream(mEngineHandle, wavpath);
        }
    }

    static long stop_trans_stream() {
        return native_stop_trans_stream(mEngineHandle);
    }

    static void transcribe_file(String wavpath, String ctm, String modeldir) {
        native_transcribe_file(mEngineHandle, wavpath, ctm);
    }

    static String get_text() {
        if (mEngineHandle != 0) {
            return native_getText(mEngineHandle);
        } else {
            return "!ERROR";
        }
    }

    static void setAudioDeviceId(int deviceId){
        if (mEngineHandle != 0) native_setAudioDeviceId(mEngineHandle, deviceId);
    }

    public static native long native_createEngine(String modeldir);
    public static native void native_deleteEngine(long engineHandle);
    public static native void native_setAudioDeviceId(long engineHandle, int deviceId);
    public static native String native_getText(long engineHandle);
    public static native void native_transcribe_stream(long engineHandle, String wavpath);
    public static native long native_stop_trans_stream(long engineHandle);
    public static native void native_transcribe_file(long engineHandle, String wavpath, String ctm);
}
