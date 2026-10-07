package com.hfstudio.flamechunk.server.sampler;

import com.hfstudio.flamechunk.common.tick.TickCategory;

public class WorkContextTracker {

    public static final int MAX_DEPTH = 256;
    public static final int NO_DIMENSION = Integer.MIN_VALUE;
    public final Slot[] slots = new Slot[MAX_DEPTH];
    public final Thread owner;
    public volatile long revision;
    public volatile long tickId;
    public volatile int depth;

    public WorkContextTracker(Thread owner) {
        this.owner = owner;
        for (int index = 0; index < slots.length; index++) {
            slots[index] = new Slot();
        }
    }

    public void beginTick(long id) {
        revision++;
        depth = 0;
        tickId = id;
        revision++;
    }

    public void endTick() {
        revision++;
        tickId = 0;
        depth = 0;
        revision++;
    }

    public int enter(TickCategory category, int dimensionId, String typeName) {
        if (tickId == 0 || Thread.currentThread() != owner || category == null) {
            return 0;
        }
        int previousDepth = depth;
        if (previousDepth == MAX_DEPTH) {
            return 0;
        }
        revision++;
        Slot slot = slots[previousDepth];
        slot.category = category;
        slot.dimensionId = dimensionId;
        slot.typeName = typeName == null || typeName.isEmpty() ? "Unknown" : typeName;
        depth = previousDepth + 1;
        revision++;
        return previousDepth + 1;
    }

    public void leave(int token) {
        if (token == 0 || Thread.currentThread() != owner || tickId == 0) {
            return;
        }
        revision++;
        depth = token - 1;
        revision++;
    }

    public boolean read(Observation observation) {
        long id = tickId;
        if (id == 0) {
            return false;
        }
        observation.tickId = id;
        observation.category = TickCategory.TASK;
        observation.dimensionId = NO_DIMENSION;
        observation.typeName = "Unknown";
        observation.consistent = false;
        for (int attempt = 0; attempt < 3; attempt++) {
            long before = revision;
            if ((before & 1) != 0) {
                continue;
            }
            long currentTick = tickId;
            int currentDepth = depth;
            TickCategory category = TickCategory.TASK;
            int dimension = NO_DIMENSION;
            String typeName = "Unknown";
            if (currentDepth > 0) {
                Slot slot = slots[currentDepth - 1];
                category = slot.category;
                dimension = slot.dimensionId;
                typeName = slot.typeName;
            }
            if (before == revision && currentTick == id && currentTick != 0) {
                observation.category = category;
                observation.dimensionId = dimension;
                observation.typeName = typeName;
                observation.consistent = true;
                return true;
            }
        }
        return tickId == id;
    }

    public static class Slot {

        public volatile TickCategory category = TickCategory.TASK;
        public volatile int dimensionId = NO_DIMENSION;
        public volatile String typeName = "Unknown";
    }

    public static class Observation {

        public long tickId;
        public TickCategory category;
        public int dimensionId;
        public String typeName;
        public boolean consistent;
    }
}
