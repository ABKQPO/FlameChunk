package com.hfstudio.flamechunk.mixins.late.journeymap6;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.StatCollector;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hfstudio.flamechunk.client.integration.MapOverlayControls;
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
                new ChatComponentText(MapOverlayControls.scanMenuLabel()),
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
        flamechunk$scanButton.setPosX(6);
        flamechunk$scanButton.setPosY(flamechunk$screenHeight() - 24);
        flamechunk$clearButton.setPosX(86);
        flamechunk$clearButton.setPosY(flamechunk$screenHeight() - 24);
        flamechunk$updateScanButton();
    }

    @Inject(method = "drawScreen", at = @At("RETURN"), remap = true)
    private void flamechunk$renderScanProgress(int mouseX, int mouseY, float partialTicks, CallbackInfo callbackInfo) {
        if (flamechunk$scanButton == null || !isButtonsVisable()) {
            return;
        }
        MapScanProgressRenderer.render(
            6,
            flamechunk$screenHeight() - 48,
            flamechunk$scanButton.getX(),
            flamechunk$scanButton.getY(),
            flamechunk$scanButton.jmGetWidth(),
            flamechunk$scanButton.getHeight());
    }

    @Unique
    private void flamechunk$updateScanButton() {
        boolean scanning = MapOverlayControls.isScanning();
        boolean pending = MapOverlayControls.hasPendingScan();
        int state = scanning ? 1000 + Math.round(MapOverlayControls.scanProgress() * 100.0F) : pending ? -1 : 0;
        if (state == flamechunk$scanButtonState) {
            return;
        }
        flamechunk$scanButton.setMessage(new ChatComponentText(MapOverlayControls.scanMenuLabel()));
        flamechunk$scanButton.setEnabled(scanning || !pending);
        flamechunk$scanButtonState = state;
    }

    @Unique
    private int flamechunk$screenHeight() {
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        return screen == null ? 0 : screen.height;
    }
}
