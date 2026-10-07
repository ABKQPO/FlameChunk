package com.hfstudio.flamechunk.client.storage;

import java.util.ArrayList;
import java.util.List;

import com.hfstudio.flamechunk.common.data.ScanSnapshot;
import com.hfstudio.flamechunk.common.data.WeakChunkSnapshot;
import com.hfstudio.flamechunk.common.network.packet.ScanProgressPacket;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public class ClientSnapshotStorage {

    public ScanSnapshot snapshot;
    public final Int2ObjectOpenHashMap<WeakChunkSnapshot> weakSnapshots = new Int2ObjectOpenHashMap<>();
    public long elapsedTicks;
    public long totalTicks;
    public boolean scanning;
    public int status = -1;

    public synchronized void clear() {
        snapshot = null;
        elapsedTicks = 0L;
        totalTicks = 0L;
        scanning = false;
        status = -1;
    }

    public synchronized void reset() {
        clear();
        weakSnapshots.clear();
    }

    public synchronized void updateProgress(long elapsed, long total) {
        if (total < 1L || elapsed < 0L || elapsed > total) {
            return;
        }
        elapsedTicks = elapsed;
        totalTicks = total;
        scanning = elapsed < total;
        status = -1;
    }

    public synchronized void publish(ScanSnapshot value) {
        publish(value, true);
    }

    public synchronized void publish(ScanSnapshot value, boolean finalSnapshot) {
        snapshot = value;
        if (finalSnapshot) {
            scanning = false;
            status = -1;
            elapsedTicks = value == null ? 0L : value.getSampledTicks();
            totalTicks = value == null ? 0L : value.getSampledTicks();
        } else if (value != null) {
            if (totalTicks <= 0L) {
                totalTicks = value.getDurationSeconds() * 20L;
            }
            elapsedTicks = Math.max(elapsedTicks, Math.min(value.getSampledTicks(), totalTicks));
            scanning = true;
            status = ScanProgressPacket.STARTED;
        }
    }

    public synchronized ScanSnapshot getSnapshot() {
        return snapshot;
    }

    public synchronized void publishWeakSnapshot(WeakChunkSnapshot value) {
        if (value != null) {
            weakSnapshots.put(value.getDimensionId(), value);
        }
    }

    public synchronized WeakChunkSnapshot getWeakSnapshot(int dimensionId) {
        return weakSnapshots.get(dimensionId);
    }

    public synchronized List<WeakChunkSnapshot> getWeakSnapshots() {
        return new ArrayList<>(weakSnapshots.values());
    }

    public synchronized boolean isScanning() {
        return scanning;
    }

    public synchronized boolean hasPendingScan() {
        return scanning || status == ScanProgressPacket.QUEUED || status == ScanProgressPacket.STARTED;
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

    public synchronized void setScanStatus(int value) {
        if (value == -1) {
            status = -1;
            scanning = false;
            return;
        }
        if (value >= ScanProgressPacket.QUEUED && value <= ScanProgressPacket.SERVER_UNAVAILABLE) {
            status = value;
            scanning = value == ScanProgressPacket.STARTED;
        }
    }

    public synchronized int getStatus() {
        return status;
    }
}
