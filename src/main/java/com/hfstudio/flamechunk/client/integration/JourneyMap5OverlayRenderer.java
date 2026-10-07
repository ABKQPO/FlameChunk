package com.hfstudio.flamechunk.client.integration;

import net.minecraft.client.Minecraft;

import cpw.mods.fml.common.Optional;
import journeymap.client.render.map.GridRenderer;

public class JourneyMap5OverlayRenderer {

    @Optional.Method(modid = "journeymap")
    public static void render(GridRenderer renderer, double offsetX, double offsetY) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.theWorld == null || renderer == null || renderer.getMapType() == null) {
            return;
        }
        double scale = Math.scalb(1.0D, renderer.getZoom());
        XaeroOverlayRenderer.render(
            renderer.getCenterBlockX() - offsetX / scale,
            renderer.getCenterBlockZ() - offsetY / scale,
            scale,
            -1,
            -1,
            minecraft.displayWidth,
            minecraft.displayHeight,
            renderer.getMapType().dimension);
    }
}
