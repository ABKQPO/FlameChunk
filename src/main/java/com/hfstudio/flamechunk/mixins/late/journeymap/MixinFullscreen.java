package com.hfstudio.flamechunk.mixins.late.journeymap;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.StatCollector;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hfstudio.flamechunk.client.integration.JourneyMap5OverlayRenderer;
import com.hfstudio.flamechunk.client.integration.MapControlIds;
import com.hfstudio.flamechunk.client.integration.MapOverlayControls;

import journeymap.client.render.map.GridRenderer;
import journeymap.client.ui.fullscreen.Fullscreen;

@Mixin(value = Fullscreen.class, remap = false)
public abstract class MixinFullscreen {

    @Inject(method = "initGui", at = @At("RETURN"), remap = true)
    private void flamechunk$addControls(CallbackInfo callbackInfo) {
        Fullscreen screen = (Fullscreen) (Object) this;
        screen.getButtonList()
            .add(
                new GuiButton(
                    MapControlIds.JOURNEYMAP_SCAN,
                    6,
                    screen.height - 24,
                    76,
                    20,
                    StatCollector.translateToLocal("flamechunk.client.scan")));
        screen.getButtonList()
            .add(
                new GuiButton(
                    MapControlIds.JOURNEYMAP_CLEAR,
                    86,
                    screen.height - 24,
                    76,
                    20,
                    StatCollector.translateToLocal("flamechunk.client.clear")));
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), remap = true)
    private void flamechunk$handleControl(GuiButton guibutton, CallbackInfo callbackInfo) {
        if (guibutton == null) {
            return;
        }
        if (guibutton.id == MapControlIds.JOURNEYMAP_SCAN) {
            MapOverlayControls.requestScan();
        } else if (guibutton.id == MapControlIds.JOURNEYMAP_CLEAR) {
            MapOverlayControls.clear();
        }
    }

    @Redirect(
        method = "drawMap",
        at = @At(value = "INVOKE", target = "Ljourneymap/client/render/map/GridRenderer;draw(FDDZ)V"))
    private void flamechunk$renderHeatmap(GridRenderer renderer, float alpha, double offsetX, double offsetZ,
        boolean showGrid) {
        renderer.draw(alpha, offsetX, offsetZ, showGrid);
        JourneyMap5OverlayRenderer.render(renderer, offsetX, offsetZ);
    }
}
