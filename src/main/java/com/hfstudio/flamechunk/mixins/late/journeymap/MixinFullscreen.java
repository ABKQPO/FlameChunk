package com.hfstudio.flamechunk.mixins.late.journeymap;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.StatCollector;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hfstudio.flamechunk.client.integration.JourneyMap5OverlayRenderer;
import com.hfstudio.flamechunk.client.integration.MapOverlayControls;

import journeymap.client.render.map.GridRenderer;
import journeymap.client.ui.fullscreen.Fullscreen;

@Mixin(value = Fullscreen.class, remap = false)
public abstract class MixinFullscreen {

    @Unique
    private static final int FLAMECHUNK_SCAN_BUTTON = 0x465311;
    @Unique
    private static final int FLAMECHUNK_CLEAR_BUTTON = 0x465312;

    @Inject(method = "initGui", at = @At("RETURN"), remap = true)
    private void flamechunk$addControls(CallbackInfo callbackInfo) {
        Fullscreen screen = (Fullscreen) (Object) this;
        screen.getButtonList()
            .add(
                new GuiButton(
                    FLAMECHUNK_SCAN_BUTTON,
                    6,
                    screen.height - 24,
                    76,
                    20,
                    StatCollector.translateToLocal("flamechunk.client.scan")));
        screen.getButtonList()
            .add(
                new GuiButton(
                    FLAMECHUNK_CLEAR_BUTTON,
                    86,
                    screen.height - 24,
                    76,
                    20,
                    StatCollector.translateToLocal("flamechunk.client.clear")));
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), remap = true)
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

    @Redirect(
        method = "drawMap",
        at = @At(value = "INVOKE", target = "Ljourneymap/client/render/map/GridRenderer;draw(FDDZ)V"))
    public void flamechunk$renderHeatmap(GridRenderer renderer, float alpha, double offsetX, double offsetY,
        boolean showGrid) {
        renderer.draw(alpha, offsetX, offsetY, showGrid);
        JourneyMap5OverlayRenderer.render(renderer, offsetX, offsetY);
    }
}
