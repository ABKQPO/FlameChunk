package com.hfstudio.flamechunk.common.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.hfstudio.flamechunk.common.tick.TickCategory;

public class ChunkTiming {

    private final long[] nanos = new long[TickCategory.COUNT];
    private final int[] counts = new int[TickCategory.COUNT];
    public final Map<TickCategory, Map<String, TypeAggregate>> typeTimings = new EnumMap<>(TickCategory.class);

    public void add(TickCategory category, long elapsedNanos) {
        if (elapsedNanos < 0L) {
            throw new IllegalArgumentException("Elapsed time cannot be negative");
        }
        int index = category.ordinal();
        nanos[index] = saturatingAdd(nanos[index], elapsedNanos);
        if (counts[index] < Integer.MAX_VALUE) {
            counts[index]++;
        }
    }

    public boolean addObjectTiming(TickCategory category, String typeName, long elapsedNanos, boolean allowNewType) {
        add(category, elapsedNanos);
        if (!category.supportsTypeTiming() || typeName == null || typeName.length() == 0) {
            return false;
        }
        if (typeName.length() > ChunkTypeTiming.MAX_TYPE_NAME_LENGTH) {
            typeName = typeName.substring(0, ChunkTypeTiming.MAX_TYPE_NAME_LENGTH);
        }
        Map<String, TypeAggregate> types = typeMap(category);
        if (types == null) {
            if (!allowNewType) {
                return false;
            }
            types = new HashMap<>();
            typeTimings.put(category, types);
        }
        TypeAggregate aggregate = types.get(typeName);
        boolean created = false;
        if (aggregate == null) {
            if (!allowNewType || types.size() >= 16) {
                return false;
            }
            aggregate = new TypeAggregate();
            types.put(typeName, aggregate);
            created = true;
        }
        aggregate.add(elapsedNanos);
        return created;
    }

    public List<ChunkTypeTiming> topObjectTimings(TickCategory category, int limit) {
        Map<String, TypeAggregate> types = typeMap(category);
        if (types == null || types.isEmpty() || limit < 1) {
            return Collections.emptyList();
        }
        List<Map.Entry<String, TypeAggregate>> entries = new ArrayList<>(types.entrySet());
        entries.sort((left, right) -> {
            int costOrder = Long.compare(right.getValue().nanos, left.getValue().nanos);
            return costOrder != 0 ? costOrder
                : left.getKey()
                    .compareTo(right.getKey());
        });
        List<ChunkTypeTiming> result = new ArrayList<>(Math.min(limit, entries.size()));
        for (int index = 0; index < entries.size() && index < limit; index++) {
            Map.Entry<String, TypeAggregate> entry = entries.get(index);
            String typeName = entry.getKey();
            if (typeName.length() > ChunkTypeTiming.MAX_TYPE_NAME_LENGTH) {
                typeName = typeName.substring(0, ChunkTypeTiming.MAX_TYPE_NAME_LENGTH);
            }
            result.add(
                new ChunkTypeTiming(
                    category,
                    typeName,
                    entry.getValue().nanos,
                    entry.getValue().count,
                    entry.getValue().peakNanos));
        }
        return result;
    }

    private Map<String, TypeAggregate> typeMap(TickCategory category) {
        return typeTimings.get(category);
    }

    public long getNanos(TickCategory category) {
        return nanos[category.ordinal()];
    }

    public int getCount(TickCategory category) {
        return counts[category.ordinal()];
    }

    public long[] copyNanos() {
        return nanos.clone();
    }

    public int[] copyCounts() {
        return counts.clone();
    }

    public long totalNanos() {
        long total = 0L;
        for (int index = 0; index < nanos.length; index++) {
            if (index != TickCategory.BLOCK_UPDATE.ordinal()) {
                total = saturatingAdd(total, nanos[index]);
            }
        }
        return total;
    }

    public ChunkTiming copy() {
        ChunkTiming copy = new ChunkTiming();
        System.arraycopy(nanos, 0, copy.nanos, 0, nanos.length);
        System.arraycopy(counts, 0, copy.counts, 0, counts.length);
        return copy;
    }

    private static long saturatingAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    public static class TypeAggregate {

        public long nanos;
        public int count;
        public long peakNanos;

        public void add(long elapsedNanos) {
            nanos = saturatingAdd(nanos, elapsedNanos);
            peakNanos = Math.max(peakNanos, elapsedNanos);
            if (count < Integer.MAX_VALUE) {
                count++;
            }
        }
    }
}
