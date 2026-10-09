package com.hfstudio.flamechunk.client.integration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import com.gtnewhorizons.navigator.api.model.SupportedMods;
import com.gtnewhorizons.navigator.api.model.buttons.ButtonManager;
import com.gtnewhorizons.navigator.api.model.layers.InteractableLayerManager;
import com.gtnewhorizons.navigator.api.model.layers.LayerRenderer;
import com.gtnewhorizons.navigator.api.model.layers.UniversalInteractableRenderer;
import com.gtnewhorizons.navigator.api.model.locations.ILocationProvider;
import com.gtnewhorizons.navigator.api.model.steps.UniversalLocationInteractableStep;
import com.gtnewhorizons.navigator.api.util.DrawUtils;
import com.hfstudio.flamechunk.client.config.ClientConfig;
import com.hfstudio.flamechunk.client.render.ChunkLabelRenderer;
import com.hfstudio.flamechunk.client.render.ColorUtils;
import com.hfstudio.flamechunk.common.integration.Mods;

import cpw.mods.fml.common.Optional;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public class NavigatorHeatmapLayer extends InteractableLayerManager {

    public static final NavigatorHeatmapLayer INSTANCE = new NavigatorHeatmapLayer();
    public volatile MapOverlayModel model = ClientMapOverlayState.emptyModel();

    public NavigatorHeatmapLayer() {
        super(new HeatmapButton());
    }

    @Override
    public LayerRenderer addLayerRenderer(InteractableLayerManager manager, SupportedMods mod) {
        if (mod == SupportedMods.XaeroMiniMap) {
            return null;
        }
        UniversalInteractableRenderer renderer = new UniversalInteractableRenderer(manager);
        renderer.withRenderStep(location -> new CellRenderStep(this, (CellLocation) location));
        if (Mods.JourneyMap6.isModLoaded()) {
            configureJourneyMap6Overlays(renderer);
        }
        return renderer;
    }

    @Optional.Method(modid = "journeymap_api")
    public void configureJourneyMap6Overlays(UniversalInteractableRenderer renderer) {
        renderer.withJourneyMapV6Overlays(NavigatorJourneyMap6Bridge::createOverlays, true);
    }

    @Override
    public Collection<? extends ILocationProvider> generateVisibleLocations(int minBlockX, int minBlockZ, int maxBlockX,
        int maxBlockZ, int dimension) {
        MapOverlayControls.requestWeakSnapshot();
        List<CellLocation> locations = new ArrayList<>();
        Long2ObjectOpenHashMap<List<MapOverlayCell>> tiles = model.cellsByTile.get(dimension);
        if (tiles == null) {
            return locations;
        }
        int minTileX = Math.floorDiv(minBlockX, MapOverlayModel.TILE_BLOCKS);
        int minTileZ = Math.floorDiv(minBlockZ, MapOverlayModel.TILE_BLOCKS);
        int maxTileX = Math.floorDiv(maxBlockX, MapOverlayModel.TILE_BLOCKS);
        int maxTileZ = Math.floorDiv(maxBlockZ, MapOverlayModel.TILE_BLOCKS);
        long tileWidth = (long) maxTileX - minTileX + 1L;
        long tileHeight = (long) maxTileZ - minTileZ + 1L;
        if (tileWidth > 0L && tileHeight > 0L && tileWidth * tileHeight <= tiles.size()) {
            for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
                for (int tileZ = minTileZ; tileZ <= maxTileZ; tileZ++) {
                    List<MapOverlayCell> tileCells = tiles.get(MapOverlayModel.key(tileX, tileZ));
                    if (tileCells != null) {
                        addVisibleTile(locations, tileCells, minBlockX, minBlockZ, maxBlockX, maxBlockZ);
                    }
                }
            }
        } else {
            for (Long2ObjectMap.Entry<List<MapOverlayCell>> tileEntry : tiles.long2ObjectEntrySet()) {
                int tileX = (int) (tileEntry.getLongKey() >> 32);
                int tileZ = (int) tileEntry.getLongKey();
                if (tileX >= minTileX && tileX <= maxTileX && tileZ >= minTileZ && tileZ <= maxTileZ) {
                    addVisibleTile(locations, tileEntry.getValue(), minBlockX, minBlockZ, maxBlockX, maxBlockZ);
                }
            }
        }
        return locations;
    }

    private void addVisibleTile(List<CellLocation> locations, List<MapOverlayCell> tileCells, int minBlockX,
        int minBlockZ, int maxBlockX, int maxBlockZ) {
        for (MapOverlayCell cell : tileCells) {
            int cellMinX = cell.getChunkX() << 4;
            int cellMinZ = cell.getChunkZ() << 4;
            int cellMaxX = cellMinX + 15;
            int cellMaxZ = cellMinZ + 15;
            if (cellMinX > maxBlockX || cellMaxX < minBlockX || cellMinZ > maxBlockZ || cellMaxZ < minBlockZ) {
                continue;
            }
            locations.add(new CellLocation(this, cell));
        }
    }

    public void publish(MapOverlayModel nextModel) {
        MapOverlayModel publishedModel = nextModel == null ? ClientMapOverlayState.emptyModel() : nextModel;
        if (model.hasSameContent(publishedModel)) {
            model = publishedModel;
            return;
        }
        MapOverlayModel previousModel = model;
        List<MapOverlayCell> previousCells = previousModel.getCells();
        int removedCells = 0;
        for (MapOverlayCell cell : previousCells) {
            if (publishedModel.find(cell.getDimensionId(), cell.getChunkX(), cell.getChunkZ()) == null) {
                removedCells++;
            }
        }
        model = publishedModel;
        if (removedCells == 0) {
            forceRefresh();
            return;
        }
        if (removedCells > previousCells.size() / 2) {
            clearFullCache();
            return;
        }
        for (MapOverlayCell cell : previousCells) {
            if (publishedModel.find(cell.getDimensionId(), cell.getChunkX(), cell.getChunkZ()) == null) {
                invalidateLocation(cell.getDimensionId(), cell.getChunkX(), cell.getChunkZ());
            }
        }
    }

    public static class HeatmapButton extends ButtonManager {

        public HeatmapButton() {
            isActive = true;
        }

        @Override
        public ResourceLocation getIcon(SupportedMods mod, String theme) {
            return new ResourceLocation("navigator", "textures/icon/nodes.png");
        }

        @Override
        public String getButtonText() {
            return StatCollector.translateToLocal("flamechunk.client.map.layer");
        }
    }

    public static class CellLocation implements ILocationProvider {

        public final NavigatorHeatmapLayer owner;
        public final int dimensionId;
        public final int chunkX;
        public final int chunkZ;

        public CellLocation(NavigatorHeatmapLayer owner, MapOverlayCell cell) {
            this.owner = owner;
            this.dimensionId = cell.getDimensionId();
            this.chunkX = cell.getChunkX();
            this.chunkZ = cell.getChunkZ();
        }

        @Override
        public int getDimensionId() {
            return dimensionId;
        }

        @Override
        public double getBlockX() {
            return (chunkX << 4) + 0.5D;
        }

        @Override
        public double getBlockZ() {
            return (chunkZ << 4) + 0.5D;
        }

        @Override
        public int getChunkX() {
            return chunkX;
        }

        @Override
        public int getChunkZ() {
            return chunkZ;
        }

        public MapOverlayCell getCell() {
            return owner.model.find(dimensionId, chunkX, chunkZ);
        }
    }

    public static class CellRenderStep extends UniversalLocationInteractableStep<CellLocation> {

        public final NavigatorHeatmapLayer owner;

        public CellRenderStep(NavigatorHeatmapLayer owner, CellLocation location) {
            super(location);
            this.owner = owner;
        }

        @Override
        public void preRender(double topX, double topY, float drawScale, double zoom) {
            setOffset(isJourneyMap ? -0.5D * blockSize : 0.0D);
        }

        @Override
        public void draw(double topX, double topY, float drawScale, double zoom) {
            MapOverlayCell cell = location.getCell();
            if (cell == null) {
                return;
            }
            double cellWidth = getAdjustedWidth();
            double cellHeight = getAdjustedHeight();
            DrawUtils.drawRect(topX, topY, cellWidth, cellHeight, cell.getColor(), ColorUtils.alpha(cell.getOpacity()));
            if (ClientConfig.showLoaderSources && cell.getTicketSourceCode() > 0) {
                DrawUtils.drawHollowRect(
                    topX,
                    topY,
                    cellWidth,
                    cellHeight,
                    cell.getTicketSourceColor(),
                    ColorUtils.alpha(Math.max(ColorUtils.TICKET_MINIMUM_OPACITY, cell.getOpacity())),
                    Math.min(1.25D, Math.min(cellWidth, cellHeight) * 0.16D));
            }
            if (!isMinimap()) {
                ChunkLabelRenderer.draw(
                    cell.getLabel(),
                    topX + cellWidth * 0.5D,
                    topY + cellHeight * 0.5D,
                    cellWidth,
                    isXaero ? zoom : 1.0D);
            }
        }

        @Override
        public void getTooltip(List<String> tooltip) {
            // FlameChunk draws a vanilla hovering-text tooltip instead so every map integration looks identical.
        }

        @Override
        public void drawCustomTooltip(FontRenderer fontRenderer, int mouseX, int mouseY, int displayWidth,
            int displayHeight) {
            MapOverlayTooltip.draw(
                mouseX,
                mouseY,
                location.getCell(),
                location.getChunkX(),
                location.getChunkZ(),
                displayWidth,
                displayHeight);
        }

        @Override
        public void onActionKeyPressed() {}
    }
}
