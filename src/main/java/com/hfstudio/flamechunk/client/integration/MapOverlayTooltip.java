package com.hfstudio.flamechunk.client.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.StatCollector;

import com.hfstudio.flamechunk.common.tick.TickCategory;

public class MapOverlayTooltip {

    private static final int PADDING = 3;
    private static final int OFFSET = 8;
    private static final int MARGIN = 2;
    private static final int BACKGROUND = 0xC0000000;

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
        Gui.drawRect(x, y, x + boxWidth, y + boxHeight, BACKGROUND);
        for (int index = 0; index < lines.size(); index++) {
            font.drawStringWithShadow(lines.get(index), x + PADDING, y + PADDING + index * lineHeight, 0xFFFFFF);
        }
    }

    public static List<String> lines(MapOverlayCell cell) {
        if (cell == null) {
            return new ArrayList<>();
        }
        List<String> lines = new ArrayList<>();
        lines.add(
            StatCollector.translateToLocalFormatted("flamechunk.tooltip.coords", cell.getChunkX(), cell.getChunkZ()));
        lines.add(StatCollector.translateToLocalFormatted("flamechunk.tooltip.entities", cell.getEntityCount()));
        lines.add(StatCollector.translateToLocalFormatted("flamechunk.tooltip.total", cell.getLabel()));
        lines.add(StatCollector.translateToLocalFormatted("flamechunk.tooltip.load", cell.getLoadLevel()));
        if (cell.getTicketSourceCode() > 0 && cell.getTicketSource() != null
            && !cell.getTicketSource()
                .isEmpty()) {
            lines.add(StatCollector.translateToLocalFormatted("flamechunk.tooltip.ticket", cell.getTicketSource()));
        }
        long[] nanos = cell.getNanos();
        for (TickCategory category : TickCategory.values()) {
            double mspt = nanos[category.ordinal()] / 1000000.0D / Math.max(1L, cell.getSampledTicks());
            if (mspt <= 0.0D) {
                continue;
            }
            lines.add(
                StatCollector.translateToLocalFormatted(
                    "flamechunk.tooltip.category",
                    categoryName(category),
                    String.format(Locale.ENGLISH, "%.3f", mspt)));
        }
        return lines;
    }

    private static String categoryName(TickCategory category) {
        return StatCollector.translateToLocal(
            "flamechunk.category." + category.name()
                .toLowerCase(Locale.ENGLISH));
    }
}
