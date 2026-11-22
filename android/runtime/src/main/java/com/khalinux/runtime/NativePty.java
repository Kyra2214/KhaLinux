package com.khalinux.runtime;

import java.io.IOException;

/** JNI bridge for the Android PTY backend. Loaded only when an interactive session is requested. */
public final class NativePty {
    static {
        System.loadLibrary("khalinux_pty");
    }

    private NativePty() { }

    public static native int[] spawn(String[] arguments, String[] environment, int rows, int columns)
            throws IOException;

    public static native int read(int masterFd, byte[] buffer, int offset, int length) throws IOException;

    public static native int write(int masterFd, byte[] buffer, int offset, int length) throws IOException;

    public static native boolean resize(int masterFd, int rows, int columns) throws IOException;

    public static native int waitFor(int processId) throws IOException;

    public static native boolean signal(int processId, boolean force) throws IOException;

    public static native void close(int fileDescriptor);
}
