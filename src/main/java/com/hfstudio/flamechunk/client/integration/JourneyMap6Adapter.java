package com.hfstudio.flamechunk.client.integration;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;

import org.jetbrains.annotations.NotNull;

import com.hfstudio.flamechunk.FlameChunk;
import com.hfstudio.flamechunk.api.client.map.MapOverlayApi;
import com.hfstudio.flamechunk.client.config.ClientConfig;
import com.hfstudio.flamechunk.client.render.ColorUtils;
import com.hfstudio.flamechunk.common.data.ChunkTypeTiming;
import com.hfstudio.flamechunk.common.data.WeakChunkSnapshot.EntityTypeCount;
import com.hfstudio.flamechunk.common.tick.TickCategory;

import cpw.mods.fml.common.Optional;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.display.DisplayType;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.event.PopupMenuEvent.FullscreenPopupMenuEvent;
import journeymap.api.v2.client.fullscreen.ModPopupMenu;
import journeymap.api.v2.client.model.MapPolygon;
import journeymap.api.v2.client.model.ShapeProperties;
import journeymap.api.v2.client.model.TextProperties;
import journeymap.api.v2.common.Context;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.util.BlockPos;

@JourneyMapPlugin(apiVersion = "2.0.0", require = false)
@Optional.Interface(iface = "journeymap.api.v2.client.IClientPlugin", modid = "journeymap_api", striprefs = true)
public class JourneyMap6Adapter implements IClientPlugin, MapOverlaySink {

    public static final String GROUP_NAME = "flamechunk.heatmap";
    public IClientAPI api;
    public final Int2ObjectOpenHashMap<Long2ObjectOpenHashMap<PolygonOverlay>> overlaysByPosition = new Int2ObjectOpenHashMap<>();
    public MapOverlayModel previousModel = ClientMapOverlayState.emptyModel();
    public long previousDisplaySettings = displaySettings();

    @Override
    @Optional.Method(modid = "journeymap_api")
    public String getModId() {
        return FlameChunk.MODID;
    }

    @Override
    @Optional.Method(modid = "journeymap_api")
    public void initialize(@NotNull IClientAPI value) {
        api = value;
        MapOverlayApi.register(this);
        publish(ClientMapOverlayState.get());
        MapOverlayControls.requestWeakSnapshot();
    }

    @Optional.Method(modid = "journeymap_api")
    public static void addPopupMenuItems(FullscreenPopupMenuEvent event) {
        if (event.getFullscreen() == null || event.getFullscreen()
            .getUiState() == null) {
            return;
        }
        final int mapDimensionId = event.getFullscreen()
            .getUiState().dimension;
        ModPopupMenu popupMenu = event.getPopupMenu();
        popupMenu.addMenuItem(MapOverlayControls.scanMenuLabel(), position -> MapOverlayControls.toggleScan());
        popupMenu.addMenuItem(
            StatCollector.translateToLocal("flamechunk.client.journeymap.clear"),
            position -> MapOverlayControls.clear());
        popupMenu.addMenuItem(StatCollector.translateToLocal("flamechunk.client.map.weakclear"), position -> {
            if (position != null) {
                MapOverlayControls.openWeakClearSelection(mapDimensionId, position.getX() >> 4, position.getZ() >> 4);
            }
        });
        popupMenu.addMenuItem(StatCollector.translateToLocal("flamechunk.client.map.loader.toggle"), position -> {
            if (position != null && Minecraft.getMinecraft().theWorld != null) {
                int chunkX = position.getX() >> 4;
                int chunkZ = position.getZ() >> 4;
                if (MapOverlayControls.hasLoaderControlTarget(mapDimensionId, chunkX, chunkZ)) {
                    MapOverlayControls
                        .confirmLoaderToggle(Minecraft.getMinecraft().currentScreen, mapDimensionId, chunkX, chunkZ);
                }
            }
        });
    }

