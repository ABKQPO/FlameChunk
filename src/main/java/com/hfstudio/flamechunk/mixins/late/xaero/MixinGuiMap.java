package com.hfstudio.flamechunk.mixins.late.xaero;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.StatCollector;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hfstudio.flamechunk.client.integration.MapOverlayControls;
import com.hfstudio.flamechunk.client.integration.XaeroOverlayRenderer;

import xaero.map.gui.GuiMap;

@Mixin(value = GuiMap.class, remap = false)
public abstract class MixinGuiMap {

    @Unique
    private static final int FLAMECHUNK_SCAN_BUTTON = 0x465301;
    @Unique
    private static final int FLAMECHUNK_CLEAR_BUTTON = 0x465302;

    @Shadow(remap = false)
    private double cameraX;
    @Shadow(remap = false)
    private double cameraZ;
    @Shadow(remap = false)
    private double scale;

    @Shadow(remap = false)
    public abstract void addGuiButton(GuiButton b);

    @Inject(method = "func_73866_w_", at = @At("RETURN"), remap = false)
    private void flamechunk$addControls(CallbackInfo callbackInfo) {
        addGuiButton(
            new GuiButton(
                FLAMECHUNK_SCAN_BUTTON,
                4,
                4,
                80,
                20,
                StatCollector.translateToLocal("flamechunk.client.scan")));
        addGuiButton(
            new GuiButton(
                FLAMECHUNK_CLEAR_BUTTON,
                86,
                4,
                80,
                20,
                StatCollector.translateToLocal("flamechunk.client.clear")));
    }

    @Inject(method = "func_146284_a", at = @At("HEAD"), remap = false)
    private void flamechunk$handleControl(GuiButton button, CallbackInfo callbackInfo) {
        if (button == null) {
            return;
        }
        if (button.id == FLAMECHUNK_SCAN_BUTTON) {
            MapOverlayControls.requestScan();
        } else if (button.id == FLAMECHUNK_CLEAR_BUTTON) {
            MapOverlayControls.clear();
        }
    }

    @Inject(method = "func_73863_a", at = @At("RETURN"), remap = false)
    private void flamechunk$renderHeatmap(int mouseX, int mouseY, float partialTicks, CallbackInfo callbackInfo) {
        XaeroOverlayRenderer.render(cameraX, cameraZ, scale, mouseX, mouseY);
    }
}
