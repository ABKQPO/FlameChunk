package com.hfstudio.flamechunk.common.data;

import java.util.List;

import lombok.Getter;

@Getter
public class WeakChunkSnapshot {

    public static final int MAX_CHUNKS = 8;
    public static final int MAX_ENTITY_TYPES = 16;
    public static final int MAX_TYPE_ID_LENGTH = 64;
    public static final int ENTITY_WARNING_THRESHOLD = 50;
    public static final int ENTITY_CLEAR_THRESHOLD = 200;
    public static final int ENTITY_RED_THRESHOLD = 350;
    public static final int ENTITY_PURPLE_THRESHOLD = 500;

    public final int dimensionId;
    public final long generatedAtTick;
    public final boolean truncated;
    public final List<ChunkEntry> chunks;

    public WeakChunkSnapshot(int dimensionId, long generatedAtTick, boolean truncated, List<ChunkEntry> chunks) {
        if (generatedAtTick < 0L || chunks == null || chunks.size() > MAX_CHUNKS) {
            throw new IllegalArgumentException("Invalid weak chunk snapshot");
        }
        this.dimensionId = dimensionId;
        this.generatedAtTick = generatedAtTick;
        this.truncated = truncated;
        this.chunks = List.copyOf(chunks);
    }

    @Getter
    public static class ChunkEntry {

        public final int chunkX;
        public final int chunkZ;
        public final int entityCount;
        public final List<EntityTypeCount> entityTypes;

        public ChunkEntry(int chunkX, int chunkZ, int entityCount, List<EntityTypeCount> entityTypes) {
            if (entityCount < 1 || entityTypes == null || entityTypes.size() > MAX_ENTITY_TYPES) {
                throw new IllegalArgumentException("Invalid weak chunk entry");
            }
            long sampledCount = 0L;
            for (EntityTypeCount entityType : entityTypes) {
                if (entityType == null) {
                    throw new IllegalArgumentException("Weak chunk entity types must not contain null entries");
                }
                sampledCount += entityType.getCount();
            }
            if (sampledCount > entityCount) {
                throw new IllegalArgumentException("Weak chunk type counts exceed total entity count");
            }
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.entityCount = entityCount;
            this.entityTypes = List.copyOf(entityTypes);
        }

    }

    @Getter
    public static class EntityTypeCount {

        public final String typeId;
        public final int count;

        public EntityTypeCount(String typeId, int count) {
            if (typeId == null || typeId.length() == 0 || typeId.length() > MAX_TYPE_ID_LENGTH || count < 1) {
                throw new IllegalArgumentException("Invalid weak chunk entity type");
            }
            this.typeId = typeId;
            this.count = count;
        }

    }
}
