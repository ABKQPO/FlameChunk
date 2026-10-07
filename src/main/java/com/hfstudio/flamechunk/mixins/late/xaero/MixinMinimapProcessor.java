package com.hfstudio.flamechunk.mixins.late.xaero;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hfstudio.flamechunk.client.integration.NavigatorMapBridge;
import com.hfstudio.flamechunk.client.integration.XaeroOverlayRenderer;

import xaero.common.minimap.MinimapProcessor;

@Mixin(value = MinimapProcessor.class, remap = false)
public abstract class MixinMinimapProcessor {

    @Shadow(remap = false)
    private double minimapZoom;

    @Inject(method = "onRender", at = @At("RETURN"), remap = false)
    private void flamechunk$renderHeatmap(int x, int y, int width, int height, int scale, int size, int boxSize,
        float partial, CallbackInfo callbackInfo) {
        if (!NavigatorMapBridge.ownsXaeroMinimap()) {
            XaeroOverlayRenderer.renderMinimap(x, y, boxSize, partial, minimapZoom);
        }
    }
}
