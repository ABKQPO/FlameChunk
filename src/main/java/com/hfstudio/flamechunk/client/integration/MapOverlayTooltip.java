package com.hfstudio.flamechunk.client.integration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.hfstudio.flamechunk.client.config.ClientConfig;
import com.hfstudio.flamechunk.common.data.ChunkTypeTiming;
import com.hfstudio.flamechunk.common.data.WeakChunkSnapshot.EntityTypeCount;
import com.hfstudio.flamechunk.common.tick.TickCategory;

public class MapOverlayTooltip {

    /** Vanilla {@code GuiScreen.drawHoveringText} metrics. */
    public static final int LINE_HEIGHT = 10;
    public static final int MOUSE_OFFSET = 12;
    public static final int MARGIN = 4;
    public static final int BACKGROUND_COLOR = 0xF0100010;
    public static final int BORDER_START_COLOR = 0x505000FF;
    public static final int BORDER_END_COLOR = 0x5028007F;
    public static final int TEXT_COLOR = 0xFFFFFFFF;
    public static final float Z_LEVEL = 300.0F;

    public static MapOverlayCell cachedCell;
    public static int cachedChunkX;
    public static int cachedChunkZ;
    public static int cachedNanosHash;
    public static long cachedSignature = -1L;
    public static List<String> cachedLines = List.of();

    public static boolean contextMenuOpen;
    public static List<String> pendingLines = List.of();

    public static void clearPending() {
        pendingLines = List.of();
    }

    public static void setContextMenuOpen(boolean open) {
        contextMenuOpen = open;
        if (open) {
            clearPending();
        }
    }

    public static void draw(int mouseX, int mouseY, MapOverlayCell cell, int width, int height) {
        if (cell != null) {
            draw(mouseX, mouseY, cell, cell.getChunkX(), cell.getChunkZ(), width, height);
        }
    }

    public static void draw(int mouseX, int mouseY, MapOverlayCell cell, int chunkX, int chunkZ, int width,
        int height) {
        if (cell == null || contextMenuOpen || mouseX < 0 || mouseY < 0) {
            return;
        }
        List<String> lines = lines(cell, chunkX, chunkZ);
        if (!lines.isEmpty()) {
            pendingLines = lines;
        }
    }

    public static void flush() {
        List<String> lines = pendingLines;
        clearPending();
        Minecraft minecraft = Minecraft.getMinecraft();
        GuiScreen screen = minecraft.currentScreen;
        if (lines.isEmpty() || screen == null || screen.width <= 0 || screen.height <= 0) {
            return;
        }
        ScaledResolution resolution = new ScaledResolution(minecraft, minecraft.displayWidth, minecraft.displayHeight);
        int mouseX = Mouse.getX() * resolution.getScaledWidth() / Math.max(1, minecraft.displayWidth);
        int mouseY = resolution.getScaledHeight() - 1
            - Mouse.getY() * resolution.getScaledHeight() / Math.max(1, minecraft.displayHeight);
        if (mouseX < 0 || mouseY < 0 || mouseX >= screen.width || mouseY >= screen.height) {
            return;
        }
        drawHoveringText(lines, mouseX, mouseY, screen.width, screen.height);
    }

    public static void drawHoveringText(List<String> lines, int mouseX, int mouseY, int width, int height) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        if (font == null || lines.isEmpty() || width <= 0 || height <= 0) {
            return;
        }
        List<String> bounded = bound(font, lines, width, height);
        int textWidth = 0;
        for (String line : bounded) {
            textWidth = Math.max(textWidth, font.getStringWidth(line));
        }
        int boxHeight = 8;
        if (bounded.size() > 1) {
            boxHeight += 2 + (bounded.size() - 1) * LINE_HEIGHT;
        }
        int left = mouseX + MOUSE_OFFSET;
        int top = mouseY - MOUSE_OFFSET;
        if (left + textWidth > width) {
            left -= 28 + textWidth;
        }
        if (top + boxHeight + 6 > height) {
            top = height - boxHeight - 6;
        }
        left = Math.max(MARGIN, left);
        top = Math.max(MARGIN, top);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        drawGradientRect(left - 3, top - 4, left + textWidth + 3, top - 3, BACKGROUND_COLOR, BACKGROUND_COLOR);
        drawGradientRect(
            left - 3,
            top + boxHeight + 3,
            left + textWidth + 3,
            top + boxHeight + 4,
            BACKGROUND_COLOR,
            BACKGROUND_COLOR);
        drawGradientRect(
            left - 3,
            top - 3,
            left + textWidth + 3,
            top + boxHeight + 3,
            BACKGROUND_COLOR,
            BACKGROUND_COLOR);
        drawGradientRect(left - 4, top - 3, left - 3, top + boxHeight + 3, BACKGROUND_COLOR, BACKGROUND_COLOR);
        drawGradientRect(
            left + textWidth + 3,
            top - 3,
            left + textWidth + 4,
            top + boxHeight + 3,
            BACKGROUND_COLOR,
            BACKGROUND_COLOR);
        drawGradientRect(left - 3, top - 2, left - 2, top + boxHeight + 2, BORDER_START_COLOR, BORDER_END_COLOR);
        drawGradientRect(
            left + textWidth + 2,
            top - 2,
            left + textWidth + 3,
            top + boxHeight + 2,
            BORDER_START_COLOR,
            BORDER_END_COLOR);
        drawGradientRect(left - 3, top - 3, left + textWidth + 3, top - 2, BORDER_START_COLOR, BORDER_START_COLOR);
        drawGradientRect(
            left - 3,
            top + boxHeight + 2,
            left + textWidth + 3,
            top + boxHeight + 3,
            BORDER_END_COLOR,
            BORDER_END_COLOR);

