package com.hfstudio.flamechunk.mixins.late.xaero;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hfstudio.flamechunk.client.integration.NavigatorMapBridge;
import com.hfstudio.flamechunk.client.integration.XaeroOverlayRenderer;

import xaero.common.IXaeroMinimap;
import xaero.common.XaeroMinimapSession;
import xaero.common.minimap.MinimapProcessor;
import xaero.common.minimap.render.MinimapRenderer;
import xaero.common.settings.ModSettings;

@Mixin(value = MinimapRenderer.class, remap = false)
public abstract class MixinMinimapRenderer {

    @Shadow
    protected Minecraft mc;

    @Shadow
    protected IXaeroMinimap modMain;

    @Shadow
    protected double zoom;

    @Shadow
    public abstract double getRenderAngle(boolean lockedNorth);

    @Inject(
        method = "renderMinimap",
        at = @At(
            value = "INVOKE",
            target = "Lxaero/common/minimap/waypoints/render/WaypointsGuiRenderer;render(Lxaero/common/XaeroMinimapSession;Lxaero/common/minimap/render/MinimapRendererHelper;DDIIDDFDZFZ)V"),
        remap = false)
    private void flamechunk$renderHeatmap(XaeroMinimapSession minimapSession, MinimapProcessor minimap, int x, int y,
        int width, int height, int scale, int size, float partial, CallbackInfo callbackInfo) {
        if (mc.currentScreen != null || NavigatorMapBridge.ownsXaeroMinimap()
            || mc.theWorld == null
            || mc.renderViewEntity == null) {
            return;
        }
        ModSettings settings = modMain.getSettings();
        int minimapSize = minimap.getMinimapSize(size);
        float minimapScale = settings.getMinimapScale(size);
        if (minimapScale <= 0.0F || minimapSize <= 0 || zoom <= 0.0D) {
            return;
        }
        boolean lockNorth = settings.getLockNorth(minimapSize / 2, settings.minimapShape);
        XaeroOverlayRenderer.renderMinimapInXaeroMatrix(
            minimap.mainPlayerX,
            minimap.mainPlayerZ,
            zoom * minimapScale / 2.0D,
            minimapSize,
            getRenderAngle(lockNorth));
    }
}
