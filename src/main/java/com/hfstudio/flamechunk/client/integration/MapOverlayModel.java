package com.hfstudio.flamechunk.client.integration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import net.minecraft.util.StatCollector;

import com.hfstudio.flamechunk.client.config.ClientConfig;
import com.hfstudio.flamechunk.client.render.ColorCalculator;
import com.hfstudio.flamechunk.client.render.ColorUtils;
import com.hfstudio.flamechunk.common.data.ChunkSnapshot;
import com.hfstudio.flamechunk.common.data.DimensionSnapshot;
import com.hfstudio.flamechunk.common.data.ScanSnapshot;
import com.hfstudio.flamechunk.common.data.WeakChunkSnapshot;
import com.hfstudio.flamechunk.common.data.WeakChunkSnapshot.ChunkEntry;
import com.hfstudio.flamechunk.common.tick.TickCategory;

import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import lombok.AccessLevel;
import lombok.Getter;

public class MapOverlayModel {

    public static final int TILE_CHUNKS = 16;
    public static final int TILE_BLOCKS = TILE_CHUNKS << 4;
    public static final ColorCalculator COLOR_CALCULATOR = new ColorCalculator();

    @Getter
    public final List<MapOverlayCell> cells;
    public final long displaySignature;
    @Getter(AccessLevel.NONE)
    public final Int2ObjectOpenHashMap<Long2ObjectOpenHashMap<MapOverlayCell>> cellsByPosition;
    public final Int2ObjectOpenHashMap<List<MapOverlayCell>> cellsByDimension;
    public final Int2ObjectOpenHashMap<Long2ObjectOpenHashMap<List<MapOverlayCell>>> cellsByTile;

    public MapOverlayModel(List<MapOverlayCell> cells) {
        if (cells == null) {
            throw new IllegalArgumentException("Overlay cells must not be null");
        }
        this.cells = List.copyOf(cells);
        this.displaySignature = MapOverlayTooltip.settingSignature() | (ClientConfig.showLoaderSources ? 1L << 63 : 0L);
        this.cellsByDimension = new Int2ObjectOpenHashMap<>();
        this.cellsByTile = new Int2ObjectOpenHashMap<>();
        Int2ObjectOpenHashMap<Long2ObjectOpenHashMap<MapOverlayCell>> index = new Int2ObjectOpenHashMap<>();
        for (MapOverlayCell cell : this.cells) {
            Long2ObjectOpenHashMap<MapOverlayCell> dimensionIndex = index.get(cell.getDimensionId());
            if (dimensionIndex == null) {
                dimensionIndex = new Long2ObjectOpenHashMap<>();
                index.put(cell.getDimensionId(), dimensionIndex);
            }
            dimensionIndex.put(key(cell.getChunkX(), cell.getChunkZ()), cell);

            Long2ObjectOpenHashMap<List<MapOverlayCell>> tileIndex = this.cellsByTile.get(cell.getDimensionId());
            if (tileIndex == null) {
                tileIndex = new Long2ObjectOpenHashMap<>();
                this.cellsByTile.put(cell.getDimensionId(), tileIndex);
            }
            int tileX = Math.floorDiv(cell.getChunkX(), TILE_CHUNKS);
            int tileZ = Math.floorDiv(cell.getChunkZ(), TILE_CHUNKS);
            long tileKey = key(tileX, tileZ);
            List<MapOverlayCell> tileCells = tileIndex.get(tileKey);
            if (tileCells == null) {
                tileCells = new ArrayList<>();
                tileIndex.put(tileKey, tileCells);
            }
            tileCells.add(cell);

            List<MapOverlayCell> dimensionCells = this.cellsByDimension.get(cell.getDimensionId());
            if (dimensionCells == null) {
                dimensionCells = new ArrayList<>();
                this.cellsByDimension.put(cell.getDimensionId(), dimensionCells);
            }
            dimensionCells.add(cell);
        }
        this.cellsByPosition = index;
        for (var entry : this.cellsByDimension.int2ObjectEntrySet()) {
            entry.setValue(List.copyOf(entry.getValue()));
        }
        for (var dimensionEntry : this.cellsByTile.int2ObjectEntrySet()) {
            for (var tileEntry : dimensionEntry.getValue()
                .long2ObjectEntrySet()) {
                tileEntry.setValue(List.copyOf(tileEntry.getValue()));
            }
        }
    }

    public static MapOverlayModel from(ScanSnapshot snapshot) {
        return from(snapshot, Collections.emptyList());
    }

