package com.hfstudio.flamechunk.client.integration;

import java.util.Collections;
import java.util.List;

import com.hfstudio.flamechunk.client.render.ColorUtils;
import com.hfstudio.flamechunk.common.data.ChunkTypeTiming;
import com.hfstudio.flamechunk.common.data.WeakChunkSnapshot.EntityTypeCount;
import com.hfstudio.flamechunk.common.tick.TickCategory;

import lombok.Getter;

@Getter
public class MapOverlayCell {

    public final int dimensionId;
    public final int chunkX;
    public final int chunkZ;
    public final int color;
    public final float opacity;
    public final String label;
    public final long[] nanos;
    public final int[] counts;
    public final long sampledTicks;
    public final int entityCount;
    public final byte loadLevel;
    public final int ticketSourceCode;
    public final String ticketSource;
    @Getter
    public final boolean weakChunk;
    public final List<EntityTypeCount> weakEntityTypes;
    public final List<ChunkTypeTiming> typeTimings;

    public int getTicketSourceColor() {
        return ColorUtils.ticketSourceColor(ticketSourceCode);
    }

    public MapOverlayCell(int dimensionId, int chunkX, int chunkZ, int color, float opacity, String label) {
        this(
            dimensionId,
            chunkX,
            chunkZ,
            color,
            opacity,
            label,
            new long[TickCategory.COUNT],
            new int[TickCategory.COUNT],
            0L,
            0,
            (byte) 0,
            0,
            "",
            false,
            Collections.emptyList());
    }

    public MapOverlayCell(int dimensionId, int chunkX, int chunkZ, int color, float opacity, String label, long[] nanos,
        int[] counts, long sampledTicks, int entityCount, byte loadLevel, int ticketSourceCode, String ticketSource) {
        this(
            dimensionId,
            chunkX,
            chunkZ,
            color,
            opacity,
            label,
            nanos,
            counts,
            sampledTicks,
            entityCount,
            loadLevel,
            ticketSourceCode,
            ticketSource,
            false,
            Collections.emptyList(),
            Collections.emptyList());
    }

    public MapOverlayCell(int dimensionId, int chunkX, int chunkZ, int color, float opacity, String label, long[] nanos,
        int[] counts, long sampledTicks, int entityCount, byte loadLevel, int ticketSourceCode, String ticketSource,
        boolean weakChunk, List<EntityTypeCount> weakEntityTypes) {
        this(
            dimensionId,
            chunkX,
            chunkZ,
            color,
            opacity,
            label,
            nanos,
            counts,
            sampledTicks,
            entityCount,
            loadLevel,
            ticketSourceCode,
            ticketSource,
            weakChunk,
            weakEntityTypes,
            Collections.emptyList());
    }

    public MapOverlayCell(int dimensionId, int chunkX, int chunkZ, int color, float opacity, String label, long[] nanos,
        int[] counts, long sampledTicks, int entityCount, byte loadLevel, int ticketSourceCode, String ticketSource,
        boolean weakChunk, List<EntityTypeCount> weakEntityTypes, List<ChunkTypeTiming> typeTimings) {
        if (nanos == null || counts == null
            || nanos.length != TickCategory.COUNT
            || counts.length != TickCategory.COUNT
            || sampledTicks < 0L
            || entityCount < 0
            || ticketSourceCode < 0
            || ticketSource == null
            || ticketSource.length() > 64
            || weakEntityTypes == null
            || typeTimings == null) {
            throw new IllegalArgumentException("Invalid overlay cell metadata");
        }
        this.dimensionId = dimensionId;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.color = color;
        this.opacity = opacity;
        this.label = label;
        this.nanos = nanos.clone();
        this.counts = counts.clone();
        this.sampledTicks = sampledTicks;
        this.entityCount = entityCount;
        this.loadLevel = loadLevel;
        this.ticketSourceCode = ticketSourceCode;
        this.ticketSource = ticketSource;
        this.weakChunk = weakChunk;
        this.weakEntityTypes = List.copyOf(weakEntityTypes);
        this.typeTimings = List.copyOf(typeTimings);
    }

    public long[] getNanos() {
        return nanos.clone();
    }

    public int[] getCounts() {
        return counts.clone();
    }

    public MapOverlayCell withWeakChunk(int weakColor, float weakOpacity, String weakLabel, int weakEntityCount,
        List<EntityTypeCount> entityTypes) {
        return new MapOverlayCell(
            dimensionId,
            chunkX,
            chunkZ,
            weakColor,
            weakOpacity,
            weakLabel,
            nanos,
            counts,
            sampledTicks,
            Math.max(entityCount, weakEntityCount),
            loadLevel,
            ticketSourceCode,
            ticketSource,
            true,
            entityTypes,
            typeTimings);
    }

}
