package com.hfstudio.flamechunk.client.storage;

import com.hfstudio.flamechunk.common.data.ScanSnapshot;

public class ClientSnapshotStorage {

    private ScanSnapshot snapshot;
    private long elapsedTicks;
    private long totalTicks;
    private boolean scanning;

    public synchronized void clear() {
        snapshot = null;
        elapsedTicks = 0L;
        totalTicks = 0L;
        scanning = true;
    }

    public synchronized void updateProgress(long elapsed, long total) {
        if (total < 1L || elapsed < 0L || elapsed > total) {
            return;
        }
        elapsedTicks = elapsed;
        totalTicks = total;
        scanning = elapsed < total;
    }

    public synchronized void publish(ScanSnapshot value) {
        snapshot = value;
        scanning = false;
        elapsedTicks = value == null ? 0L : value.getSampledTicks();
        totalTicks = value == null ? 0L : value.getSampledTicks();
    }

    public synchronized ScanSnapshot getSnapshot() {
        return snapshot;
    }

    public synchronized boolean isScanning() {
        return scanning;
    }

    public synchronized long getElapsedTicks() {
        return elapsedTicks;
    }

    public synchronized long getTotalTicks() {
        return totalTicks;
    }

    public synchronized float getProgress() {
        return totalTicks <= 0L ? 0.0F : (float) elapsedTicks / (float) totalTicks;
    }
}