    @Override
    @Optional.Method(modid = "journeymap_api")
    public void publish(MapOverlayModel model) {
        if (NavigatorMapBridge.ownsJourneyMap() || api == null || model == null) {
            return;
        }
        long currentDisplaySettings = displaySettings();
        boolean refreshAll = previousDisplaySettings != currentDisplaySettings;
        for (MapOverlayCell previousCell : previousModel.getCells()) {
            if (model.find(previousCell.getDimensionId(), previousCell.getChunkX(), previousCell.getChunkZ()) == null) {
                removeOverlay(previousCell.getDimensionId(), previousCell.getChunkX(), previousCell.getChunkZ());
            }
        }
        for (MapOverlayCell cell : model.getCells()) {
            Long2ObjectOpenHashMap<PolygonOverlay> dimensionOverlays = overlaysByPosition.get(cell.getDimensionId());
            if (dimensionOverlays == null) {
                dimensionOverlays = new Long2ObjectOpenHashMap<>();
                overlaysByPosition.put(cell.getDimensionId(), dimensionOverlays);
            }
            long positionKey = MapOverlayModel.key(cell.getChunkX(), cell.getChunkZ());
            PolygonOverlay previousOverlay = dimensionOverlays.get(positionKey);
            MapOverlayCell previousCell = previousModel.find(cell.getDimensionId(), cell.getChunkX(), cell.getChunkZ());
            if (!refreshAll && previousOverlay != null
                && previousCell != null
                && sameOverlayContent(previousCell, cell)) {
                continue;
            }
            try {
                if (previousOverlay != null) {
                    updateOverlay(previousOverlay, cell);
                    api.show(previousOverlay);
                } else {
                    PolygonOverlay overlay = createOverlay(cell);
                    api.show(overlay);
                    dimensionOverlays.put(positionKey, overlay);
                }
            } catch (Exception | LinkageError exception) {
                if (previousOverlay != null) {
                    try {
                        api.remove(previousOverlay);
                    } catch (RuntimeException | LinkageError cleanupException) {
                        exception.addSuppressed(cleanupException);
                    }
                }
                dimensionOverlays.remove(positionKey);
                FlameChunk.LOG.warn("Unable to show a JourneyMap heatmap overlay", exception);
            }
        }
        previousModel = model;
        previousDisplaySettings = currentDisplaySettings;
    }

    @Override
    @Optional.Method(modid = "journeymap_api")
    public void clear() {
        if (api != null) {
            try {
                api.removeAll(FlameChunk.MODID, DisplayType.Polygon);
            } catch (RuntimeException | LinkageError exception) {
                FlameChunk.LOG.debug("Unable to remove JourneyMap heatmap overlays", exception);
            }
        }
        overlaysByPosition.clear();
        previousModel = ClientMapOverlayState.emptyModel();
        previousDisplaySettings = displaySettings();
    }

    public static long displaySettings() {
        long settings = 0L;
        settings |= ClientConfig.tooltipCoordinates ? 1L : 0L;
        settings |= ClientConfig.tooltipEntityCount ? 1L << 1 : 0L;
        settings |= ClientConfig.tooltipTotal ? 1L << 2 : 0L;
        settings |= ClientConfig.tooltipLoadLevel ? 1L << 3 : 0L;
        settings |= ClientConfig.tooltipTicketSource ? 1L << 4 : 0L;
        settings |= ClientConfig.tooltipCategoryNamesShort ? 1L << 5 : 0L;
        settings |= ClientConfig.tooltipCategoryUnits ? 1L << 6 : 0L;
        settings |= ClientConfig.showLoaderSources ? 1L << 7 : 0L;
        int enabledCategories = 0;
        for (TickCategory category : TickCategory.values()) {
            if (ClientConfig.tooltipCategories.contains(category)) {
                enabledCategories |= 1 << category.ordinal();
            }
        }
        return settings | (long) enabledCategories << 8;
    }

    @Optional.Method(modid = "journeymap_api")
    public void removeOverlay(int dimensionId, int chunkX, int chunkZ) {
        Long2ObjectOpenHashMap<PolygonOverlay> dimensionOverlays = overlaysByPosition.get(dimensionId);
        if (dimensionOverlays == null) {
            return;
        }
        long positionKey = MapOverlayModel.key(chunkX, chunkZ);
        PolygonOverlay overlay = dimensionOverlays.remove(positionKey);
        if (overlay != null) {
            api.remove(overlay);
        }
        if (dimensionOverlays.isEmpty()) {
            overlaysByPosition.remove(dimensionId);
        }
    }

