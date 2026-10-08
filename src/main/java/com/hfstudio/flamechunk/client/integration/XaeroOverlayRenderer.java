package com.hfstudio.flamechunk.client.integration;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;

import com.hfstudio.flamechunk.client.config.ClientConfig;
import com.hfstudio.flamechunk.client.render.ColorUtils;
import com.hfstudio.flamechunk.common.data.WeakChunkSnapshot;
import com.hfstudio.flamechunk.common.tick.TickCategory;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

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
        Minecraft minecraft = Minecraft.getMinecraft();
        GuiScreen screen = minecraft.currentScreen;
        World world = minecraft.theWorld;
        if (screen == null || world == null) {
            return;
        }
        var dimensionCells = model.getCells(dimensionId);
        if (!dimensionCells.isEmpty()) {
            renderChunkCells(model, dimensionId, cameraX, cameraZ, scale, width, height);
        }
        MapOverlayCell hoveredIndicator = drawOffscreenRiskIndicators(
            model,
            dimensionId,
            cameraX,
            cameraZ,
            scale,
            width,
            height,
            mouseX,
            mouseY);
        if (mouseX >= 0 && mouseY >= 0 && !dimensionCells.isEmpty()) {
            int mouseChunkX = floorChunk(cameraX + (mouseX - width * 0.5D) / scale);
            int mouseChunkZ = floorChunk(cameraZ + (mouseY - height * 0.5D) / scale);
            MapOverlayCell cell = hoveredIndicator == null ? model.find(dimensionId, mouseChunkX, mouseChunkZ)
                : hoveredIndicator;
            int chunkX = cell == null ? mouseChunkX : cell.getChunkX();
            int chunkZ = cell == null ? mouseChunkZ : cell.getChunkZ();
            MapOverlayTooltip.draw(mouseX, mouseY, cell, chunkX, chunkZ, width, height);
        }
    }

    /** Draws the overlay while Xaero's world-map transform is active. */
    public static void renderWorldMap(double cameraX, double cameraZ, double scale, int dimensionId, int width,
        int height) {
        MapOverlayControls.requestWeakSnapshot();
        if (!Double.isFinite(scale) || scale <= 0.0D || width <= 0 || height <= 0) {
            return;
        }
        MapOverlayModel model = ClientMapOverlayState.get();
        Long2ObjectOpenHashMap<List<MapOverlayCell>> tiles = model.cellsByTile.get(dimensionId);
        if (tiles == null || tiles.isEmpty()) {
            return;
        }
        int minChunkX = floorChunk(cameraX - width * 0.5D / scale) - 1;
        int maxChunkX = floorChunk(cameraX + width * 0.5D / scale) + 1;
        int minChunkZ = floorChunk(cameraZ - height * 0.5D / scale) - 1;
        int maxChunkZ = floorChunk(cameraZ + height * 0.5D / scale) + 1;
        int minTileX = Math.floorDiv(minChunkX, MapOverlayModel.TILE_CHUNKS);
        int maxTileX = Math.floorDiv(maxChunkX, MapOverlayModel.TILE_CHUNKS);
        int minTileZ = Math.floorDiv(minChunkZ, MapOverlayModel.TILE_CHUNKS);
        int maxTileZ = Math.floorDiv(maxChunkZ, MapOverlayModel.TILE_CHUNKS);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        try {
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
                for (int tileZ = minTileZ; tileZ <= maxTileZ; tileZ++) {
                    List<MapOverlayCell> cells = tiles.get(MapOverlayModel.key(tileX, tileZ));
                    if (cells == null) {
                        continue;
                    }
                    for (MapOverlayCell cell : cells) {
                        double minX = cell.getChunkX() * 16.0D - cameraX;
                        double maxX = minX + 16.0D;
                        double minZ = cell.getChunkZ() * 16.0D - cameraZ;
                        double maxZ = minZ + 16.0D;
                        if (maxX * scale < -width * 0.5D || minX * scale > width * 0.5D
                            || maxZ * scale < -height * 0.5D
                            || minZ * scale > height * 0.5D) {
                            continue;
                        }
                        tessellator.setColorRGBA_I(cell.getColor(), ColorUtils.alpha(cell.getOpacity()));
                        tessellator.addVertex(minX, minZ, 0.0D);
                        tessellator.addVertex(maxX, minZ, 0.0D);
                        tessellator.addVertex(maxX, maxZ, 0.0D);
                        tessellator.addVertex(minX, maxZ, 0.0D);
                        if (ClientConfig.showLoaderSources && cell.getTicketSourceCode() > 0) {
                            addTicketOutline(tessellator, cell, minX, maxX, minZ, maxZ);
                        }
                    }
                }
            }
            tessellator.draw();
        } finally {
            GL11.glPopAttrib();
        }
    }

    public static void renderWorldMapTooltip(double cameraX, double cameraZ, double scale, int dimensionId,
        int displayMouseX, int displayMouseY, int mouseX, int mouseY, int width, int height, int displayWidth,
        int displayHeight) {
        if (!Double.isFinite(scale) || scale <= 0.0D
            || width <= 0
            || height <= 0
            || displayWidth <= 0
            || displayHeight <= 0
            || displayMouseX < 0
            || displayMouseY < 0) {
            return;
        }
        MapOverlayModel model = ClientMapOverlayState.get();
        if (model.getCells(dimensionId)
            .isEmpty()) {
            return;
        }
        int chunkX = floorChunk(cameraX + (displayMouseX - displayWidth * 0.5D) / scale);
        int chunkZ = floorChunk(cameraZ + (displayMouseY - displayHeight * 0.5D) / scale);
        MapOverlayCell cell = model.find(dimensionId, chunkX, chunkZ);
        MapOverlayTooltip.draw(mouseX, mouseY, cell, chunkX, chunkZ, width, height);
    }

    public static void renderNavigatorSupplements(double cameraX, double cameraZ, double scale, int mouseX, int mouseY,
        int width, int height, int dimensionId) {
        if (!Double.isFinite(scale) || scale <= 0.0D || width <= 0 || height <= 0) {
            return;
        }
        MapOverlayModel model = ClientMapOverlayState.get();
        if (model.getCells(dimensionId)
            .isEmpty()) {
            return;
        }
        drawOffscreenRiskIndicators(model, dimensionId, cameraX, cameraZ, scale, width, height, mouseX, mouseY);
    }

    public static void renderChunkCells(MapOverlayModel model, int dimensionId, double cameraX, double cameraZ,
        double scale, int width, int height) {
        renderChunkCells(model, dimensionId, cameraX, cameraZ, scale, width, height, false);
    }

    public static void renderChunkCells(MapOverlayModel model, int dimensionId, double cameraX, double cameraZ,
        double scale, int width, int height, boolean centerAtOrigin) {
        if (!Double.isFinite(scale) || scale <= 0.0D || width <= 0 || height <= 0) {
            return;
        }
        Long2ObjectOpenHashMap<List<MapOverlayCell>> tiles = model.cellsByTile.get(dimensionId);
        if (tiles == null || tiles.isEmpty()) {
            return;
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        try {
            double originX = centerAtOrigin ? 0.0D : width * 0.5D;
            double originZ = centerAtOrigin ? 0.0D : height * 0.5D;
            double minViewportX = centerAtOrigin ? -width * 0.5D : 0.0D;
            double maxViewportX = centerAtOrigin ? width * 0.5D : width;
            double minViewportZ = centerAtOrigin ? -height * 0.5D : 0.0D;
            double maxViewportZ = centerAtOrigin ? height * 0.5D : height;
            int minChunkX = floorChunk(cameraX + (minViewportX - originX) / scale) - 1;
            int maxChunkX = floorChunk(cameraX + (maxViewportX - originX) / scale) + 1;
            int minChunkZ = floorChunk(cameraZ + (minViewportZ - originZ) / scale) - 1;
            int maxChunkZ = floorChunk(cameraZ + (maxViewportZ - originZ) / scale) + 1;
            int minTileX = Math.floorDiv(minChunkX, MapOverlayModel.TILE_CHUNKS);
            int maxTileX = Math.floorDiv(maxChunkX, MapOverlayModel.TILE_CHUNKS);
            int minTileZ = Math.floorDiv(minChunkZ, MapOverlayModel.TILE_CHUNKS);
            int maxTileZ = Math.floorDiv(maxChunkZ, MapOverlayModel.TILE_CHUNKS);
            long tileWidth = (long) maxTileX - minTileX + 1L;
            long tileHeight = (long) maxTileZ - minTileZ + 1L;
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            if (tileWidth > 0L && tileHeight > 0L && tileWidth * tileHeight <= tiles.size()) {
                for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
                    for (int tileZ = minTileZ; tileZ <= maxTileZ; tileZ++) {
                        List<MapOverlayCell> tileCells = tiles.get(MapOverlayModel.key(tileX, tileZ));
                        if (tileCells != null) {
                            drawVisibleTile(
                                tessellator,
                                tileCells,
                                cameraX,
                                cameraZ,
                                scale,
                                originX,
                                originZ,
                                minViewportX,
                                maxViewportX,
                                minViewportZ,
                                maxViewportZ);
                        }
                    }
                }
            } else {
                for (Long2ObjectMap.Entry<List<MapOverlayCell>> entry : tiles.long2ObjectEntrySet()) {
                    int tileX = (int) (entry.getLongKey() >> 32);
                    int tileZ = (int) entry.getLongKey();
                    if (tileX >= minTileX && tileX <= maxTileX && tileZ >= minTileZ && tileZ <= maxTileZ) {
                        drawVisibleTile(
                            tessellator,
                            entry.getValue(),
                            cameraX,
                            cameraZ,
                            scale,
                            originX,
                            originZ,
                            minViewportX,
                            maxViewportX,
                            minViewportZ,
                            maxViewportZ);
                    }
                }
            }
            tessellator.draw();
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private static void drawVisibleTile(Tessellator tessellator, List<MapOverlayCell> cells, double cameraX,
        double cameraZ, double scale, double originX, double originZ, double minViewportX, double maxViewportX,
        double minViewportZ, double maxViewportZ) {
        for (MapOverlayCell cell : cells) {
            double minX = (cell.getChunkX() * 16.0D - cameraX) * scale + originX;
            double maxX = (cell.getChunkX() * 16.0D + 16.0D - cameraX) * scale + originX;
            double minZ = (cell.getChunkZ() * 16.0D - cameraZ) * scale + originZ;
            double maxZ = (cell.getChunkZ() * 16.0D + 16.0D - cameraZ) * scale + originZ;
            if (maxX < minViewportX || minX > maxViewportX || maxZ < minViewportZ || minZ > maxViewportZ) {
                continue;
            }
            tessellator.setColorRGBA_I(cell.getColor(), ColorUtils.alpha(cell.getOpacity()));
            tessellator.addVertex(minX, minZ, 0.0D);
            tessellator.addVertex(maxX, minZ, 0.0D);
            tessellator.addVertex(maxX, maxZ, 0.0D);
            tessellator.addVertex(minX, maxZ, 0.0D);
            if (ClientConfig.showLoaderSources && cell.getTicketSourceCode() > 0) {
                addTicketOutline(tessellator, cell, minX, maxX, minZ, maxZ);
            }
        }
    }

    public static MapOverlayCell drawOffscreenRiskIndicators(MapOverlayModel model, int dimensionId, double cameraX,
        double cameraZ, double scale, int width, int height, int mouseX, int mouseY) {
        if (!Double.isFinite(scale) || scale <= 0.0D || width <= 0 || height <= 0) {
            return null;
        }
        MapOverlayCell[] selected = new MapOverlayCell[16];
        double[] angles = new double[16];
        double[] priorities = new double[16];
        int selectedCount = 0;
        for (MapOverlayCell cell : model.getCells(dimensionId)) {
            double priority = riskPriority(cell);
            if (priority <= 0.0D) {
                continue;
            }
            double centerX = toScreenX(cell.getChunkX() * 16.0D + 8.0D, cameraX, scale, width);
            double centerZ = toScreenZ(cell.getChunkZ() * 16.0D + 8.0D, cameraZ, scale, height);
            if (centerX >= 0.0D && centerX <= width && centerZ >= 0.0D && centerZ <= height) {
                continue;
            }
            double angle = Math.atan2(centerZ - height * 0.5D, centerX - width * 0.5D);
            int bucket = directionBucket(angle);
            if (selected[bucket] == null || priority > priorities[bucket]) {
                if (selected[bucket] == null) {
                    selectedCount++;
                }
                selected[bucket] = cell;
                angles[bucket] = angle;
                priorities[bucket] = priority;
            }
        }
        if (selectedCount == 0) {
            return null;
        }

        MapOverlayCell hovered = null;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        try {
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawing(GL11.GL_TRIANGLES);
            for (int index = 0; index < selected.length; index++) {
                MapOverlayCell cell = selected[index];
                if (cell == null) {
                    continue;
                }
                double angle = angles[index];
                double directionX = Math.cos(angle);
                double directionY = Math.sin(angle);
                double limitX = Math.max(1.0D, width * 0.5D - 10.0D);
                double limitY = Math.max(1.0D, height * 0.5D - 10.0D);
                double edgeScale = Math.min(
                    limitX / Math.max(Math.abs(directionX), 1.0E-9D),
                    limitY / Math.max(Math.abs(directionY), 1.0E-9D));
                double markerX = width * 0.5D + directionX * edgeScale;
                double markerY = height * 0.5D + directionY * edgeScale;
                double baseX = markerX - directionX * 12.0D;
                double baseY = markerY - directionY * 12.0D;
                double sideX = -directionY * 5.0D;
                double sideY = directionX * 5.0D;
                tessellator.setColorRGBA_I(ColorUtils.opaque(cell.getColor()), 255);
                tessellator.addVertex(markerX, markerY, 0.0D);
                tessellator.addVertex(baseX + sideX, baseY + sideY, 0.0D);
                tessellator.addVertex(baseX - sideX, baseY - sideY, 0.0D);
                if (mouseX >= 0 && mouseY >= 0) {
                    double deltaX = mouseX - markerX;
                    double deltaY = mouseY - markerY;
                    if (deltaX * deltaX + deltaY * deltaY <= 100.0D) {
                        hovered = cell;
                    }
                }
            }
            tessellator.draw();
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
        return hovered;
    }

    public static double riskPriority(MapOverlayCell cell) {
        double priority = 0.0D;
        if (cell.getSampledTicks() > 0L) {
            long totalNanos = 0L;
            for (int index = 0; index < cell.nanos.length; index++) {
                if (index == TickCategory.BLOCK_UPDATE.ordinal()) {
                    continue;
                }
                long value = cell.nanos[index];
                if (value > 0L && Long.MAX_VALUE - totalNanos < value) {
                    totalNanos = Long.MAX_VALUE;
                    break;
                }
                totalNanos += value;
            }
            double mspt = totalNanos / 1000000.0D / cell.getSampledTicks();
            double threshold = Math.max(0.001D, ClientConfig.heatThresholdMspt);
            if (mspt >= threshold) {
                priority = mspt / threshold;
            }
        }
        if (cell.isWeakChunk() && cell.getEntityCount() >= WeakChunkSnapshot.ENTITY_RED_THRESHOLD) {
            priority = Math.max(priority, cell.getEntityCount() / (double) WeakChunkSnapshot.ENTITY_RED_THRESHOLD);
        }
        return priority;
    }

    public static int directionBucket(double angle) {
        double normalized = (angle + Math.PI) / (Math.PI * 2.0D);
        int bucket = (int) Math.floor(normalized * 16.0D + 0.5D);
        return Math.floorMod(bucket, 16);
    }

    public static void renderMinimap(int left, int top, int boxSize, float partialTicks, double zoom) {
        MapOverlayControls.requestWeakSnapshot();
        if (boxSize <= 0) {
            return;
        }
        MapOverlayModel model = ClientMapOverlayState.get();
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
        int dimensionId = world.provider.dimensionId;
        var dimensionCells = model.getCells(dimensionId);
        if (dimensionCells.isEmpty()) {
            return;
        }
        double centerScreenX = left + boxSize * 0.5D;
        double centerScreenZ = top + boxSize * 0.5D;
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
            for (MapOverlayCell cell : dimensionCells) {
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

    public static void renderMinimapInXaeroMatrix(double cameraX, double cameraZ, double mapScale, int viewportSize,
        double renderAngle) {
        if (!Double.isFinite(mapScale) || mapScale <= 0.0D || viewportSize <= 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        World world = minecraft.theWorld;
        if (world == null) {
            return;
        }
        MapOverlayModel model = ClientMapOverlayState.get();
        List<MapOverlayCell> cells = model.getCells(world.provider.dimensionId);
        if (cells.isEmpty()) {
            return;
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glRotated(renderAngle - 90.0D, 0.0D, 0.0D, 1.0D);
        GL11.glScaled(mapScale, mapScale, 1.0D);
        try {
            double halfViewport = viewportSize / (2.0D * mapScale);
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            for (MapOverlayCell cell : cells) {
                double minX = cell.getChunkX() * 16.0D - cameraX;
                double maxX = minX + 16.0D;
                double minZ = cell.getChunkZ() * 16.0D - cameraZ;
                double maxZ = minZ + 16.0D;
                if (maxX < -halfViewport || minX > halfViewport || maxZ < -halfViewport || minZ > halfViewport) {
                    continue;
                }
                tessellator.setColorRGBA_I(cell.getColor(), ColorUtils.alpha(cell.getOpacity()));
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
