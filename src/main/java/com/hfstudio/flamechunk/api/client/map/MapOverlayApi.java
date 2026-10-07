package com.hfstudio.flamechunk.api.client.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

import com.hfstudio.flamechunk.ClientProxy;
import com.hfstudio.flamechunk.FlameChunk;
import com.hfstudio.flamechunk.client.integration.ClientMapOverlayState;
import com.hfstudio.flamechunk.client.integration.MapOverlayControls;
import com.hfstudio.flamechunk.client.integration.MapOverlayModel;
import com.hfstudio.flamechunk.client.integration.MapOverlaySink;
import com.hfstudio.flamechunk.client.integration.XaeroOverlayRenderer;

public class MapOverlayApi {

    public static final int MAX_INTEGRATIONS = 32;
    public static final List<MapOverlaySink> integrations = new ArrayList<>();

    public static synchronized boolean register(MapOverlaySink integration) {
        Objects.requireNonNull(integration, "integration");
        if (integrations.contains(integration)) {
            return false;
        }
        if (integrations.size() >= MAX_INTEGRATIONS) {
            throw new IllegalStateException("Too many map integrations");
        }
        integrations.add(integration);
        if (FlameChunk.proxy instanceof ClientProxy proxy && proxy.mapIntegrations != null) {
            Minecraft.getMinecraft()
                .func_152344_a(() -> {
                    if (registeredIntegrations().contains(integration)) {
                        proxy.mapIntegrations.register(integration);
                    }
                });
        }
        return true;
    }

    public static synchronized boolean unregister(MapOverlaySink integration) {
        boolean removed = integrations.remove(integration);
        if (removed && FlameChunk.proxy instanceof ClientProxy proxy && proxy.mapIntegrations != null) {
            Minecraft.getMinecraft()
                .func_152344_a(() -> proxy.mapIntegrations.unregister(integration));
        }
        return removed;
    }

    public static synchronized List<MapOverlaySink> registeredIntegrations() {
        return List.copyOf(integrations);
    }

    public static synchronized boolean hasRegisteredIntegration() {
        return !integrations.isEmpty();
    }

    public static MapOverlayModel currentOverlay() {
        return ClientMapOverlayState.get();
    }

    public static void render(MapViewport viewport, int mouseX, int mouseY) {
        XaeroOverlayRenderer.render(
            viewport.centerX(),
            viewport.centerZ(),
            viewport.pixelsPerBlock(),
            mouseX,
            mouseY,
            viewport.width(),
            viewport.height(),
            viewport.dimensionId());
    }

    public static void requestScan() {
        MapOverlayControls.requestScan();
    }

    public static void stopScan() {
        MapOverlayControls.requestStop();
    }

    public static void clear() {
        MapOverlayControls.clear();
    }

    public static boolean isScanning() {
        return MapOverlayControls.isScanning();
    }

    public static float scanProgress() {
        return MapOverlayControls.scanProgress();
    }

    public static List<MapMenuItem> menuItems(GuiScreen parent, MapTarget target) {
        List<MapMenuItem> items = new ArrayList<>();
        items.add(
            new MapMenuItem(
                StatCollector.translateToLocal("flamechunk.client.journeymap.scan"),
                MapOverlayApi::requestScan));
        items.add(new MapMenuItem(StatCollector.translateToLocal("flamechunk.client.stop"), MapOverlayApi::stopScan));
        items.add(
            new MapMenuItem(
                StatCollector.translateToLocal("flamechunk.client.journeymap.clear"),
                MapOverlayApi::clear));
        if (target == null || Minecraft.getMinecraft().theWorld == null
            || Minecraft.getMinecraft().theWorld.provider.dimensionId != target.dimensionId()) {
            return List.copyOf(items);
        }
        for (var type : MapOverlayControls.weakClearTargets(target.dimensionId(), target.chunkX(), target.chunkZ())) {
            items.add(
                new MapMenuItem(
                    StatCollector.translateToLocalFormatted(
                        "flamechunk.client.map.weakclear.type",
                        type.getTypeId(),
                        type.getCount()),
                    () -> MapOverlayControls.confirmWeakClear(
                        parent,
                        target.dimensionId(),
                        target.chunkX(),
                        target.chunkZ(),
                        type.getTypeId())));
        }
        if (MapOverlayControls.hasLoaderControlTarget(target.dimensionId(), target.chunkX(), target.chunkZ())) {
            items.add(
                new MapMenuItem(
                    StatCollector.translateToLocal("flamechunk.client.map.loader.toggle"),
                    () -> MapOverlayControls
                        .confirmLoaderToggle(parent, target.dimensionId(), target.chunkX(), target.chunkZ())));
        }
        return List.copyOf(items);
    }
}
