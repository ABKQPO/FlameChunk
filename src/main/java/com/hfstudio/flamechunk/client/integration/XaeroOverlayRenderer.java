package com.hfstudio.flamechunk.client.integration;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;

import com.hfstudio.flamechunk.client.config.ClientConfig;
import com.hfstudio.flamechunk.client.render.ColorUtils;

public class XaeroOverlayRenderer {

    public static void render(double cameraX, double cameraZ, double scale) {
        render(cameraX, cameraZ, scale, -1, -1);
    }

    public static void render(double cameraX, double cameraZ, double scale, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getMinecraft();
        GuiScreen screen = minecraft.currentScreen;
        if (screen != null && minecraft.theWorld != null) {
            render(
                cameraX,
                cameraZ,
                scale,
                mouseX,
                mouseY,
                screen.width,
                screen.height,
                minecraft.theWorld.provider.dimensionId);
        }
    }

    public static void render(double cameraX, double cameraZ, double scale, int mouseX, int mouseY, int width,
        int height, int dimensionId) {
        MapOverlayControls.requestWeakSnapshot();
        if (!Double.isFinite(scale) || scale <= 0.0D || width <= 0 || height <= 0) {
            return;
        }
        MapOverlayModel model = ClientMapOverlayState.get();
        if (model.getCells()
            .isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        GuiScreen screen = minecraft.currentScreen;
        World world = minecraft.theWorld;
        if (screen == null || world == null) {
            return;
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        try {
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            for (MapOverlayCell cell : model.getCells()) {
                if (cell.getDimensionId() != dimensionId) {
                    continue;
                }
                double minX = toScreenX(cell.getChunkX() * 16.0D, cameraX, scale, width);
                double maxX = toScreenX(cell.getChunkX() * 16.0D + 16.0D, cameraX, scale, width);
                double minZ = toScreenZ(cell.getChunkZ() * 16.0D, cameraZ, scale, height);
                double maxZ = toScreenZ(cell.getChunkZ() * 16.0D + 16.0D, cameraZ, scale, height);
                if (maxX < 0.0D || minX > width || maxZ < 0.0D || minZ > height) {
                    continue;
                }
                int color = cell.getColor();
                tessellator.setColorRGBA_I(color, ColorUtils.alpha(cell.getOpacity()));
                tessellator.addVertex(minX, minZ, 0.0D);
                tessellator.addVertex(maxX, minZ, 0.0D);
                tessellator.addVertex(maxX, maxZ, 0.0D);
                tessellator.addVertex(minX, maxZ, 0.0D);
                if (ClientConfig.showLoaderSources && cell.getTicketSourceCode() > 0) {
                    addTicketOutline(tessellator, cell, minX, maxX, minZ, maxZ);
                }
            }
            tessellator.draw();
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
        MapOverlayCell hoveredWeakMarker = drawWeakOffscreenMarkers(
            model,
            dimensionId,
            cameraX,
            cameraZ,
            scale,
            width,
            height,
            mouseX,
            mouseY);
        if (mouseX >= 0 && mouseY >= 0) {
            int chunkX = floorChunk(cameraX + (mouseX - width * 0.5D) / scale);
            int chunkZ = floorChunk(cameraZ + (mouseY - height * 0.5D) / scale);
            MapOverlayCell cell = hoveredWeakMarker == null ? model.find(dimensionId, chunkX, chunkZ)
                : hoveredWeakMarker;
            MapOverlayTooltip.draw(mouseX, mouseY, cell, width, height);
        }
    }

    public static MapOverlayCell drawWeakOffscreenMarkers(MapOverlayModel model, int dimensionId, double cameraX,
        double cameraZ, double scale, int width, int height, int mouseX, int mouseY) {
        MapOverlayCell hovered = null;
        for (MapOverlayCell cell : model.getCells()) {
            if (!cell.isWeakChunk() || cell.getDimensionId() != dimensionId) {
                continue;
            }
            double centerX = toScreenX(cell.getChunkX() * 16.0D + 8.0D, cameraX, scale, width);
            double centerZ = toScreenZ(cell.getChunkZ() * 16.0D + 8.0D, cameraZ, scale, height);
            if (centerX >= 0.0D && centerX <= width && centerZ >= 0.0D && centerZ <= height) {
                continue;
            }
            int markerX = Math.max(3, Math.min(width - 9, (int) centerX - 3));
            int markerY = Math.max(3, Math.min(height - 9, (int) centerZ - 3));
            Gui.drawRect(markerX, markerY, markerX + 6, markerY + 6, ColorUtils.opaque(cell.getColor()));
            if (mouseX >= markerX - 2 && mouseX <= markerX + 8 && mouseY >= markerY - 2 && mouseY <= markerY + 8) {
                hovered = cell;
            }
        }
        return hovered;
    }

    public static void renderScanProgress() {
        if (!MapOverlayControls.isScanning()) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        int left = 4;
        int top = 26;
        int width = 162;
        int progressWidth = Math.round(width * MapOverlayControls.scanProgress());
        Gui.drawRect(left, top, left + width, top + 6, ColorUtils.PANEL_BACKGROUND.getColor());
        if (progressWidth > 0) {
            Gui.drawRect(left, top, left + progressWidth, top + 6, ColorUtils.HEAT_LOW.getColor());
        }
        int percent = Math.round(MapOverlayControls.scanProgress() * 100.0F);
        minecraft.fontRenderer.drawStringWithShadow(
            StatCollector.translateToLocalFormatted("flamechunk.client.progress", percent),
            left,
            top + 8,
            ColorUtils.TEXT_PRIMARY.getColor());
    }

    public static void renderMinimap(int left, int top, int boxSize, float partialTicks, double zoom) {
        MapOverlayControls.requestWeakSnapshot();
        if (boxSize <= 0) {
            return;
        }
        MapOverlayModel model = ClientMapOverlayState.get();
        if (model.getCells()
            .isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        Entity player = minecraft.renderViewEntity;
        World world = minecraft.theWorld;
        if (player == null || world == null) {
            return;
        }
        double centerX = interpolate(player.prevPosX, player.posX, partialTicks);
        double centerZ = interpolate(player.prevPosZ, player.posZ, partialTicks);
        if (zoom <= 0.0D) {
            return;
        }
        double centerScreenX = left + boxSize * 0.5D;
        double centerScreenZ = top + boxSize * 0.5D;
        int dimensionId = world.provider.dimensionId;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glTranslatef((float) centerScreenX, (float) centerScreenZ, 0.0F);
        float rotationYaw = interpolateAngle(player.prevRotationYaw, player.rotationYaw, partialTicks);
        GL11.glRotatef(180.0F - rotationYaw, 0.0F, 0.0F, 1.0F);
        try {
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            for (MapOverlayCell cell : model.getCells()) {
                if (cell.getDimensionId() != dimensionId) {
                    continue;
                }
                double minX = (cell.getChunkX() * 16.0D - centerX) * zoom;
                double maxX = (cell.getChunkX() * 16.0D + 16.0D - centerX) * zoom;
                double minZ = (cell.getChunkZ() * 16.0D - centerZ) * zoom;
                double maxZ = (cell.getChunkZ() * 16.0D + 16.0D - centerZ) * zoom;
                if (maxX < -boxSize * 0.5D || minX > boxSize * 0.5D
                    || maxZ < -boxSize * 0.5D
                    || minZ > boxSize * 0.5D) {
                    continue;
                }
                int color = cell.getColor();
                tessellator.setColorRGBA_I(color, ColorUtils.alpha(cell.getOpacity()));
                tessellator.addVertex(minX, minZ, 0.0D);
                tessellator.addVertex(maxX, minZ, 0.0D);
                tessellator.addVertex(maxX, maxZ, 0.0D);
                tessellator.addVertex(minX, maxZ, 0.0D);
                if (ClientConfig.showLoaderSources && cell.getTicketSourceCode() > 0) {
                    addTicketOutline(tessellator, cell, minX, maxX, minZ, maxZ);
                }
            }
            tessellator.draw();
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    public static double interpolate(double previous, double current, float partialTicks) {
        return previous + (current - previous) * partialTicks;
    }

    public static void addTicketOutline(Tessellator tessellator, MapOverlayCell cell, double minX, double maxX,
        double minZ, double maxZ) {
        double width = maxX - minX;
        double height = maxZ - minZ;
        double thickness = Math.min(1.25D, Math.min(width, height) * 0.16D);
        if (thickness <= 0.0D) {
            return;
        }
        int color = cell.getTicketSourceColor();
        tessellator
            .setColorRGBA_I(color, ColorUtils.alpha(Math.max(ColorUtils.TICKET_MINIMUM_OPACITY, cell.getOpacity())));
        addQuad(tessellator, minX, minZ, maxX, minZ + thickness);
        addQuad(tessellator, minX, maxZ - thickness, maxX, maxZ);
        addQuad(tessellator, minX, minZ + thickness, minX + thickness, maxZ - thickness);
        addQuad(tessellator, maxX - thickness, minZ + thickness, maxX, maxZ - thickness);
    }

    public static void addQuad(Tessellator tessellator, double minX, double minZ, double maxX, double maxZ) {
        tessellator.addVertex(minX, minZ, 0.0D);
        tessellator.addVertex(maxX, minZ, 0.0D);
        tessellator.addVertex(maxX, maxZ, 0.0D);
        tessellator.addVertex(minX, maxZ, 0.0D);
    }

    public static float interpolateAngle(float previous, float current, float partialTicks) {
        float delta = current - previous;
        while (delta < -180.0F) {
            delta += 360.0F;
        }
        while (delta >= 180.0F) {
            delta -= 360.0F;
        }
        return previous + delta * partialTicks;
    }

    public static double toScreenX(double block, double camera, double scale, int width) {
        return (block - camera) * scale + width * 0.5D;
    }

    public static double toScreenZ(double block, double camera, double scale, int height) {
        return (block - camera) * scale + height * 0.5D;
    }

    public static int floorChunk(double block) {
        return (int) Math.floor(block / 16.0D);
    }
}
