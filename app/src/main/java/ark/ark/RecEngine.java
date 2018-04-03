package ark.ark;


public class RecEngine {

    static long mEngineHandle = 0;

    static {
        System.loadLibrary("rec-engine");
    }

    static boolean create(String fname){

        if (mEngineHandle == 0){
            mEngineHandle = native_createEngine(fname);
        }
        return (mEngineHandle != 0);
    }

    static void delete(){
        if (mEngineHandle != 0){
            native_deleteEngine(mEngineHandle);
        }
        mEngineHandle = 0;
    }

    static void transcribe(String wavpath, String modeldir, String ctm) {
        native_transcribe(wavpath, modeldir, ctm);
    }

    static void setAudioDeviceId(int deviceId){
        if (mEngineHandle != 0) native_setAudioDeviceId(mEngineHandle, deviceId);
    }

    private static native long native_createEngine(String fname);
    private static native void native_deleteEngine(long engineHandle);
    private static native void native_setAudioDeviceId(long engineHandle, int deviceId);
    private static native void native_transcribe(String wavpath, String modeldir, String ctm);
}
