package com.hfstudio.flamechunk.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import org.lwjgl.opengl.GL11;

public class ChunkLabelRenderer {

    public static final double MINIMUM_CELL_PIXELS = 24.0D;
    public static final double MINIMUM_TEXT_PIXELS = 4.5D;
    public static final double FILL_RATIO = 0.92D;

    public static void draw(String text, double centerX, double centerY, double cellUnits, double unitsToPixels) {
        draw(text, centerX, centerY, cellUnits, unitsToPixels, ColorUtils.TEXT_PRIMARY.getColor());
    }

    public static void draw(String text, double centerX, double centerY, double cellUnits, double unitsToPixels,
        int color) {
        if (text == null || text.isEmpty()
            || !Double.isFinite(cellUnits)
            || cellUnits <= 0.0D
            || !Double.isFinite(unitsToPixels)
            || unitsToPixels <= 0.0D
            || cellUnits * unitsToPixels < MINIMUM_CELL_PIXELS) {
            return;
        }
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        if (font == null) {
            return;
        }
        int textWidth = font.getStringWidth(text);
        if (textWidth <= 0) {
            return;
        }
        double fontScale = cellUnits * FILL_RATIO / textWidth;
        if (font.FONT_HEIGHT * fontScale * unitsToPixels < MINIMUM_TEXT_PIXELS) {
            return;
        }
        GL11.glPushMatrix();
        GL11.glTranslated(centerX, centerY, 0.0D);
        GL11.glScaled(fontScale, fontScale, 1.0D);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        font.drawStringWithShadow(text, -textWidth / 2, -font.FONT_HEIGHT / 2, color);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glPopMatrix();
    }
}
