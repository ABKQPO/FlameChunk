package com.hfstudio.flamechunk.client.integration;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

import org.lwjgl.input.Mouse;

import com.hfstudio.flamechunk.common.data.WeakChunkSnapshot.EntityTypeCount;

import cpw.mods.fml.common.Optional;
import journeymap.client.model.BlockCoordIntPair;
import journeymap.client.render.map.GridRenderer;
import journeymap.client.ui.fullscreen.Fullscreen;

public class JourneyMap5OverlayRenderer {

    @Optional.Method(modid = "journeymap")
    public static void render(GridRenderer renderer, double offsetX, double offsetY, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.theWorld == null || renderer == null || renderer.getMapType() == null) {
            return;
        }
        double scale = Math.scalb(1.0D, renderer.getZoom());
        double cameraX = renderer.getCenterBlockX() - offsetX / scale;
        double cameraZ = renderer.getCenterBlockZ() - offsetY / scale;
        int dimensionId = renderer.getMapType().dimension;
        if (NavigatorMapBridge.ownsJourneyMap()) {
            if (!NavigatorMapBridge.isJourneyMapLayerActive()) {
                return;
            }
            Fullscreen screen = (Fullscreen) minecraft.currentScreen;
            if (screen == null) {
                return;
            }
            for (int index = 0; index < screen.getButtonList()
                .size(); index++) {
                if (!(screen.getButtonList()
                    .get(index) instanceof GuiButton button)) {
                    continue;
                }
                if (button.visible && mouseX >= button.xPosition
                    && mouseY >= button.yPosition
                    && mouseX < button.xPosition + button.width
                    && mouseY < button.yPosition + button.height) {
                    mouseX = -1;
                    mouseY = -1;
                    break;
                }
            }
            XaeroOverlayRenderer.renderNavigatorSupplements(
                cameraX,
                cameraZ,
                scale,
                mouseX,
                mouseY,
                screen.width,
                screen.height,
                dimensionId);
        } else {
            XaeroOverlayRenderer
                .render(cameraX, cameraZ, scale, -1, -1, minecraft.displayWidth, minecraft.displayHeight, dimensionId);
        }
    }

    @Optional.Method(modid = "journeymap")
    public static void renderMinimap(GridRenderer renderer) {
        if (NavigatorMapBridge.ownsJourneyMap()) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.theWorld == null || renderer == null || renderer.getMapType() == null) {
            return;
        }
        MapOverlayControls.requestWeakSnapshot();
        XaeroOverlayRenderer.renderChunkCells(
            ClientMapOverlayState.get(),
            renderer.getMapType().dimension,
            renderer.getCenterBlockX(),
            renderer.getCenterBlockZ(),
            Math.scalb(1.0D, renderer.getZoom()),
            minecraft.displayWidth,
            minecraft.displayHeight,
            true);
    }

    @Optional.Method(modid = "journeymap")
    public static void renderTooltip(Fullscreen screen, GridRenderer renderer, int mouseX, int mouseY) {
        if (NavigatorMapBridge.ownsJourneyMap()) {
            return;
        }
        if (screen == null || renderer == null
            || renderer.getMapType() == null
            || Minecraft.getMinecraft().theWorld == null) {
            return;
        }
        for (int index = 0; index < screen.getButtonList()
            .size(); index++) {
            if (!(screen.getButtonList()
                .get(index) instanceof GuiButton button)) {
                continue;
            }
            if (button.visible && mouseX >= button.xPosition
                && mouseY >= button.yPosition
                && mouseX < button.xPosition + button.width
                && mouseY < button.yPosition + button.height) {
                return;
            }
        }
        int dimensionId = renderer.getMapType().dimension;
        if (ClientMapOverlayState.get()
            .getCells(dimensionId)
            .isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        BlockCoordIntPair block = renderer
            .getBlockUnderMouse(Mouse.getX(), Mouse.getY(), minecraft.displayWidth, minecraft.displayHeight);
        int chunkX = block.x >> 4;
        int chunkZ = block.z >> 4;
        MapOverlayCell cell = ClientMapOverlayState.get()
            .find(dimensionId, chunkX, chunkZ);
        MapOverlayTooltip.draw(mouseX, mouseY, cell, chunkX, chunkZ, screen.width, screen.height);
    }

    @Optional.Method(modid = "journeymap")
    public static void handleRightClick(Fullscreen screen, GridRenderer renderer, int mouseX, int mouseY,
        int mouseButton) {
        if (mouseButton != 1 || screen == null
            || Minecraft.getMinecraft().currentScreen != screen
            || renderer == null
            || renderer.getMapType() == null) {
            return;
        }
        for (int index = 0; index < screen.getButtonList()
            .size(); index++) {
            if (!(screen.getButtonList()
                .get(index) instanceof GuiButton button)) {
                continue;
            }
            if (button.visible && mouseX >= button.xPosition
                && mouseY >= button.yPosition
                && mouseX < button.xPosition + button.width
                && mouseY < button.yPosition + button.height) {
                return;
            }
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.theWorld == null) {
            return;
        }
        BlockCoordIntPair block = renderer
            .getBlockUnderMouse(Mouse.getEventX(), Mouse.getEventY(), minecraft.displayWidth, minecraft.displayHeight);
        int dimensionId = renderer.getMapType().dimension;
        int chunkX = block.x >> 4;
        int chunkZ = block.z >> 4;
        List<EntityTypeCount> weakTargets = MapOverlayControls.weakClearTargets(dimensionId, chunkX, chunkZ);
        boolean hasLoaderTarget = MapOverlayControls.hasLoaderControlTarget(dimensionId, chunkX, chunkZ);
        if (!weakTargets.isEmpty()) {
            MapOverlayControls.openWeakClearSelection(dimensionId, chunkX, chunkZ, hasLoaderTarget);
        } else if (hasLoaderTarget) {
            MapOverlayControls.confirmLoaderToggle(screen, dimensionId, chunkX, chunkZ);
        }
    }
}
