package com.hfstudio.flamechunk.client.integration;

import java.util.ArrayList;
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

    private static final int PADDING = 3;
    private static final int OFFSET = 8;
    private static final int MARGIN = 2;

    public static void draw(int mouseX, int mouseY, MapOverlayCell cell, int width, int height) {
        if (cell == null) {
            return;
        }
        List<String> lines = lines(cell);
        if (lines.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        FontRenderer font = minecraft.fontRenderer;
        int lineHeight = font.FONT_HEIGHT + 1;
        int textWidth = 0;
        for (String line : lines) {
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
        x = Math.max(MARGIN, x);
        y = Math.max(MARGIN, y);
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
        if (cell == null) {
            return new ArrayList<>();
        }
        List<String> lines = new ArrayList<>();
        if (ClientConfig.tooltipCoordinates) {
            lines.add(
                StatCollector
                    .translateToLocalFormatted("flamechunk.tooltip.coords", cell.getChunkX(), cell.getChunkZ()));
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
            lines.add(
                StatCollector.translateToLocalFormatted(
                    "flamechunk.tooltip.category",
                    categoryName(category),
                    String.format(Locale.ENGLISH, "%.3f", mspt),
                    ClientConfig.tooltipCategoryUnits ? " ms/t" : ""));
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

    private static String categoryName(TickCategory category) {
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
