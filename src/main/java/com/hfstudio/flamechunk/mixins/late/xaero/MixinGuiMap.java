package com.hfstudio.flamechunk.mixins.late.xaero;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hfstudio.flamechunk.client.integration.MapControlIds;
import com.hfstudio.flamechunk.client.integration.MapOverlayControls;
import com.hfstudio.flamechunk.client.integration.XaeroOverlayRenderer;
import com.hfstudio.flamechunk.common.data.WeakChunkSnapshot.EntityTypeCount;

import xaero.map.gui.GuiMap;
import xaero.map.gui.RightClickOption;

@Mixin(value = GuiMap.class, remap = false)
public abstract class MixinGuiMap {

    @Shadow(remap = false)
    private double cameraX;

    @Shadow(remap = false)
    private double cameraZ;

    @Shadow(remap = false)
    private double scale;

    @Shadow(remap = false)
    private Integer lastViewedDimensionId;

    @Shadow(remap = false)
    private int rightClickX;

    @Shadow(remap = false)
    private int rightClickZ;

    @Shadow(remap = false)
    public abstract void addGuiButton(GuiButton b);

    @Inject(method = "initGui", at = @At("RETURN"), remap = true)
    private void flamechunk$addControls(CallbackInfo callbackInfo) {
        addGuiButton(
            new GuiButton(
                MapControlIds.XAERO_SCAN,
                4,
                4,
                80,
                20,
                StatCollector.translateToLocal("flamechunk.client.scan")));
        addGuiButton(
            new GuiButton(
                MapControlIds.XAERO_CLEAR,
                86,
                4,
                80,
                20,
                StatCollector.translateToLocal("flamechunk.client.clear")));
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), remap = true)
    private void flamechunk$handleControl(GuiButton button, CallbackInfo callbackInfo) {
        if (button == null) {
            return;
        }
        if (button.id == MapControlIds.XAERO_SCAN) {
            MapOverlayControls.requestScan();
        } else if (button.id == MapControlIds.XAERO_CLEAR) {
            MapOverlayControls.clear();
        }
    }

    @Inject(method = "drawScreen", at = @At("RETURN"), remap = true)
    private void flamechunk$renderHeatmap(int scaledMouseX, int scaledMouseY, float partialTicks,
        CallbackInfo callbackInfo) {
        XaeroOverlayRenderer.render(cameraX, cameraZ, scale, scaledMouseX, scaledMouseY);
        XaeroOverlayRenderer.renderScanProgress();
    }

    @Inject(method = "getRightClickOptions", at = @At("RETURN"), remap = false)
    private void flamechunk$addContextActions(CallbackInfoReturnable<ArrayList<RightClickOption>> callbackInfo) {

        if (callbackInfo.getReturnValue() == null || Minecraft.getMinecraft().theWorld == null) {
            return;
        }

        GuiMap map = (GuiMap) (Object) this;
        if (!map.isRightClickValid()) {
            return;
        }

        int dimensionId = Minecraft.getMinecraft().theWorld.provider.dimensionId;
        Integer viewedDimensionId = lastViewedDimensionId;

        if (viewedDimensionId == null || viewedDimensionId != dimensionId) {
            return;
        }

        int chunkX = rightClickX >> 4;
        int chunkZ = rightClickZ >> 4;

        ArrayList<RightClickOption> options = callbackInfo.getReturnValue();
        List<EntityTypeCount> types = MapOverlayControls.weakClearTargets(dimensionId, chunkX, chunkZ);

        for (final EntityTypeCount type : types) {
            options.add(new RightClickOption("flamechunk.client.map.weakclear.type", options.size(), map) {

                @Override
                public void onAction(GuiScreen screen) {
                    MapOverlayControls.confirmWeakClear(screen, dimensionId, chunkX, chunkZ, type.getTypeId());
                }
            }.setNameFormatArgs(type.getTypeId(), type.getCount()));
        }

        if (MapOverlayControls.hasLoaderControlTarget(dimensionId, chunkX, chunkZ)) {
            options.add(new RightClickOption("flamechunk.client.map.loader.toggle", options.size(), map) {

                @Override
                public void onAction(GuiScreen screen) {
                    MapOverlayControls.confirmLoaderToggle(screen, dimensionId, chunkX, chunkZ);
                }
            });
        }
    }
}
