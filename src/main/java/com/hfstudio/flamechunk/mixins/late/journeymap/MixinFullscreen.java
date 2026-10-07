package com.hfstudio.flamechunk.mixins.late.journeymap;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hfstudio.flamechunk.client.integration.JourneyMap5OverlayRenderer;

import journeymap.client.model.MapState;
import journeymap.client.ui.fullscreen.Fullscreen;

@Mixin(value = Fullscreen.class, remap = false)
public abstract class MixinFullscreen {

    @Shadow(remap = false)
    @Final
    static MapState state;

    @Inject(method = "func_73863_a", at = @At("RETURN"), remap = false)
    private void flamechunk$renderHeatmap(int mouseX, int mouseY, float partialTicks, CallbackInfo callbackInfo) {
        if (state != null) {
            JourneyMap5OverlayRenderer.render(state.getZoom());
        }
    }
}
