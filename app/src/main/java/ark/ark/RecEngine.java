package ark.ark;


import android.util.Log;

import java.util.concurrent.Semaphore;


public class RecEngine {

    private static RecEngine instance;
    private static boolean doing_creation = false;
    public static Semaphore available = new Semaphore(1);

    static long mEngineHandle = 0;
    static boolean isready = false;

    private RecEngine(final String modeldir) {
        if (!doing_creation) {
            doing_creation = true;
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    Log.i("APP", "Creating engine");
                    mEngineHandle = native_createEngine(modeldir);
                    isready = true;
                    doing_creation = false;
                }
            });
            t.setPriority(10);
            t.start();
        }
    }

    public static RecEngine getInstance(String modeldir) {

        if (instance == null) {
            instance = new RecEngine(modeldir);
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
        Log.i("APP", String.format("Using %d", mEngineHandle));
        if (mEngineHandle != 0) {
            native_transcribe_stream(mEngineHandle, wavpath);
        }
    }

    public long stop_trans_stream() {
        return native_stop_trans_stream(mEngineHandle);
    }


    public String get_text() {
        if (mEngineHandle != 0) {
            return native_getText(mEngineHandle);
        } else {
            return "!ERROR";
        }
    }

    public void setAudioDeviceId(int deviceId){
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
