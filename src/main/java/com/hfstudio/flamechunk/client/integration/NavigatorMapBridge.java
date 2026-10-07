package com.hfstudio.flamechunk.client.integration;

import com.gtnewhorizons.navigator.api.NavigatorApi;
import com.gtnewhorizons.navigator.api.model.SupportedMods;
import com.hfstudio.flamechunk.common.integration.Mods;

import cpw.mods.fml.common.Optional;

public class NavigatorMapBridge {

    public static void initialize() {
        if (Mods.Navigator.isModLoaded()) {
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

    public static boolean isXaeroWorldMapLayerActive() {
        return Mods.Navigator.isModLoaded() && navigatorXaeroWorldMapLayerActive();
    }

    @Optional.Method(modid = "navigator")
    public static void registerNavigatorLayer() {
        NavigatorApi.registerLayerManager(NavigatorHeatmapLayer.INSTANCE);
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
        return NavigatorHeatmapLayer.INSTANCE.isEnabled(SupportedMods.XaeroMiniMap);
    }

    @Optional.Method(modid = "navigator")
    public static boolean navigatorJourneyMapLayerActive() {
        return NavigatorHeatmapLayer.INSTANCE.isLayerActive();
    }

    @Optional.Method(modid = "navigator")
    public static boolean navigatorXaeroWorldMapLayerActive() {
        return NavigatorHeatmapLayer.INSTANCE.isLayerActive();
    }
}
