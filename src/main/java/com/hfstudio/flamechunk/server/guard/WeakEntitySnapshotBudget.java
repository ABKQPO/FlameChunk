package com.hfstudio.flamechunk.server.guard;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.Entity;

public class WeakEntitySnapshotBudget {

    public static final int MAX_RETAINED_ENTITIES = 1000000;
    public static int retainedEntities;

    public static List<Entity> capture(List<Entity> entities, int requestedLimit) {
        int available = Math.max(0, MAX_RETAINED_ENTITIES - retainedEntities);
        int snapshotSize = Math.min(entities.size(), Math.min(Math.max(0, requestedLimit), available));
        List<Entity> snapshot = new ArrayList<>(snapshotSize);
        for (int index = 0; index < snapshotSize; index++) {
            snapshot.add(entities.get(index));
        }
        retainedEntities += snapshotSize;
        return snapshot;
    }

    public static void release(int entityCount) {
        retainedEntities = Math.max(0, retainedEntities - Math.max(0, entityCount));
    }
}
