package com.hfstudio.flamechunk.client.integration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.StatCollector;

import com.hfstudio.flamechunk.client.config.ClientConfig;
import com.hfstudio.flamechunk.client.render.ColorUtils;
import com.hfstudio.flamechunk.common.data.ChunkTypeTiming;
import com.hfstudio.flamechunk.common.data.WeakChunkSnapshot.EntityTypeCount;
import com.hfstudio.flamechunk.common.tick.TickCategory;

public class MapOverlayTooltip {

    public static final int PADDING = 3;
    public static final int OFFSET = 8;
    public static final int MARGIN = 2;
    public static MapOverlayCell cachedCell;
    public static int cachedChunkX;
    public static int cachedChunkZ;
    public static int cachedNanosHash;
    public static long cachedSignature = -1L;
    public static List<String> cachedLines = List.of();

    public static void draw(int mouseX, int mouseY, MapOverlayCell cell, int width, int height) {
        if (cell != null) {
            draw(mouseX, mouseY, cell, cell.getChunkX(), cell.getChunkZ(), width, height);
        }
    }

    public static void draw(int mouseX, int mouseY, MapOverlayCell cell, int chunkX, int chunkZ, int width,
        int height) {
        List<String> lines = lines(cell, chunkX, chunkZ);
        if (lines.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        FontRenderer font = minecraft.fontRenderer;
        int lineHeight = font.FONT_HEIGHT + 1;
        int maximumTextWidth = Math.max(1, width - MARGIN * 2 - PADDING * 2);
        int maximumLines = Math.max(1, (height - MARGIN * 2 - PADDING * 2) / lineHeight);
        if (lines.size() > maximumLines) {
            int hiddenLines = lines.size() - maximumLines + 1;
            List<String> visibleLines = new ArrayList<>(maximumLines);
            visibleLines.addAll(lines.subList(0, maximumLines - 1));
            visibleLines.add(StatCollector.translateToLocalFormatted("flamechunk.tooltip.truncated", hiddenLines));
            lines = visibleLines;
        }
        int textWidth = 0;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (font.getStringWidth(line) > maximumTextWidth) {
                String suffix = "...";
                int prefixWidth = maximumTextWidth - font.getStringWidth(suffix);
                line = prefixWidth > 0 ? font.trimStringToWidth(line, prefixWidth) + suffix
                    : font.trimStringToWidth(line, maximumTextWidth);
                lines.set(index, line);
            }
            textWidth = Math.max(textWidth, font.getStringWidth(line));
        }
        int boxWidth = textWidth + PADDING * 2;
        int boxHeight = lines.size() * lineHeight + PADDING * 2;
        int x = mouseX + OFFSET;
        int y = mouseY + OFFSET;
        if (x + boxWidth > width - MARGIN) {
            x = mouseX - OFFSET - boxWidth;
        }
        if (y + boxHeight > height - MARGIN) {
            y = mouseY - OFFSET - boxHeight;
        }
        x = Math.max(MARGIN, Math.min(x, width - MARGIN - boxWidth));
        y = Math.max(MARGIN, Math.min(y, height - MARGIN - boxHeight));
        Gui.drawRect(x, y, x + boxWidth, y + boxHeight, ColorUtils.PANEL_BACKGROUND.getColor());
        for (int index = 0; index < lines.size(); index++) {
            font.drawStringWithShadow(
                lines.get(index),
                x + PADDING,
                y + PADDING + index * lineHeight,
                ColorUtils.TEXT_PRIMARY.getColor());
        }
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
            return new ArrayList<>(cachedLines);
        }
        List<String> lines = buildLines(cell, chunkX, chunkZ);
        cachedCell = cell;
        cachedChunkX = chunkX;
        cachedChunkZ = chunkZ;
        cachedNanosHash = nanosHash;
        cachedSignature = signature;
        cachedLines = List.copyOf(lines);
        return lines;
    }

    public static List<String> buildLines(MapOverlayCell cell, int chunkX, int chunkZ) {
        List<String> lines = new ArrayList<>();
        if (!ClientConfig.hasTooltipLines()) {
            return lines;
        }
        if (ClientConfig.tooltipCoordinates) {
            lines.add(StatCollector.translateToLocalFormatted("flamechunk.tooltip.coords", chunkX, chunkZ));
        }
        if (cell == null) {
            lines.add(StatCollector.translateToLocal("flamechunk.tooltip.unsampled"));
            return lines;
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
