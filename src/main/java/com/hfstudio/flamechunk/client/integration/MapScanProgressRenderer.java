package com.hfstudio.flamechunk.client.integration;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

import com.hfstudio.flamechunk.client.render.ColorUtils;

public class MapScanProgressRenderer {

    public static final int BAR_WIDTH = 162;
    public static final int BAR_HEIGHT = 6;

    public static void render(int left, int top, int buttonX, int buttonY, int buttonWidth, int buttonHeight) {
        if (!MapOverlayControls.isScanning()) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        GuiScreen screen = minecraft.currentScreen;
        if (screen == null || screen.width <= 0 || screen.height <= 0) {
            return;
        }
        float progress = Math.max(0.0F, Math.min(1.0F, MapOverlayControls.scanProgress()));
        int background = ColorUtils.PANEL_BACKGROUND.getColor();
        int foreground = ColorUtils.TEXT_PRIMARY.getColor();
        if (buttonWidth > 0 && buttonHeight > 0) {
            int perimeter = 2 * (buttonWidth + buttonHeight);
            drawButtonTrack(buttonX, buttonY, buttonWidth, buttonHeight, background);
            drawButtonProgress(
                buttonX,
                buttonY,
                buttonWidth,
                buttonHeight,
                Math.round(perimeter * progress),
                foreground);
        }

        String label = StatCollector
            .translateToLocalFormatted("flamechunk.client.progress", Math.round(progress * 100.0F));
        int barWidth = Math.min(BAR_WIDTH, Math.max(1, screen.width - left - 4));
        int labelHeight = minecraft.fontRenderer.FONT_HEIGHT;
        top = Math.max(2, Math.min(top, screen.height - BAR_HEIGHT - labelHeight - 6));
        Gui.drawRect(left, top, left + barWidth, top + BAR_HEIGHT, background);
        int progressWidth = Math.round(barWidth * progress);
        if (progressWidth > 0) {
            Gui.drawRect(left, top, left + progressWidth, top + BAR_HEIGHT, ColorUtils.HEAT_LOW.getColor());
        }
        minecraft.fontRenderer.drawStringWithShadow(
            minecraft.fontRenderer.trimStringToWidth(label, barWidth),
            left,
            top + BAR_HEIGHT + 2,
            foreground);
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
