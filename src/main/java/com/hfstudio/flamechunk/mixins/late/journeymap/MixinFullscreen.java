package com.hfstudio.flamechunk.mixins.late.journeymap;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.StatCollector;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hfstudio.flamechunk.client.integration.JourneyMap5OverlayRenderer;
import com.hfstudio.flamechunk.client.integration.MapControlIds;
import com.hfstudio.flamechunk.client.integration.MapOverlayControls;
import com.hfstudio.flamechunk.client.integration.MapScanProgressRenderer;

import journeymap.client.render.draw.DrawStep;
import journeymap.client.render.draw.DrawWayPointStep;
import journeymap.client.render.map.GridRenderer;
import journeymap.client.ui.fullscreen.Fullscreen;
import journeymap.client.ui.fullscreen.MapChat;
import journeymap.client.ui.fullscreen.layer.LayerDelegate;

@Mixin(value = Fullscreen.class, remap = false)
public abstract class MixinFullscreen {

    @Shadow(remap = false)
    @Final
    static GridRenderer gridRenderer;

    @Shadow(remap = false)
    int mx;

    @Shadow(remap = false)
    int my;

    @Shadow(remap = false)
    MapChat chat;

    @Shadow(remap = false)
    @Final
    LayerDelegate layerDelegate;

    @Inject(method = "drawScreen", at = @At("HEAD"), remap = true)
    private void flamechunk$updateScanControl(int width, int height, float f, CallbackInfo callbackInfo) {
        Fullscreen screen = (Fullscreen) (Object) this;
        MapOverlayControls.updateScanButton(screen.getButtonList(), MapControlIds.JOURNEYMAP_SCAN);
    }

    @Inject(method = "drawScreen", at = @At("RETURN"), remap = true)
    private void flamechunk$renderScanProgress(int width, int height, float f, CallbackInfo callbackInfo) {
        MapScanProgressRenderer.render(6, height - 48, 6, height - 24, 76, 20);
        if ((chat == null || chat.isHidden()) && !flamechunk$hasWaypointHover()) {
            JourneyMap5OverlayRenderer.renderTooltip((Fullscreen) (Object) this, gridRenderer, mx, my);
        }
    }

    @Unique
    private boolean flamechunk$hasWaypointHover() {
        for (DrawStep step : layerDelegate.getDrawSteps()) {
            if (step instanceof DrawWayPointStep) {
                return true;
            }
        }
        return false;
    }

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
            MapOverlayControls.toggleScan();
        } else if (guibutton.id == MapControlIds.JOURNEYMAP_CLEAR) {
            MapOverlayControls.clear();
        }
    }

    @Inject(method = "mouseClicked", at = @At("RETURN"), remap = true)
    private void flamechunk$handleMapContextAction(int mouseX, int mouseY, int mouseButton, CallbackInfo callbackInfo) {
        if ((chat != null && !chat.isHidden()) || flamechunk$hasWaypointHover()) {
            return;
        }
        JourneyMap5OverlayRenderer
            .handleRightClick((Fullscreen) (Object) this, gridRenderer, mouseX, mouseY, mouseButton);
    }

    @Redirect(
        method = "drawMap",
        at = @At(value = "INVOKE", target = "Ljourneymap/client/render/map/GridRenderer;draw(FDDZ)V"))
    private void flamechunk$renderHeatmap(GridRenderer renderer, float alpha, double offsetX, double offsetZ,
        boolean showGrid) {
        renderer.draw(alpha, offsetX, offsetZ, showGrid);
        int tooltipMouseX = (chat == null || chat.isHidden()) && !flamechunk$hasWaypointHover() ? mx : -1;
        int tooltipMouseY = tooltipMouseX < 0 ? -1 : my;
        JourneyMap5OverlayRenderer.render(renderer, offsetX, offsetZ, tooltipMouseX, tooltipMouseY);
    }
}
