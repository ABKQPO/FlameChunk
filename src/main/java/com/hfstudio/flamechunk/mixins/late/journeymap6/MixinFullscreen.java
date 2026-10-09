package com.hfstudio.flamechunk.mixins.late.journeymap6;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hfstudio.flamechunk.client.config.ClientConfig;
import com.hfstudio.flamechunk.client.integration.MapControlIds;
import com.hfstudio.flamechunk.client.integration.MapOverlayControls;
import com.hfstudio.flamechunk.client.integration.MapOverlayTooltip;
import com.hfstudio.flamechunk.client.integration.MapScanProgressRenderer;

import journeymap.client.ui.component.buttons.Button;
import journeymap.client.ui.component.screens.JmUILegacy;
import journeymap.client.ui.fullscreen.Fullscreen;

@Pseudo
@Mixin(value = Fullscreen.class, remap = false)
public abstract class MixinFullscreen {

    @Unique
    private Button flamechunk$scanButton;
    @Unique
    private Button flamechunk$clearButton;
    @Unique
    private int flamechunk$scanButtonState = Integer.MIN_VALUE;
    @Unique
    private int flamechunk$draggedButtonId = -1;
    @Unique
    private int flamechunk$dragOffsetX;
    @Unique
    private int flamechunk$dragOffsetY;

    @Shadow(remap = false)
    public abstract void addRenderableWidget(Button button);

    @Shadow(remap = false)
    public abstract boolean isButtonsVisable();

    @Inject(method = "layoutButtons", at = @At("TAIL"), remap = false)
    private void flamechunk$addControls(CallbackInfo callbackInfo) {
        if (flamechunk$scanButton == null) {
            flamechunk$scanButton = new Button(
                6,
                flamechunk$screenHeight() - 24,
                new ChatComponentText(MapOverlayControls.scanButtonLabel()),
                button -> MapOverlayControls.toggleScan());
            flamechunk$scanButton.setWidth(76);
            flamechunk$scanButton.setHeight(20);
        }
        if (flamechunk$clearButton == null) {
            flamechunk$clearButton = new Button(
                86,
                flamechunk$screenHeight() - 24,
                new ChatComponentText(StatCollector.translateToLocal("flamechunk.client.journeymap.clear")),
                button -> MapOverlayControls.clear());
            flamechunk$clearButton.setWidth(76);
            flamechunk$clearButton.setHeight(20);
        }
        List<?> renderables = ((JmUILegacy) (Object) this).getRenderables();
        if (isButtonsVisable()) {
            if (!renderables.contains(flamechunk$scanButton)) {
                addRenderableWidget(flamechunk$scanButton);
            }
            if (!renderables.contains(flamechunk$clearButton)) {
                addRenderableWidget(flamechunk$clearButton);
            }
        } else {
            renderables.remove(flamechunk$scanButton);
            renderables.remove(flamechunk$clearButton);
        }
        int screenWidth = Minecraft.getMinecraft().currentScreen.width;
        flamechunk$positionButtons(screenWidth, flamechunk$screenHeight());
        flamechunk$updateScanButton();
    }

    @Inject(method = "drawScreen", at = @At("HEAD"), remap = true)
    private void flamechunk$updateButtonDrag(int mouseX, int mouseY, float partialTicks, CallbackInfo callbackInfo) {
        MapOverlayTooltip.clearPending();
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (screen != null) {
            flamechunk$updateButtonDrag(screen.width, screen.height, mouseX, mouseY);
        }
    }

    @Inject(method = "drawScreen", at = @At("RETURN"), remap = true)
    private void flamechunk$renderScanProgress(int mouseX, int mouseY, float partialTicks, CallbackInfo callbackInfo) {
        flamechunk$finishButtonDrag();
        if (flamechunk$scanButton != null && isButtonsVisable()) {
            flamechunk$updateScanButton();
            MapScanProgressRenderer.render(
                flamechunk$scanButton.getX(),
                flamechunk$scanButton.getY() - 24,
                flamechunk$scanButton.getX(),
                flamechunk$scanButton.getY(),
                flamechunk$scanButton.jmGetWidth(),
                flamechunk$scanButton.getHeight());
        }
        // Drawn last so the tooltip sits above JourneyMap's buttons instead of behind them.
        MapOverlayTooltip.flush();
    }

    @Inject(method = "onGuiClosed", at = @At("RETURN"), remap = true)
    private void flamechunk$resetTooltip(CallbackInfo callbackInfo) {
        MapOverlayTooltip.setContextMenuOpen(false);
        MapOverlayTooltip.clearPending();
    }

