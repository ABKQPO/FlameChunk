package com.hfstudio.flamechunk.client.integration;

import net.minecraft.client.Minecraft;

public class JourneyMap5OverlayRenderer {

    public static void render(int zoom) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer == null || minecraft.theWorld == null || zoom < 0 || zoom > 5) {
            return;
        }
        double scale = Math.scalb(1.0D, zoom);
        XaeroOverlayRenderer.render(minecraft.thePlayer.posX, minecraft.thePlayer.posZ, scale);
    }
}