    public static MapOverlayModel from(ScanSnapshot snapshot, List<WeakChunkSnapshot> weakSnapshots) {
        if (weakSnapshots == null) {
            throw new IllegalArgumentException("Weak chunk snapshots must not be null");
        }
        List<MapOverlayCell> cells = new ArrayList<>();
        Int2ObjectOpenHashMap<Long2IntOpenHashMap> positions = new Int2ObjectOpenHashMap<>();
        boolean relativeHeatColor = ClientConfig.relativeHeatColor;
        Int2FloatOpenHashMap colorBudgets = relativeHeatColor && snapshot != null ? new Int2FloatOpenHashMap() : null;
        long sampledTicks = snapshot == null ? 1L : Math.max(1L, snapshot.getSampledTicks());
        if (colorBudgets != null) {
            for (DimensionSnapshot dimension : snapshot.getDimensions()) {
                float colorBudget = 0.0F;
                for (ChunkSnapshot chunk : dimension.getChunks()) {
                    colorBudget = Math.max(colorBudget, chunk.calculateMspt(sampledTicks));
                }
                colorBudgets.put(
                    dimension.getDimensionId(),
                    colorBudget <= 0.0F ? ClientConfig.heatThresholdMspt : colorBudget);
            }
        }
        if (snapshot != null) {
            for (DimensionSnapshot dimension : snapshot.getDimensions()) {
                float colorBudget = relativeHeatColor ? colorBudgets.get(dimension.getDimensionId())
                    : ClientConfig.heatThresholdMspt;
                for (ChunkSnapshot chunk : dimension.getChunks()) {
                    float mspt = chunk.calculateMspt(sampledTicks);
                    boolean weakIdle = chunk.isWeakLoaded() && !chunk.isTimed();
                    if (weakIdle && !ClientConfig.showWeakIdleChunks) {
                        continue;
                    }
                    int color = weakIdle ? ColorUtils.rgb(ColorUtils.WEAK_IDLE)
                        : COLOR_CALCULATOR.colorForMspt(mspt, colorBudget);
                    float opacity = weakIdle ? ColorUtils.WEAK_IDLE_OPACITY
                        : ColorUtils.heatOpacity(ClientConfig.heatAlpha, mspt, colorBudget);
                    addCell(
                        cells,
                        positions,
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
                            chunk.getTicketSource(),
                            false,
                            Collections.emptyList(),
                            chunk.getTypeTimings()));
                }
            }
        }
        for (WeakChunkSnapshot weakSnapshot : weakSnapshots) {
            if (weakSnapshot == null) {
                continue;
            }
            for (ChunkEntry chunk : weakSnapshot.getChunks()) {
                MapOverlayCell weakCell = new MapOverlayCell(
                    weakSnapshot.getDimensionId(),
                    chunk.getChunkX(),
                    chunk.getChunkZ(),
                    ColorUtils.weakChunkColor(chunk.getEntityCount()),
                    ClientConfig.heatAlpha,
                    StatCollector.translateToLocalFormatted("flamechunk.overlay.weak", chunk.getEntityCount()),
                    new long[TickCategory.COUNT],
                    new int[TickCategory.COUNT],
                    0L,
                    chunk.getEntityCount(),
                    (byte) 32,
                    0,
                    "",
                    true,
                    chunk.getEntityTypes(),
                    Collections.emptyList());
                Long2IntOpenHashMap dimensionPositions = positions.get(weakSnapshot.getDimensionId());
                int cellIndex = dimensionPositions == null ? -1
                    : dimensionPositions.get(key(chunk.getChunkX(), chunk.getChunkZ()));
                if (cellIndex < 0) {
                    addCell(cells, positions, weakCell);
                } else {
                    MapOverlayCell previous = cells.get(cellIndex);
                    cells.set(
                        cellIndex,
                        previous.withWeakChunk(
                            weakCell.getColor(),
                            weakCell.getOpacity(),
                            weakCell.getLabel(),
                            weakCell.getEntityCount(),
                            weakCell.getWeakEntityTypes()));
                }
            }
        }
        return new MapOverlayModel(cells);
    }

    public static void addCell(List<MapOverlayCell> cells, Int2ObjectOpenHashMap<Long2IntOpenHashMap> positions,
        MapOverlayCell cell) {
        Long2IntOpenHashMap dimensionPositions = positions.get(cell.getDimensionId());
        if (dimensionPositions == null) {
            dimensionPositions = new Long2IntOpenHashMap();
            dimensionPositions.defaultReturnValue(-1);
            positions.put(cell.getDimensionId(), dimensionPositions);
        }
        long key = key(cell.getChunkX(), cell.getChunkZ());
        int index = dimensionPositions.get(key);
        if (index < 0) {
            dimensionPositions.put(key, cells.size());
            cells.add(cell);
        } else {
            cells.set(index, cell);
        }
    }

    public MapOverlayCell find(int dimensionId, int chunkX, int chunkZ) {
        Long2ObjectOpenHashMap<MapOverlayCell> dimensionIndex = cellsByPosition.get(dimensionId);
        return dimensionIndex == null ? null : dimensionIndex.get(key(chunkX, chunkZ));
    }

    public List<MapOverlayCell> getCells(int dimensionId) {
        List<MapOverlayCell> dimensionCells = cellsByDimension.get(dimensionId);
        return dimensionCells == null ? Collections.emptyList() : dimensionCells;
    }

    public List<MapOverlayCell> getCellsInTile(int dimensionId, int tileX, int tileZ) {
        Long2ObjectOpenHashMap<List<MapOverlayCell>> tileIndex = cellsByTile.get(dimensionId);
        if (tileIndex == null) {
            return Collections.emptyList();
        }
        List<MapOverlayCell> tileCells = tileIndex.get(key(tileX, tileZ));
        return tileCells == null ? Collections.emptyList() : tileCells;
    }

    public boolean hasSameContent(MapOverlayModel other) {
        if (other == null || displaySignature != other.displaySignature || cells.size() != other.cells.size()) {
            return false;
        }
        for (MapOverlayCell cell : cells) {
            MapOverlayCell otherCell = other.find(cell.getDimensionId(), cell.getChunkX(), cell.getChunkZ());
            if (!cell.hasSameContent(otherCell)) {
                return false;
            }
        }
        return true;
    }

    public static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

}
