package com.hfstudio.flamechunk.common.tick;

public enum TickCategory {

    RANDOM_TICK,
    SCHEDULED_TICK,
    BLOCK_ENTITY,
    ENTITY,
    MOB_SPAWNING,
    BLOCK_UPDATE,
    BLOCK_EVENT,
    HANDLER,
    GARBAGE_COLLECTION;

    public static final int COUNT = values().length;

    public boolean supportsTypeTiming() {
        return this == ENTITY || this == BLOCK_ENTITY
            || this == HANDLER
            || this == RANDOM_TICK
            || this == SCHEDULED_TICK
            || this == BLOCK_EVENT
            || this == BLOCK_UPDATE;
    }
}
