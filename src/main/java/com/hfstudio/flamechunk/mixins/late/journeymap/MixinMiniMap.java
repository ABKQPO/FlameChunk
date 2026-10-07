package com.hfstudio.flamechunk.mixins.late.journeymap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.hfstudio.flamechunk.client.integration.JourneyMap5OverlayRenderer;

import journeymap.client.render.map.GridRenderer;
import journeymap.client.ui.minimap.MiniMap;

@Mixin(value = MiniMap.class, remap = false)
public abstract class MixinMiniMap {

    @Redirect(
        method = "drawMap(ZF)V",
        at = @At(value = "INVOKE", target = "Ljourneymap/client/render/map/GridRenderer;draw(FDDZ)V"),
        remap = false)
    private void flamechunk$renderHeatmap(GridRenderer renderer, float alpha, double offsetX, double offsetZ,
        boolean showGrid) {
        renderer.draw(alpha, offsetX, offsetZ, showGrid);
        JourneyMap5OverlayRenderer.renderMinimap(renderer);
    }
}
