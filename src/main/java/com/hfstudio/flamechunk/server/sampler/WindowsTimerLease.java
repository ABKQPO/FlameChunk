package com.hfstudio.flamechunk.server.sampler;

import com.hfstudio.flamechunk.FlameChunk;
import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;

public class WindowsTimerLease implements AutoCloseable {

    public static final int PERIOD_MILLIS = 1;
    public boolean acquired;

    public synchronized boolean acquire() {
        if (acquired || !System.getProperty("os.name", "")
            .startsWith("Windows")) {
            return true;
        }
        try {
            int result = WinMM.begin.invokeInt(new Object[] { PERIOD_MILLIS });
            acquired = result == 0;
            if (!acquired) {
                FlameChunk.LOG.warn("Windows timer request failed with status {}", result);
            }
            return acquired;
        } catch (RuntimeException | LinkageError exception) {
            FlameChunk.LOG.warn("Unable to request the Windows sampling timer", exception);
            return false;
        }
    }

    @Override
    public synchronized void close() {
        if (!acquired) {
            return;
        }
        acquired = false;
        try {
            int result = WinMM.end.invokeInt(new Object[] { PERIOD_MILLIS });
            if (result != 0) {
                FlameChunk.LOG.warn("Windows timer release failed with status {}", result);
            }
        } catch (RuntimeException | LinkageError exception) {
            FlameChunk.LOG.warn("Unable to release the Windows sampling timer", exception);
        }
    }

    public static class WinMM {

        public static final NativeLibrary library = NativeLibrary.getInstance("winmm");
        public static final Function begin = library.getFunction("timeBeginPeriod", Function.ALT_CONVENTION);
        public static final Function end = library.getFunction("timeEndPeriod", Function.ALT_CONVENTION);
    }
}
