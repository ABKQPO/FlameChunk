package com.hfstudio.flamechunk.mixins.late.xaero;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hfstudio.flamechunk.client.integration.XaeroOverlayRenderer;

import xaero.map.gui.GuiMap;

@Mixin(value = GuiMap.class, remap = false)
public class MixinGuiMap {

    @Shadow(remap = false)
    private double cameraX;
    @Shadow(remap = false)
    private double cameraZ;
    @Shadow(remap = false)
    private double scale;

    @Inject(method = "func_73863_a", at = @At("RETURN"), remap = false)
    private void flamechunk$renderHeatmap(int mouseX, int mouseY, float partialTicks, CallbackInfo callbackInfo) {
        XaeroOverlayRenderer.render(cameraX, cameraZ, scale);
    }
}
