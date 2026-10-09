package com.hfstudio.flamechunk.mixins.late.journeymap;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hfstudio.flamechunk.client.config.ClientConfig;
import com.hfstudio.flamechunk.client.integration.JourneyMap5OverlayRenderer;
import com.hfstudio.flamechunk.client.integration.MapControlIds;
import com.hfstudio.flamechunk.client.integration.MapOverlayControls;
import com.hfstudio.flamechunk.client.integration.MapOverlayTooltip;
import com.hfstudio.flamechunk.client.integration.MapScanProgressRenderer;

import journeymap.client.render.draw.DrawStep;
import journeymap.client.render.draw.DrawWayPointStep;
import journeymap.client.render.map.GridRenderer;
import journeymap.client.ui.fullscreen.Fullscreen;
import journeymap.client.ui.fullscreen.MapChat;
import journeymap.client.ui.fullscreen.layer.LayerDelegate;

@Mixin(value = Fullscreen.class, remap = false)
public abstract class MixinFullscreen {

    @Unique
    private int flamechunk$draggedButtonId = -1;
    @Unique
    private int flamechunk$dragOffsetX;
    @Unique
    private int flamechunk$dragOffsetY;

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
        MapOverlayTooltip.clearPending();
        Fullscreen screen = (Fullscreen) (Object) this;
        MapOverlayControls.updateScanButton(screen.getButtonList(), MapControlIds.JOURNEYMAP_SCAN);
        flamechunk$updateButtonDrag(screen, width, height);
    }

    @Inject(method = "drawScreen", at = @At("RETURN"), remap = true)
    private void flamechunk$renderScanProgress(int mouseX, int mouseY, float partialTicks, CallbackInfo callbackInfo) {
        Fullscreen screen = (Fullscreen) (Object) this;
        int scanX = JourneyMap5OverlayRenderer.buttonX(screen, 0);
        int scanY = JourneyMap5OverlayRenderer.buttonY(screen, 0);
        MapScanProgressRenderer.render(scanX, scanY - 24, scanX, scanY, 76, 20);
        boolean chatOpen = chat != null && !chat.isHidden();
        MapOverlayTooltip.setContextMenuOpen(chatOpen || flamechunk$hasWaypointHover());
        if (!chatOpen && !flamechunk$hasWaypointHover()) {
            JourneyMap5OverlayRenderer.renderTooltip(screen, gridRenderer, mx, my);
        }
        // Drawn last so the tooltip sits above JourneyMap's toolbars and the waypoint layer.
        MapOverlayTooltip.flush();
    }

    @Inject(method = "onGuiClosed", at = @At("RETURN"), remap = true)
    private void flamechunk$resetTooltip(CallbackInfo callbackInfo) {
        MapOverlayTooltip.setContextMenuOpen(false);
        MapOverlayTooltip.clearPending();
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
        flamechunk$ensureControls((Fullscreen) (Object) this);
    }

    @Inject(method = "layoutButtons", at = @At("RETURN"), remap = false)
    private void flamechunk$restoreControls(CallbackInfo callbackInfo) {
        flamechunk$ensureControls((Fullscreen) (Object) this);
    }

    @Unique
    private void flamechunk$ensureControls(Fullscreen screen) {
        boolean scanPresent = false;
        boolean clearPresent = false;
        for (Object element : screen.getButtonList()) {
            if (!(element instanceof GuiButton button)) {
                continue;
            }
            scanPresent |= button.id == MapControlIds.JOURNEYMAP_SCAN;
            clearPresent |= button.id == MapControlIds.JOURNEYMAP_CLEAR;
        }
        if (!scanPresent) {
            screen.getButtonList()
                .add(
                    new GuiButton(
                        MapControlIds.JOURNEYMAP_SCAN,
                        JourneyMap5OverlayRenderer.buttonX(screen, 0),
                        JourneyMap5OverlayRenderer.buttonY(screen, 0),
                        76,
                        20,
                        StatCollector.translateToLocal("flamechunk.client.scan")));
        }
        if (!clearPresent) {
            screen.getButtonList()
                .add(
                    new GuiButton(
                        MapControlIds.JOURNEYMAP_CLEAR,
                        JourneyMap5OverlayRenderer.buttonX(screen, 1),
                        JourneyMap5OverlayRenderer.buttonY(screen, 1),
                        76,
                        20,
                        StatCollector.translateToLocal("flamechunk.client.clear")));
        }
    }

    @Unique
    private void flamechunk$positionControls(Fullscreen screen) {
        for (Object element : screen.getButtonList()) {
            if (!(element instanceof GuiButton button)) {
                continue;
            }
            if (button.id == MapControlIds.JOURNEYMAP_SCAN) {
                button.xPosition = JourneyMap5OverlayRenderer.buttonX(screen, 0);
                button.yPosition = JourneyMap5OverlayRenderer.buttonY(screen, 0);
            } else if (button.id == MapControlIds.JOURNEYMAP_CLEAR) {
                button.xPosition = JourneyMap5OverlayRenderer.buttonX(screen, 1);
                button.yPosition = JourneyMap5OverlayRenderer.buttonY(screen, 1);
            }
        }
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
        if (flamechunk$draggedButtonId >= 0 || (chat != null && !chat.isHidden()) || flamechunk$hasWaypointHover()) {
            return;
        }
        JourneyMap5OverlayRenderer
            .handleRightClick((Fullscreen) (Object) this, gridRenderer, mouseX, mouseY, mouseButton);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = true)
    private void flamechunk$beginButtonDrag(int mouseX, int mouseY, int mouseButton, CallbackInfo callbackInfo) {
        if (mouseButton != 1) {
            return;
        }
        Fullscreen screen = (Fullscreen) (Object) this;
        for (Object element : screen.getButtonList()) {
            if (!(element instanceof GuiButton button)
                || (button.id != MapControlIds.JOURNEYMAP_SCAN && button.id != MapControlIds.JOURNEYMAP_CLEAR)
                || !button.visible
                || mouseX < button.xPosition
                || mouseY < button.yPosition
                || mouseX >= button.xPosition + button.width
                || mouseY >= button.yPosition + button.height) {
                continue;
            }
            flamechunk$draggedButtonId = button.id;
            flamechunk$dragOffsetX = mouseX - button.xPosition;
            flamechunk$dragOffsetY = mouseY - button.yPosition;
            callbackInfo.cancel();
            return;
        }
    }

    @Unique
    private void flamechunk$updateButtonDrag(Fullscreen screen, int mouseX, int mouseY) {
        if (flamechunk$draggedButtonId < 0) {
            return;
        }
        if (!Mouse.isButtonDown(1)) {
            ClientConfig.save();
            flamechunk$draggedButtonId = -1;
            return;
        }
        int x = JourneyMap5OverlayRenderer.boundedButtonX(screen.width, mouseX - flamechunk$dragOffsetX, 76);
        int y = JourneyMap5OverlayRenderer
            .boundedButtonY(screen.height, screen.height - (mouseY - flamechunk$dragOffsetY) - 20, 20);
        if (flamechunk$draggedButtonId == MapControlIds.JOURNEYMAP_SCAN) {
            ClientConfig.journeyMap5ButtonX = x;
            ClientConfig.journeyMap5ButtonBottom = screen.height - y - 20;
        } else {
            ClientConfig.journeyMap5ClearButtonX = x;
            ClientConfig.journeyMap5ClearButtonBottom = screen.height - y - 20;
        }
        flamechunk$positionControls(screen);
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