    @Unique
    private void flamechunk$updateScanButton() {
        int state = MapOverlayControls.scanButtonState();
        if (state == flamechunk$scanButtonState) {
            return;
        }
        flamechunk$scanButton.setMessage(new ChatComponentText(MapOverlayControls.scanButtonLabel()));
        flamechunk$scanButton.setEnabled(MapOverlayControls.isScanning() || !MapOverlayControls.hasPendingScan());
        flamechunk$scanButtonState = state;
    }

    @Unique
    private int flamechunk$screenHeight() {
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        return screen == null ? 0 : screen.height;
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = true)
    private void flamechunk$beginButtonDrag(int mouseX, int mouseY, int mouseButton, CallbackInfo callbackInfo) {
        // Any click dismisses an open popup; a right-click re-arms the latch later in this same call.
        MapOverlayTooltip.setContextMenuOpen(false);
        if (mouseButton != 1 || flamechunk$scanButton == null || flamechunk$clearButton == null) {
            return;
        }
        Button button = flamechunk$buttonAt(mouseX, mouseY);
        if (button == null) {
            return;
        }
        flamechunk$draggedButtonId = button == flamechunk$scanButton ? MapControlIds.JOURNEYMAP_SCAN
            : MapControlIds.JOURNEYMAP_CLEAR;
        flamechunk$dragOffsetX = mouseX - button.getX();
        flamechunk$dragOffsetY = mouseY - button.getY();
        callbackInfo.cancel();
    }

    @Unique
    private int flamechunk$buttonX(int screenWidth, int x) {
        return Math.max(0, Math.min(x, Math.max(0, screenWidth - 76)));
    }

    @Unique
    private int flamechunk$buttonY(int screenHeight, int buttonId) {
        int bottom = buttonId == MapControlIds.JOURNEYMAP_SCAN ? ClientConfig.journeyMap6ButtonBottom
            : ClientConfig.journeyMap6ClearButtonBottom;
        return Math.max(0, Math.min(screenHeight - bottom - 20, Math.max(0, screenHeight - 20)));
    }

    @Unique
    private void flamechunk$positionButtons(int screenWidth, int screenHeight) {
        flamechunk$scanButton.setPosX(flamechunk$buttonX(screenWidth, ClientConfig.journeyMap6ButtonX));
        flamechunk$scanButton.setPosY(flamechunk$buttonY(screenHeight, MapControlIds.JOURNEYMAP_SCAN));
        flamechunk$clearButton.setPosX(flamechunk$buttonX(screenWidth, ClientConfig.journeyMap6ClearButtonX));
        flamechunk$clearButton.setPosY(flamechunk$buttonY(screenHeight, MapControlIds.JOURNEYMAP_CLEAR));
    }

    @Unique
    private Button flamechunk$buttonAt(int mouseX, int mouseY) {
        if (flamechunk$contains(flamechunk$scanButton, mouseX, mouseY)) {
            return flamechunk$scanButton;
        }
        if (flamechunk$contains(flamechunk$clearButton, mouseX, mouseY)) {
            return flamechunk$clearButton;
        }
        return null;
    }

    @Unique
    private boolean flamechunk$contains(Button button, int mouseX, int mouseY) {
        return button != null && mouseX >= button.getX()
            && mouseY >= button.getY()
            && mouseX < button.getX() + button.jmGetWidth()
            && mouseY < button.getY() + button.getHeight();
    }

    @Unique
    private void flamechunk$updateButtonDrag(int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (flamechunk$draggedButtonId < 0 || !Mouse.isButtonDown(1)) {
            return;
        }
        int x = Math.max(0, Math.min(mouseX - flamechunk$dragOffsetX, Math.max(0, screenWidth - 76)));
        int y = Math.max(0, Math.min(mouseY - flamechunk$dragOffsetY, Math.max(0, screenHeight - 20)));
        if (flamechunk$draggedButtonId == MapControlIds.JOURNEYMAP_SCAN) {
            ClientConfig.journeyMap6ButtonX = x;
            ClientConfig.journeyMap6ButtonBottom = screenHeight - y - 20;
        } else {
            ClientConfig.journeyMap6ClearButtonX = x;
            ClientConfig.journeyMap6ClearButtonBottom = screenHeight - y - 20;
        }
        flamechunk$positionButtons(screenWidth, screenHeight);
    }

    @Unique
    private void flamechunk$finishButtonDrag() {
        if (flamechunk$draggedButtonId >= 0 && !Mouse.isButtonDown(1)) {
            ClientConfig.save();
            flamechunk$draggedButtonId = -1;
        }
    }
}