        int lineY = top;
        for (int index = 0; index < bounded.size(); index++) {
            font.drawStringWithShadow(bounded.get(index), left, lineY, TEXT_COLOR);
            if (index == 0) {
                lineY += 2;
            }
            lineY += LINE_HEIGHT;
        }
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static List<String> bound(FontRenderer font, List<String> lines, int width, int height) {
        int maximumTextWidth = Math.max(48, width - 48);
        int maximumLines = Math.max(1, (height - 16) / LINE_HEIGHT);
        List<String> bounded = new ArrayList<>(Math.min(lines.size(), maximumLines));
        int visibleLines = Math.min(lines.size(), maximumLines);
        boolean truncated = lines.size() > maximumLines;
        if (truncated) {
            visibleLines = Math.max(1, maximumLines - 1);
        }
        for (int index = 0; index < visibleLines; index++) {
            bounded.add(trim(font, lines.get(index), maximumTextWidth));
        }
        if (truncated) {
            bounded.add(
                trim(
                    font,
                    StatCollector
                        .translateToLocalFormatted("flamechunk.tooltip.truncated", lines.size() - visibleLines),
                    maximumTextWidth));
        }
        return bounded;
    }

    public static String trim(FontRenderer font, String line, int maximumTextWidth) {
        if (font.getStringWidth(line) <= maximumTextWidth) {
            return line;
        }
        String suffix = "...";
        int prefixWidth = maximumTextWidth - font.getStringWidth(suffix);
        return prefixWidth > 0 ? font.trimStringToWidth(line, prefixWidth) + suffix
            : font.trimStringToWidth(line, maximumTextWidth);
    }

