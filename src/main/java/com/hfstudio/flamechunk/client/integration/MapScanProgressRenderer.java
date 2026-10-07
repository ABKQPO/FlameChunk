package com.hfstudio.flamechunk.client.integration;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

import com.hfstudio.flamechunk.client.render.ColorUtils;

public class MapScanProgressRenderer {

    public static void render(int left, int top, int buttonX, int buttonY, int buttonWidth, int buttonHeight) {
        if (!MapOverlayControls.isScanning()) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        GuiScreen screen = minecraft.currentScreen;
        if (screen == null || buttonWidth <= 0 || buttonHeight <= 0) {
            return;
        }
        float progress = Math.max(0.0F, Math.min(1.0F, MapOverlayControls.scanProgress()));
        int perimeter = 2 * (buttonWidth + buttonHeight);
        int completed = Math.round(perimeter * progress);
        int background = ColorUtils.PANEL_BACKGROUND.getColor();
        int foreground = ColorUtils.TEXT_PRIMARY.getColor();
        drawButtonTrack(buttonX, buttonY, buttonWidth, buttonHeight, background);
        drawButtonProgress(buttonX, buttonY, buttonWidth, buttonHeight, completed, foreground);

        int availableWidth = Math.max(1, screen.width - left - 4);
        int width = Math.min(162, availableWidth);
        int availableHeight = Math.max(1, screen.height - 4);
        top = Math.max(2, Math.min(top, availableHeight - minecraft.fontRenderer.FONT_HEIGHT - 10));
        int progressWidth = Math.round(width * progress);
        Gui.drawRect(left, top, left + width, top + 6, background);
        if (progressWidth > 0) {
            Gui.drawRect(left, top, left + progressWidth, top + 6, ColorUtils.HEAT_LOW.getColor());
        }
        String label = StatCollector
            .translateToLocalFormatted("flamechunk.client.progress", Math.round(progress * 100.0F));
        minecraft.fontRenderer.drawStringWithShadow(
            minecraft.fontRenderer.trimStringToWidth(label, width),
            left,
            top + 8,
            ColorUtils.TEXT_PRIMARY.getColor());
    }

    public static void drawButtonTrack(int x, int y, int width, int height, int color) {
        Gui.drawRect(x, y, x + width, y + 1, color);
        Gui.drawRect(x + width - 1, y, x + width, y + height, color);
        Gui.drawRect(x, y + height - 1, x + width, y + height, color);
        Gui.drawRect(x, y, x + 1, y + height, color);
    }

    public static void drawButtonProgress(int x, int y, int width, int height, int completed, int color) {
        int top = Math.min(completed, width);
        if (top > 0) {
            Gui.drawRect(x, y, x + top, y + 1, color);
        }
        int right = Math.max(0, Math.min(height, completed - width));
        if (right > 0) {
            Gui.drawRect(x + width - 1, y, x + width, y + right, color);
        }
        int bottom = Math.max(0, Math.min(width, completed - width - height));
        if (bottom > 0) {
            Gui.drawRect(x + width - bottom, y + height - 1, x + width, y + height, color);
        }
        int left = Math.max(0, Math.min(height, completed - 2 * width - height));
        if (left > 0) {
            Gui.drawRect(x, y + height - left, x + 1, y + height, color);
        }
    }
}
