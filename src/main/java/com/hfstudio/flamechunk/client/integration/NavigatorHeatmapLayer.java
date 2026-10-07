package com.hfstudio.flamechunk.client.integration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.navigator.api.model.SupportedMods;
import com.gtnewhorizons.navigator.api.model.buttons.ButtonManager;
import com.gtnewhorizons.navigator.api.model.layers.InteractableLayerManager;
import com.gtnewhorizons.navigator.api.model.layers.LayerRenderer;
import com.gtnewhorizons.navigator.api.model.layers.UniversalInteractableRenderer;
import com.gtnewhorizons.navigator.api.model.locations.ILocationProvider;
import com.gtnewhorizons.navigator.api.model.steps.UniversalLocationInteractableStep;
import com.hfstudio.flamechunk.client.config.ClientConfig;
import com.hfstudio.flamechunk.client.render.ColorUtils;
import com.hfstudio.flamechunk.common.integration.Mods;

import cpw.mods.fml.common.Optional;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

public class NavigatorHeatmapLayer extends InteractableLayerManager {

    public static final NavigatorHeatmapLayer INSTANCE = new NavigatorHeatmapLayer();
    public volatile MapOverlayModel model = ClientMapOverlayState.emptyModel();
    public final LongOpenHashSet batchAnchorKeys = new LongOpenHashSet();
    public final LongOpenHashSet visibleCellKeys = new LongOpenHashSet();

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
        batchAnchorKeys.clear();
        visibleCellKeys.clear();
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
        boolean hasAnchor = false;
        for (MapOverlayCell cell : tileCells) {
            int cellMinX = cell.getChunkX() << 4;
            int cellMinZ = cell.getChunkZ() << 4;
            int cellMaxX = cellMinX + 15;
            int cellMaxZ = cellMinZ + 15;
            if (cellMinX > maxBlockX || cellMaxX < minBlockX || cellMinZ > maxBlockZ || cellMaxZ < minBlockZ) {
                continue;
            }
            locations.add(new CellLocation(cell));
            visibleCellKeys.add(MapOverlayModel.key(cell.getChunkX(), cell.getChunkZ()));
            if (!hasAnchor) {
                batchAnchorKeys.add(MapOverlayModel.key(cell.getChunkX(), cell.getChunkZ()));
                hasAnchor = true;
            }
        }
    }

    @Override
    public void updateElement(ILocationProvider location) {
        if (location instanceof CellLocation cellLocation) {
            cellLocation.cell = model
                .find(cellLocation.getDimensionId(), cellLocation.getChunkX(), cellLocation.getChunkZ());
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

    public boolean isBatchAnchor(CellLocation location) {
        return location.cell != null
            && batchAnchorKeys.contains(MapOverlayModel.key(location.getChunkX(), location.getChunkZ()));
    }

    public void drawTile(CellLocation anchor, double centerX, double centerZ) {
        List<MapOverlayCell> tileCells = model.getCellsInTile(
            anchor.getDimensionId(),
            Math.floorDiv(anchor.getChunkX(), MapOverlayModel.TILE_CHUNKS),
            Math.floorDiv(anchor.getChunkZ(), MapOverlayModel.TILE_CHUNKS));
        if (tileCells.isEmpty()) {
            return;
        }

        double blockScale = anchor.stepScale;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        try {
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            for (MapOverlayCell cell : tileCells) {
                if (!visibleCellKeys.contains(MapOverlayModel.key(cell.getChunkX(), cell.getChunkZ()))) {
                    continue;
                }
                double cellCenterX = centerX + ((cell.getChunkX() << 4) + 8 - anchor.getBlockX()) * blockScale;
                double cellCenterZ = centerZ + ((cell.getChunkZ() << 4) + 8 - anchor.getBlockZ()) * blockScale;
                double minX = cellCenterX - 8.0D * blockScale;
                double maxX = cellCenterX + 8.0D * blockScale;
                double minZ = cellCenterZ - 8.0D * blockScale;
                double maxZ = cellCenterZ + 8.0D * blockScale;
                tessellator.setColorRGBA_I(cell.getColor(), ColorUtils.alpha(cell.getOpacity()));
                tessellator.addVertex(minX, minZ, 0.0D);
                tessellator.addVertex(maxX, minZ, 0.0D);
                tessellator.addVertex(maxX, maxZ, 0.0D);
                tessellator.addVertex(minX, maxZ, 0.0D);
                if (ClientConfig.showLoaderSources && cell.getTicketSourceCode() > 0) {
                    XaeroOverlayRenderer.addTicketOutline(tessellator, cell, minX, maxX, minZ, maxZ);
                }
            }
            tessellator.draw();
        } finally {
            GL11.glPopAttrib();
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

        public MapOverlayCell cell;
        public double stepScale = 1.0D;

        public CellLocation(MapOverlayCell cell) {
            this.cell = cell;
        }

        @Override
        public int getDimensionId() {
            return cell.getDimensionId();
        }

        @Override
        public double getBlockX() {
            return (cell.getChunkX() << 4) + 8.0D;
        }

        @Override
        public double getBlockZ() {
            return (cell.getChunkZ() << 4) + 8.0D;
        }

        public MapOverlayCell getCell() {
            return cell;
        }
    }

    public static class CellRenderStep extends UniversalLocationInteractableStep<CellLocation> {

        public final NavigatorHeatmapLayer owner;

        public CellRenderStep(NavigatorHeatmapLayer owner, CellLocation location) {
            super(location);
            this.owner = owner;
            setSize(16.0D);
        }

        @Override
        public void draw(double x, double y, float drawScale, double zoom) {
            if (!owner.isBatchAnchor(location)) {
                return;
            }
            location.stepScale = isJourneyMap ? blockSize : 1.0D;
            owner.drawTile(location, x, y);
        }

        @Override
        public void getTooltip(List<String> tooltip) {
            MapOverlayCell cell = location.getCell();
            if (cell != null) {
                tooltip.addAll(MapOverlayTooltip.lines(cell));
            }
        }

        @Override
        public boolean isMouseOver(int mouseX, int mouseY) {
            double halfWidth = getAdjustedWidth() * 0.5D;
            double halfHeight = getAdjustedHeight() * 0.5D;
            return mouseX >= getX() - halfWidth && mouseX <= getX() + halfWidth
                && mouseY >= getY() - halfHeight
                && mouseY <= getY() + halfHeight;
        }

        @Override
        public void onActionKeyPressed() {}
    }
}