    /** Vanilla {@code Gui.drawGradientRect} at the tooltip Z level. */
    public static void drawGradientRect(int left, int top, int right, int bottom, int startColor, int endColor) {
        float startAlpha = (startColor >> 24 & 0xFF) / 255.0F;
        float startRed = (startColor >> 16 & 0xFF) / 255.0F;
        float startGreen = (startColor >> 8 & 0xFF) / 255.0F;
        float startBlue = (startColor & 0xFF) / 255.0F;
        float endAlpha = (endColor >> 24 & 0xFF) / 255.0F;
        float endRed = (endColor >> 16 & 0xFF) / 255.0F;
        float endGreen = (endColor >> 8 & 0xFF) / 255.0F;
        float endBlue = (endColor & 0xFF) / 255.0F;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_F(startRed, startGreen, startBlue, startAlpha);
        tessellator.addVertex(right, top, Z_LEVEL);
        tessellator.addVertex(left, top, Z_LEVEL);
        tessellator.setColorRGBA_F(endRed, endGreen, endBlue, endAlpha);
        tessellator.addVertex(left, bottom, Z_LEVEL);
        tessellator.addVertex(right, bottom, Z_LEVEL);
        tessellator.draw();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    public static List<String> lines(MapOverlayCell cell) {
        return cell == null ? new ArrayList<>() : lines(cell, cell.getChunkX(), cell.getChunkZ());
    }

    public static List<String> lines(MapOverlayCell cell, int chunkX, int chunkZ) {
        long signature = settingSignature();
        int nanosHash = cell == null ? 0 : Arrays.hashCode(cell.nanos);
        if (cell == cachedCell && chunkX == cachedChunkX
            && chunkZ == cachedChunkZ
            && nanosHash == cachedNanosHash
            && signature == cachedSignature) {
            return cachedLines;
        }
        List<String> lines = buildLines(cell, chunkX, chunkZ);
        cachedCell = cell;
        cachedChunkX = chunkX;
        cachedChunkZ = chunkZ;
        cachedNanosHash = nanosHash;
        cachedSignature = signature;
        cachedLines = List.copyOf(lines);
        return cachedLines;
    }

    public static List<String> buildLines(MapOverlayCell cell, int chunkX, int chunkZ) {
        List<String> lines = new ArrayList<>();
        if (cell == null || !ClientConfig.hasTooltipLines()) {
            return lines;
        }
        if (ClientConfig.tooltipCoordinates) {
            lines.add(StatCollector.translateToLocalFormatted("flamechunk.tooltip.coords", chunkX, chunkZ));
        }
        if (ClientConfig.tooltipEntityCount) {
            lines.add(StatCollector.translateToLocalFormatted("flamechunk.tooltip.entities", cell.getEntityCount()));
        }
        if (cell.isWeakChunk()) {
            for (EntityTypeCount entityType : cell.getWeakEntityTypes()) {
                String typeId = entityType.getTypeId();
                if (typeId.length() > 32) {
                    typeId = typeId.substring(0, 29) + "...";
                }
                lines.add(
                    StatCollector
                        .translateToLocalFormatted("flamechunk.tooltip.weakType", typeId, entityType.getCount()));
            }
        }
        if (ClientConfig.tooltipTotal) {
            lines.add(StatCollector.translateToLocalFormatted("flamechunk.tooltip.total", cell.getLabel()));
        }
        if (ClientConfig.tooltipLoadLevel) {
            lines.add(StatCollector.translateToLocalFormatted("flamechunk.tooltip.load", cell.getLoadLevel()));
        }
        if (ClientConfig.tooltipTicketSource && cell.getTicketSourceCode() > 0
            && cell.getTicketSource() != null
            && !cell.getTicketSource()
                .isEmpty()) {
            lines.add(StatCollector.translateToLocalFormatted("flamechunk.tooltip.ticket", cell.getTicketSource()));
        }
        long[] nanos = cell.getNanos();
        for (TickCategory category : TickCategory.values()) {
            if (!ClientConfig.tooltipCategories.contains(category)) {
                continue;
            }
            double mspt = nanos[category.ordinal()] / 1000000.0D / Math.max(1L, cell.getSampledTicks());
            if (mspt <= 0.0D) {
                continue;
            }
            String name = categoryName(category);
            if (category == TickCategory.BLOCK_UPDATE) {
                name += "*";
            }
            lines.add(
                StatCollector.translateToLocalFormatted(
                    "flamechunk.tooltip.category",
                    name,
                    String.format(Locale.ENGLISH, "%.3f", mspt),
                    ClientConfig.tooltipCategoryUnits ? " ms/t" : ""));
            if (category == TickCategory.BLOCK_UPDATE) {
                lines.add(StatCollector.translateToLocal("flamechunk.tooltip.blockUpdateNote"));
            }
        }
        for (ChunkTypeTiming typeTiming : cell.getTypeTimings()) {
            if (!ClientConfig.tooltipCategories.contains(typeTiming.getCategory())) {
                continue;
            }
            double mspt = typeTiming.getNanos() / 1000000.0D / Math.max(1L, cell.getSampledTicks());
            if (mspt <= 0.0D) {
                continue;
            }
            lines.add(
                StatCollector.translateToLocalFormatted(
                    "flamechunk.tooltip.hotspot",
                    categoryName(typeTiming.getCategory()),
                    typeTiming.getTypeName(),
                    String.format(Locale.ENGLISH, "%.3f", mspt),
                    typeTiming.getCount()));
            lines.add(
                StatCollector
                    .translateToLocalFormatted("flamechunk.client.peak", typeTiming.getPeakNanos() / 1000000.0D));
        }
        return lines;
    }

    public static long settingSignature() {
        long signature = (ClientConfig.tooltipCoordinates ? 1L : 0L) | (ClientConfig.tooltipEntityCount ? 1L << 1 : 0L)
            | (ClientConfig.tooltipTotal ? 1L << 2 : 0L)
            | (ClientConfig.tooltipLoadLevel ? 1L << 3 : 0L)
            | (ClientConfig.tooltipTicketSource ? 1L << 4 : 0L)
            | (ClientConfig.tooltipCategoryNamesShort ? 1L << 5 : 0L)
            | (ClientConfig.tooltipCategoryUnits ? 1L << 6 : 0L);
        for (TickCategory category : TickCategory.values()) {
            if (ClientConfig.tooltipCategories.contains(category)) {
                signature |= 1L << (7 + category.ordinal());
            }
        }
        return signature;
    }

    public static String categoryName(TickCategory category) {
        if (ClientConfig.tooltipCategoryNamesShort) {
            return category.name()
                .substring(
                    0,
                    Math.min(
                        2,
                        category.name()
                            .length()));
        }
        return StatCollector.translateToLocal(
            "flamechunk.category." + category.name()
                .toLowerCase(Locale.ENGLISH));
    }
}
