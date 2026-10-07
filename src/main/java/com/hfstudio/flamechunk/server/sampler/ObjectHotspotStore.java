package com.hfstudio.flamechunk.server.sampler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import com.hfstudio.flamechunk.common.data.ChunkTypeTiming;
import com.hfstudio.flamechunk.common.data.ObjectHotspot;
import com.hfstudio.flamechunk.common.tick.TickCategory;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public class ObjectHotspotStore {

    public static final int MAX_TRACKED_OBJECTS = 4096;
    public static final int REPORT_OBJECT_LIMIT = 128;
    public final Int2ObjectOpenHashMap<Map<TickCategory, Long2ObjectOpenHashMap<Aggregate>>> dimensions = new Int2ObjectOpenHashMap<>();
    public int size;

    public void record(int dimensionId, TickCategory category, String typeName, int entityId, long identityMost,
        long identityLeast, int x, int y, int z, long elapsedNanos) {
        if (elapsedNanos < 0L || category == null
            || !category.supportsTypeTiming()
            || category == TickCategory.HANDLER
            || typeName == null
            || typeName.isEmpty()
            || x < -30000000
            || x > 30000000
            || z < -30000000
            || z > 30000000
            || category != TickCategory.ENTITY && (y < 0 || y > 4095)) {
            return;
        }
        if (typeName.length() > ChunkTypeTiming.MAX_TYPE_NAME_LENGTH) {
            typeName = typeName.substring(0, ChunkTypeTiming.MAX_TYPE_NAME_LENGTH);
        }
        Map<TickCategory, Long2ObjectOpenHashMap<Aggregate>> categories = dimensions.get(dimensionId);
        if (categories == null) {
            if (size >= MAX_TRACKED_OBJECTS) {
                return;
            }
            categories = new EnumMap<>(TickCategory.class);
            dimensions.put(dimensionId, categories);
        }
        Long2ObjectOpenHashMap<Aggregate> objects = categories.get(category);
        if (objects == null) {
            if (size >= MAX_TRACKED_OBJECTS) {
                return;
            }
            objects = new Long2ObjectOpenHashMap<>();
            categories.put(category, objects);
        }
        long key = category == TickCategory.ENTITY ? entityId : ObjectHotspot.blockKey(x, y, z);
        Aggregate aggregate = objects.get(key);
        if (aggregate == null) {
            if (size >= MAX_TRACKED_OBJECTS) {
                return;
            }
            aggregate = new Aggregate();
            objects.put(key, aggregate);
            size++;
        } else if (aggregate.identityMost != identityMost || aggregate.identityLeast != identityLeast
            || !aggregate.typeName.equals(typeName)) {
                aggregate.nanos = 0L;
                aggregate.peakNanos = 0L;
                aggregate.count = 0;
            }
        aggregate.typeName = typeName;
        aggregate.category = category;
        aggregate.entityId = entityId;
        aggregate.identityMost = identityMost;
        aggregate.identityLeast = identityLeast;
        aggregate.x = x;
        aggregate.y = y;
        aggregate.z = z;
        aggregate.nanos = Long.MAX_VALUE - aggregate.nanos < elapsedNanos ? Long.MAX_VALUE
            : aggregate.nanos + elapsedNanos;
        aggregate.peakNanos = Math.max(aggregate.peakNanos, elapsedNanos);
        if (aggregate.count < Integer.MAX_VALUE) {
            aggregate.count++;
        }
    }

    public List<ObjectHotspot> snapshot(int dimensionId) {
        Map<TickCategory, Long2ObjectOpenHashMap<Aggregate>> categories = dimensions.get(dimensionId);
        if (categories == null) {
            return Collections.emptyList();
        }
        List<Aggregate> ranked = new ArrayList<>();
        for (Long2ObjectOpenHashMap<Aggregate> objects : categories.values()) {
            ranked.addAll(objects.values());
        }
        ranked.sort(
            Comparator.comparingLong((Aggregate value) -> value.nanos)
                .reversed()
                .thenComparingInt(value -> value.category.ordinal())
                .thenComparingInt(value -> value.entityId)
                .thenComparingInt(value -> value.x)
                .thenComparingInt(value -> value.y)
                .thenComparingInt(value -> value.z));
        int limit = Math.min(ranked.size(), REPORT_OBJECT_LIMIT);
        List<ObjectHotspot> result = new ArrayList<>(limit);
        for (int index = 0; index < limit; index++) {
            Aggregate value = ranked.get(index);
            result.add(
                new ObjectHotspot(
                    value.category,
                    value.typeName,
                    value.entityId,
                    value.identityMost,
                    value.identityLeast,
                    value.x,
                    value.y,
                    value.z,
                    value.nanos,
                    value.peakNanos,
                    value.count));
        }
        return result;
    }

    public void clear() {
        dimensions.clear();
        size = 0;
    }

    public List<ObjectHotspot> snapshotNear(int dimensionId, double x, double y, double z, int radius, int limit) {
        Map<TickCategory, Long2ObjectOpenHashMap<Aggregate>> categories = dimensions.get(dimensionId);
        if (categories == null || limit < 1 || radius < 1) {
            return Collections.emptyList();
        }
        int boundedLimit = Math.min(limit, ObjectHotspot.MAX_PER_DIMENSION);
        double radiusSquared = (double) radius * radius;
        Comparator<Aggregate> bestFirst = Comparator.comparingLong((Aggregate value) -> value.nanos)
            .reversed()
            .thenComparingDouble(value -> distanceSquared(value, x, y, z))
            .thenComparingInt(value -> value.entityId)
            .thenComparingInt(value -> value.x)
            .thenComparingInt(value -> value.y)
            .thenComparingInt(value -> value.z);
        PriorityQueue<Aggregate> selected = new PriorityQueue<>(boundedLimit, bestFirst.reversed());
        for (TickCategory category : List.of(TickCategory.ENTITY, TickCategory.BLOCK_ENTITY)) {
            Long2ObjectOpenHashMap<Aggregate> objects = categories.get(category);
            if (objects == null) {
                continue;
            }
            for (Aggregate value : objects.values()) {
                if (distanceSquared(value, x, y, z) > radiusSquared) {
                    continue;
                }
                if (selected.size() < boundedLimit) {
                    selected.add(value);
                } else if (bestFirst.compare(value, selected.peek()) < 0) {
                    selected.poll();
                    selected.add(value);
                }
            }
        }
        List<Aggregate> ranked = new ArrayList<>(selected);
        ranked.sort(bestFirst);
        List<ObjectHotspot> result = new ArrayList<>(ranked.size());
        for (Aggregate value : ranked) {
            result.add(new ObjectHotspot(value.category, value.typeName, value.entityId, value.identityMost,
                value.identityLeast, value.x, value.y, value.z, value.nanos, value.peakNanos, value.count));
        }
        return result;
    }

    public static double distanceSquared(Aggregate value, double x, double y, double z) {
        double dx = value.x + 0.5D - x;
        double dy = value.y + 0.5D - y;
        double dz = value.z + 0.5D - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public static class Aggregate {

        public TickCategory category;
        public String typeName;
        public int entityId;
        public long identityMost;
        public long identityLeast;
        public int x;
        public int y;
        public int z;
        public long nanos;
        public long peakNanos;
        public int count;
    }
}
