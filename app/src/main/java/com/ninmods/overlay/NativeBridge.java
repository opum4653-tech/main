package com.ninmods.overlay;

public class NativeBridge {
    static {
        try {
            System.loadLibrary("nin_engine");
        } catch (UnsatisfiedLinkError e) {
            // Library may be loaded dynamically from server download path
        }
    }

    public static boolean loadLibraryFromPath(String path) {
        try {
            System.load(path);
            return true;
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
    }

    // Engine Lifecycle
    public static native boolean initEngine(String targetPackage);
    public static native void stopEngine();
    public static native boolean isAttached();
    public static native int getTargetPid();
    public static native void setScreenDimensions(float width, float height);

    // Aim Controls
    public static native void setAimEnabled(boolean enabled);
    public static native void setAimFov(float radius);
    public static native void setAimHeadshotRate(float rate);
    public static native void setAimSniperLock(boolean enabled);
    public static native void setAimScopeLock(boolean enabled);
    public static native void setAimBone(int bone);
    public static native void setAimTriggerMode(int mode);

    // Frame Telemetry for Canvas Rendering
    // Returns number of players populated. outBuffer stride is 12 floats per player.
    public static native int getPlayersData(float[] outBuffer);

    // Live Target Lock HUD Telemetry: [0]=valid, [1]=dist, [2]=hp, [3]=screenX, [4]=screenY
    public static native void getTargetInfo(float[] outInfo);
}
