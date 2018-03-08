package ark.ark;


public class RecEngine {

    static long mEngineHandle = 0;

    static {
        System.loadLibrary("rec-engine");
    }

    static boolean create(){

        if (mEngineHandle == 0){
            mEngineHandle = native_createEngine();
        }
        return (mEngineHandle != 0);
    }

    static void delete(){
        if (mEngineHandle != 0){
            native_deleteEngine(mEngineHandle);
        }
        mEngineHandle = 0;
    }

    static void setAudioDeviceId(int deviceId){
        if (mEngineHandle != 0) native_setAudioDeviceId(mEngineHandle, deviceId);
    }

    private static native long native_createEngine();
    private static native void native_deleteEngine(long engineHandle);
    private static native void native_setAudioDeviceId(long engineHandle, int deviceId);
}
