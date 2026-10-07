package com.hfstudio.flamechunk.client.integration;

import java.util.Collection;
import java.util.Collections;

import com.gtnewhorizons.navigator.api.model.locations.ILocationProvider;

import cpw.mods.fml.common.Optional;

public class NavigatorJourneyMap6Bridge {

    @Optional.Method(modid = "journeymap_api")
    public static Collection<?> createOverlays(ILocationProvider location) {
        if (!(location instanceof NavigatorHeatmapLayer.CellLocation cellLocation) || cellLocation.getCell() == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(JourneyMap6Adapter.createNavigatorOverlay(cellLocation.getCell()));
    }
}
