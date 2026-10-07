package com.hfstudio.flamechunk.client.integration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import com.hfstudio.flamechunk.FlameChunk;

import cpw.mods.fml.common.Optional;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.display.PolygonOverlay;
import journeymap.api.v2.client.model.MapPolygon;
import journeymap.api.v2.client.model.ShapeProperties;
import journeymap.api.v2.common.Context;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.util.BlockPos;

@JourneyMapPlugin(apiVersion = "2.0.0", require = false)
@Optional.Interface(iface = "journeymap.api.v2.client.IClientPlugin", modid = "journeymap_api", striprefs = true)
public class JourneyMap6Adapter implements IClientPlugin, MapOverlaySink {

    private static final String GROUP_NAME = "flamechunk.heatmap";
    private static volatile JourneyMap6Adapter activeInstance;
    private final Map<String, PolygonOverlay> overlays = new HashMap<>();
    private IClientAPI api;

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
        publish(ClientMapOverlayState.get());
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
    public void publish(MapOverlayModel model) {
        if (api == null || model == null) {
            return;
        }
        clear();
        for (MapOverlayCell cell : model.getCells()) {
            PolygonOverlay overlay = createOverlay(cell);
            overlays.put(overlay.getId(), overlay);
            try {
                api.show(overlay);
            } catch (Exception exception) {
                FlameChunk.LOG.warn("Unable to show a JourneyMap heatmap overlay", exception);
            }
        }
    }

    @Override
    public void clear() {
        if (api == null) {
            overlays.clear();
            return;
        }
        for (PolygonOverlay overlay : overlays.values()) {
            try {
                api.remove(overlay);
            } catch (RuntimeException | LinkageError exception) {
                FlameChunk.LOG.debug("Unable to remove a JourneyMap heatmap overlay", exception);
            }
        }
        overlays.clear();
    }

    private PolygonOverlay createOverlay(MapOverlayCell cell) {
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
            .setStrokeOpacity(0.0F);
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