    public static boolean sameOverlayContent(MapOverlayCell first, MapOverlayCell second) {
        return first.hasSameContent(second);
    }

    public static boolean sameEntityTypes(List<EntityTypeCount> first, List<EntityTypeCount> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int index = 0; index < first.size(); index++) {
            var firstType = first.get(index);
            var secondType = second.get(index);
            if (firstType.getCount() != secondType.getCount() || !firstType.getTypeId()
                .equals(secondType.getTypeId())) {
                return false;
            }
        }
        return true;
    }

    public static boolean sameTypeTimings(List<ChunkTypeTiming> first, List<ChunkTypeTiming> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int index = 0; index < first.size(); index++) {
            var firstTiming = first.get(index);
            var secondTiming = second.get(index);
            if (firstTiming.getCategory() != secondTiming.getCategory() || !firstTiming.getTypeName()
                .equals(secondTiming.getTypeName())
                || firstTiming.getNanos() != secondTiming.getNanos()
                || firstTiming.getCount() != secondTiming.getCount()
                || firstTiming.getPeakNanos() != secondTiming.getPeakNanos()) {
                return false;
            }
        }
        return true;
    }

    @Optional.Method(modid = "journeymap_api")
    public PolygonOverlay createOverlay(MapOverlayCell cell) {
        return createNavigatorOverlay(cell);
    }

    @Optional.Method(modid = "journeymap_api")
    public static PolygonOverlay createNavigatorOverlay(MapOverlayCell cell) {
        int minX = cell.getChunkX() << 4;
        int minZ = cell.getChunkZ() << 4;
        int maxX = minX + 16;
        int maxZ = minZ + 16;
        List<BlockPos> points = new ArrayList<>();
        points.add(new BlockPos(minX, 64, minZ));
        points.add(new BlockPos(maxX, 64, minZ));
        points.add(new BlockPos(maxX, 64, maxZ));
        points.add(new BlockPos(minX, 64, maxZ));
        PolygonOverlay overlay = new PolygonOverlay(
            FlameChunk.MODID,
            cell.getDimensionId(),
            new ShapeProperties(),
            new MapPolygon(points));
        updateNavigatorOverlay(overlay, cell);
        return overlay;
    }

    @Optional.Method(modid = "journeymap_api")
    public void updateOverlay(PolygonOverlay overlay, MapOverlayCell cell) {
        updateNavigatorOverlay(overlay, cell);
    }

    @Optional.Method(modid = "journeymap_api")
    public static void updateNavigatorOverlay(PolygonOverlay overlay, MapOverlayCell cell) {
        ShapeProperties properties = new ShapeProperties().setFillColor(cell.getColor())
            .setFillOpacity(cell.getOpacity())
            .setStrokeColor(cell.getTicketSourceColor())
            .setStrokeOpacity(
                ClientConfig.showLoaderSources && cell.getTicketSourceCode() > 0 ? ColorUtils.TICKET_STROKE_OPACITY
                    : 0.0F)
            .setStrokeWidth(1.5F);
        overlay.setShapeProperties(properties);
        overlay.setTextProperties(
            new TextProperties().setColor(ColorUtils.rgb(ColorUtils.TEXT_PRIMARY))
                .setBackgroundOpacity(0.0F)
                .setFontShadow(true)
                .setScale(1.0F)
                .setMinZoom(2)
                .setActiveUIs(Context.UI.Fullscreen));
        // The label is the per-chunk ms/t readout; the title would duplicate it inside JourneyMap's own tooltip.
        overlay.setOverlayGroupName(GROUP_NAME)
            .setTitle(null)
            .setLabel(cell.getLabel())
            .setActiveUIs(Context.UI.Fullscreen, Context.UI.Minimap)
            .setDisplayOrder(100)
            .flagForRerender();
    }
}
