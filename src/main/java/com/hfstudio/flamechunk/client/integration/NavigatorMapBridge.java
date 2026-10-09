package com.hfstudio.flamechunk.client.integration;

import com.gtnewhorizons.navigator.api.NavigatorApi;
import com.gtnewhorizons.navigator.api.model.SupportedMods;
import com.gtnewhorizons.navigator.api.model.layers.LayerRenderer;
import com.hfstudio.flamechunk.common.integration.Mods;

import cpw.mods.fml.common.Optional;

public class NavigatorMapBridge {

    private static boolean initialized;

    public static void initialize() {
        if (!initialized && Mods.Navigator.isModLoaded()) {
            initialized = true;
            registerNavigatorLayer();
        }
    }

    public static void publish(MapOverlayModel model) {
        if (Mods.Navigator.isModLoaded()) {
            publishToNavigator(model);
        }
    }

    public static boolean ownsJourneyMap() {
        return Mods.Navigator.isModLoaded() && navigatorOwnsJourneyMap();
    }

    public static boolean ownsXaeroWorldMap() {
        return Mods.Navigator.isModLoaded() && navigatorOwnsXaeroWorldMap();
    }

    public static boolean ownsXaeroMinimap() {
        return Mods.Navigator.isModLoaded() && navigatorOwnsXaeroMinimap();
    }

    public static boolean isJourneyMapLayerActive() {
        return Mods.Navigator.isModLoaded() && navigatorJourneyMapLayerActive();
    }

    public static boolean hasJourneyMapRenderSteps() {
        return Mods.Navigator.isModLoaded() && navigatorHasJourneyMapRenderSteps();
    }

    public static boolean hasXaeroWorldMapRenderSteps() {
        return Mods.Navigator.isModLoaded() && navigatorHasXaeroWorldMapRenderSteps();
    }

    public static boolean isXaeroWorldMapLayerActive() {
        return Mods.Navigator.isModLoaded() && navigatorXaeroWorldMapLayerActive();
    }

    public static boolean isXaeroWorldMapHeatmapVisible() {
        return !ownsXaeroWorldMap() || isXaeroWorldMapLayerActive();
    }

    public static boolean isJourneyMapHeatmapVisible() {
        return !ownsJourneyMap() || isJourneyMapLayerActive();
    }

    public static int xaeroScanButtonSlot() {
        return Mods.Navigator.isModLoaded() ? navigatorXaeroScanButtonSlot() : -1;
    }

    public static int xaeroButtonCount() {
        return Mods.Navigator.isModLoaded() ? navigatorXaeroButtonCount() : 0;
    }

    @Optional.Method(modid = "navigator")
    public static int navigatorXaeroScanButtonSlot() {
        return NavigatorApi.getEnabledButtons(SupportedMods.XaeroWorldMap)
            .indexOf(NavigatorActionLayer.SCAN.getButtonManager());
    }

    @Optional.Method(modid = "navigator")
    public static int navigatorXaeroButtonCount() {
        return NavigatorApi.getEnabledButtons(SupportedMods.XaeroWorldMap)
            .size();
    }

    @Optional.Method(modid = "navigator")
    public static void registerNavigatorLayer() {
        NavigatorApi.registerLayerManager(NavigatorHeatmapLayer.INSTANCE);
        NavigatorActionLayer.BUTTONS.forEach(NavigatorApi::registerLayerManager);
        NavigatorHeatmapLayer.INSTANCE.publish(ClientMapOverlayState.get());
    }

    @Optional.Method(modid = "navigator")
    public static void publishToNavigator(MapOverlayModel model) {
        NavigatorHeatmapLayer.INSTANCE.publish(model);
    }

    @Optional.Method(modid = "navigator")
    public static boolean navigatorOwnsJourneyMap() {
        return NavigatorHeatmapLayer.INSTANCE.isEnabled(SupportedMods.JourneyMap);
    }

    @Optional.Method(modid = "navigator")
    public static boolean navigatorOwnsXaeroWorldMap() {
        return NavigatorHeatmapLayer.INSTANCE.isEnabled(SupportedMods.XaeroWorldMap);
    }

    @Optional.Method(modid = "navigator")
    public static boolean navigatorOwnsXaeroMinimap() {
        // Navigator renders the Xaero minimap through its Xaero World Map renderer.
        return NavigatorHeatmapLayer.INSTANCE.isEnabled(SupportedMods.XaeroWorldMap);
    }

    @Optional.Method(modid = "navigator")
    public static boolean navigatorJourneyMapLayerActive() {
        return NavigatorHeatmapLayer.INSTANCE.isLayerActive();
    }

    @Optional.Method(modid = "navigator")
    public static boolean navigatorHasJourneyMapRenderSteps() {
        LayerRenderer renderer = NavigatorHeatmapLayer.INSTANCE.getLayerRenderer(SupportedMods.JourneyMap);
        return NavigatorHeatmapLayer.INSTANCE.isLayerActive() && renderer != null
            && !renderer.getRenderSteps()
                .isEmpty();
    }

    @Optional.Method(modid = "navigator")
    public static boolean navigatorHasXaeroWorldMapRenderSteps() {
        LayerRenderer renderer = NavigatorHeatmapLayer.INSTANCE.getLayerRenderer(SupportedMods.XaeroWorldMap);
        return NavigatorHeatmapLayer.INSTANCE.isLayerActive() && renderer != null
            && !renderer.getRenderSteps()
                .isEmpty();
    }

    @Optional.Method(modid = "navigator")
    public static boolean navigatorXaeroWorldMapLayerActive() {
        return NavigatorHeatmapLayer.INSTANCE.isLayerActive();
    }
}
