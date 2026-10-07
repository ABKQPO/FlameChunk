package com.hfstudio.flamechunk.client.integration;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;

public class XaeroOverlayRenderer {

    public static void render(double cameraX, double cameraZ, double scale) {
        if (scale <= 0.0D) {
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
        int dimensionId = world.provider.dimensionId;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
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
                double minX = toScreenX(cell.getChunkX() * 16.0D, cameraX, scale, screen.width);
                double maxX = toScreenX(cell.getChunkX() * 16.0D + 16.0D, cameraX, scale, screen.width);
                double minZ = toScreenZ(cell.getChunkZ() * 16.0D, cameraZ, scale, screen.height);
                double maxZ = toScreenZ(cell.getChunkZ() * 16.0D + 16.0D, cameraZ, scale, screen.height);
                if (maxX < 0.0D || minX > screen.width || maxZ < 0.0D || minZ > screen.height) {
                    continue;
                }
                int color = cell.getColor();
                GL11.glColor4f(
                    (color >> 16 & 0xff) / 255.0F,
                    (color >> 8 & 0xff) / 255.0F,
                    (color & 0xff) / 255.0F,
                    cell.getOpacity());
                tessellator.addVertex(minX, minZ, 0.0D);
                tessellator.addVertex(maxX, minZ, 0.0D);
                tessellator.addVertex(maxX, maxZ, 0.0D);
                tessellator.addVertex(minX, maxZ, 0.0D);
            }
            tessellator.draw();
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    public static void renderMinimap(int left, int top, int boxSize, float partialTicks, double zoom) {
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
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
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
                GL11.glColor4f(
                    (color >> 16 & 0xff) / 255.0F,
                    (color >> 8 & 0xff) / 255.0F,
                    (color & 0xff) / 255.0F,
                    cell.getOpacity());
                tessellator.addVertex(minX, minZ, 0.0D);
                tessellator.addVertex(maxX, minZ, 0.0D);
                tessellator.addVertex(maxX, maxZ, 0.0D);
                tessellator.addVertex(minX, maxZ, 0.0D);
            }
            tessellator.draw();
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private static double interpolate(double previous, double current, float partialTicks) {
        return previous + (current - previous) * partialTicks;
    }

    private static float interpolateAngle(float previous, float current, float partialTicks) {
        float delta = current - previous;
        while (delta < -180.0F) {
            delta += 360.0F;
        }
        while (delta >= 180.0F) {
            delta -= 360.0F;
        }
        return previous + delta * partialTicks;
    }

    private static double toScreenX(double block, double camera, double scale, int width) {
        return (block - camera) * scale + width * 0.5D;
    }

    private static double toScreenZ(double block, double camera, double scale, int height) {
        return (block - camera) * scale + height * 0.5D;
    }
}
