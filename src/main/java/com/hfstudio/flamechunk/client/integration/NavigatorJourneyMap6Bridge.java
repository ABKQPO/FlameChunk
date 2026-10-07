package com.hfstudio.flamechunk.client.integration;

import java.util.ArrayList;
import java.util.Collection;

import com.gtnewhorizons.navigator.api.model.locations.ILocationProvider;

import cpw.mods.fml.common.Optional;
import journeymap.api.v2.client.display.PolygonOverlay;

public class NavigatorJourneyMap6Bridge {

    @Optional.Method(modid = "journeymap_api")
    public static Collection<?> createOverlays(ILocationProvider location) {
        if (!(location instanceof NavigatorHeatmapLayer.CellLocation cellLocation) || cellLocation.getCell() == null) {
            return new ArrayList<>();
        }
        ArrayList<PolygonOverlay> overlays = new ArrayList<>(1);
        overlays.add(JourneyMap6Adapter.createNavigatorOverlay(cellLocation.getCell()));
        return overlays;
    }
}
