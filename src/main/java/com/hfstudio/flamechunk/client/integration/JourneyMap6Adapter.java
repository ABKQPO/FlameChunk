package com.hfstudio.flamechunk.client.integration;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;

import org.jetbrains.annotations.NotNull;

import com.hfstudio.flamechunk.FlameChunk;
import com.hfstudio.flamechunk.client.config.ClientConfig;
import com.hfstudio.flamechunk.client.render.ColorUtils;

import cpw.mods.fml.common.Optional;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.display.DisplayType;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.event.PopupMenuEvent.FullscreenPopupMenuEvent;
import journeymap.api.v2.client.fullscreen.ModPopupMenu;
import journeymap.api.v2.client.model.MapPolygon;
import journeymap.api.v2.client.model.ShapeProperties;
import journeymap.api.v2.common.Context;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.event.FullscreenEventRegistry;
import journeymap.api.v2.common.util.BlockPos;

@JourneyMapPlugin(apiVersion = "2.0.0", require = false)
@Optional.Interface(iface = "journeymap.api.v2.client.IClientPlugin", modid = "journeymap_api", striprefs = true)
public class JourneyMap6Adapter implements IClientPlugin, MapOverlaySink {

    public static final String GROUP_NAME = "flamechunk.heatmap";
    public static volatile JourneyMap6Adapter activeInstance;
    public IClientAPI api;

    @Override
    @Optional.Method(modid = "journeymap_api")
    public String getModId() {
        return FlameChunk.MODID;
    }

    @Override
    @Optional.Method(modid = "journeymap_api")
    public void initialize(@NotNull IClientAPI value) {
        api = value;
        activeInstance = this;
        FullscreenEventRegistry.FULLSCREEN_POPUP_MENU_EVENT.subscribe(this, FlameChunk.MODID, this::addPopupMenuItems);
        publish(ClientMapOverlayState.get());
        MapOverlayControls.requestWeakSnapshot();
    }

    @Optional.Method(modid = "journeymap_api")
    public void addPopupMenuItems(FullscreenPopupMenuEvent event) {
        if (event.getFullscreen() == null || event.getFullscreen()
            .getUiState() == null) {
            return;
        }
        final int mapDimensionId = event.getFullscreen()
            .getUiState().dimension;
        ModPopupMenu popupMenu = event.getPopupMenu();
        popupMenu.addMenuItem(
            StatCollector.translateToLocal("flamechunk.client.journeymap.scan"),
            position -> MapOverlayControls.requestScan());
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

    public static MapOverlaySink createBridge() {
        return new MapOverlaySink() {

            @Override
            public void publish(MapOverlayModel model) {
                JourneyMap6Adapter adapter = activeInstance;
                if (adapter != null) {
                    adapter.publish(model);
                }
            }

            @Override
            public void clear() {
                JourneyMap6Adapter adapter = activeInstance;
                if (adapter != null) {
                    adapter.clear();
                }
            }
        };
    }

    @Override
    @Optional.Method(modid = "journeymap_api")
    public void publish(MapOverlayModel model) {
        if (api == null || model == null) {
            return;
        }
        clear();
        for (MapOverlayCell cell : model.getCells()) {
            PolygonOverlay overlay = createOverlay(cell);
            try {
                api.show(overlay);
            } catch (Exception exception) {
                FlameChunk.LOG.warn("Unable to show a JourneyMap heatmap overlay", exception);
            }
        }
    }

    @Override
    @Optional.Method(modid = "journeymap_api")
    public void clear() {
        if (api == null) {
            return;
        }
        try {
            api.removeAll(FlameChunk.MODID, DisplayType.Polygon);
        } catch (RuntimeException | LinkageError exception) {
            FlameChunk.LOG.debug("Unable to remove JourneyMap heatmap overlays", exception);
        }
    }

    @Optional.Method(modid = "journeymap_api")
    public PolygonOverlay createOverlay(MapOverlayCell cell) {
        int minX = cell.getChunkX() << 4;
        int minZ = cell.getChunkZ() << 4;
        int maxX = minX + 16;
        int maxZ = minZ + 16;
        List<BlockPos> points = new ArrayList<>();
        points.add(new BlockPos(minX, 64, minZ));
        points.add(new BlockPos(maxX, 64, minZ));
        points.add(new BlockPos(maxX, 64, maxZ));
        points.add(new BlockPos(minX, 64, maxZ));
        ShapeProperties properties = new ShapeProperties().setFillColor(cell.getColor())
            .setFillOpacity(cell.getOpacity())
            .setStrokeColor(cell.getTicketSourceColor())
            .setStrokeOpacity(
                ClientConfig.showLoaderSources && cell.getTicketSourceCode() > 0 ? ColorUtils.TICKET_STROKE_OPACITY
                    : 0.0F)
            .setStrokeWidth(1.5F);
        PolygonOverlay overlay = new PolygonOverlay(
            FlameChunk.MODID,
            cell.getDimensionId(),
            properties,
            new MapPolygon(points));
        overlay.setOverlayGroupName(GROUP_NAME)
            .setTitle(String.join("\n", MapOverlayTooltip.lines(cell)))
            .setLabel(cell.getLabel())
            .setActiveUIs(Context.UI.Fullscreen, Context.UI.Minimap)
            .setDisplayOrder(100);
        return overlay;
    }
}
