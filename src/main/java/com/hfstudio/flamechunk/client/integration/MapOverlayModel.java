package com.hfstudio.flamechunk.client.integration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.hfstudio.flamechunk.client.render.ColorCalculator;
import com.hfstudio.flamechunk.common.data.ChunkSnapshot;
import com.hfstudio.flamechunk.common.data.DimensionSnapshot;
import com.hfstudio.flamechunk.common.data.ScanSnapshot;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import lombok.AccessLevel;
import lombok.Getter;

public class MapOverlayModel {

    private static final float DEFAULT_BUDGET_MSPT = 50.0F;
    private static final ColorCalculator COLOR_CALCULATOR = new ColorCalculator();

    @Getter
    private final List<MapOverlayCell> cells;
    @Getter(AccessLevel.NONE)
    private final Int2ObjectOpenHashMap<Long2ObjectOpenHashMap<MapOverlayCell>> cellsByPosition;

    public MapOverlayModel(List<MapOverlayCell> cells) {
        if (cells == null) {
            throw new IllegalArgumentException("Overlay cells must not be null");
        }
        this.cells = List.copyOf(cells);
        Int2ObjectOpenHashMap<Long2ObjectOpenHashMap<MapOverlayCell>> index = new Int2ObjectOpenHashMap<>();
        for (MapOverlayCell cell : this.cells) {
            Long2ObjectOpenHashMap<MapOverlayCell> dimensionIndex = index.get(cell.getDimensionId());
            if (dimensionIndex == null) {
                dimensionIndex = new Long2ObjectOpenHashMap<>();
                index.put(cell.getDimensionId(), dimensionIndex);
            }
            dimensionIndex.put(key(cell.getChunkX(), cell.getChunkZ()), cell);
        }
        this.cellsByPosition = index;
    }

    public static MapOverlayModel from(ScanSnapshot snapshot) {
        if (snapshot == null) {
            return new MapOverlayModel(Collections.emptyList());
        }
        List<MapOverlayCell> cells = new ArrayList<>();
        long sampledTicks = Math.max(1L, snapshot.getSampledTicks());
        for (DimensionSnapshot dimension : snapshot.getDimensions()) {
            for (ChunkSnapshot chunk : dimension.getChunks()) {
                float mspt = chunk.calculateMspt(sampledTicks);
                int color = COLOR_CALCULATOR.colorForMspt(mspt, DEFAULT_BUDGET_MSPT);
                float opacity = 0.25F + 0.65F * COLOR_CALCULATOR.normalize(mspt, DEFAULT_BUDGET_MSPT);
                cells.add(
                    new MapOverlayCell(
                        dimension.getDimensionId(),
                        chunk.getChunkX(),
                        chunk.getChunkZ(),
                        color,
                        opacity,
                        String.format(Locale.ENGLISH, "%.3f ms/t", mspt),
                        chunk.getNanos(),
                        chunk.getCounts(),
                        sampledTicks,
                        chunk.getEntityCount(),
                        chunk.getLoadLevel(),
                        chunk.getTicketSourceCode(),
                        chunk.getTicketSource()));
            }
        }
        return new MapOverlayModel(cells);
    }

    public MapOverlayCell find(int dimensionId, int chunkX, int chunkZ) {
        Long2ObjectOpenHashMap<MapOverlayCell> dimensionIndex = cellsByPosition.get(dimensionId);
        return dimensionIndex == null ? null : dimensionIndex.get(key(chunkX, chunkZ));
    }

    private static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

}
