package ark.ark;


import android.util.Log;

public class RecEngine {

    static long mEngineHandle = 0;
    static long cnt_start = 0;

    static {
        System.loadLibrary("rec-engine");
    }

    static long create(String modeldir){
        cnt_start++;
        Log.i("APP", String.format("In create %d", mEngineHandle));
        if (mEngineHandle == 0){
            mEngineHandle = native_createEngine(modeldir);
        }
        return mEngineHandle;
    }

    static void delete(){
        cnt_start--;
        Log.i("APP", String.format("Deleting %d", mEngineHandle));
        if (mEngineHandle != 0 && cnt_start == 0){
            Log.i("APP", String.format("Actually deleting %d", mEngineHandle));
            native_deleteEngine(mEngineHandle);
            mEngineHandle = 0;
        }
    }

    static void transcribe_stream(String wavpath) {
        Log.i("APP", String.format("Using %d", mEngineHandle));
        if (mEngineHandle != 0) {
            native_transcribe_stream(mEngineHandle, wavpath);
        }
    }

    static void stop_trans_stream() {
        native_stop_trans_stream(mEngineHandle);
    }

    static void transcribe_file(String wavpath, String ctm, String modeldir) {
        if (mEngineHandle == 0) {
            mEngineHandle = create(modeldir);
        }
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

    private static native long native_createEngine(String modeldir);
    private static native void native_deleteEngine(long engineHandle);
    private static native void native_setAudioDeviceId(long engineHandle, int deviceId);
    private static native String native_getText(long engineHandle);
    private static native void native_transcribe_stream(long engineHandle, String wavpath);
    private static native void native_stop_trans_stream(long engineHandle);
    private static native void native_transcribe_file(long engineHandle, String wavpath, String ctm);
}
