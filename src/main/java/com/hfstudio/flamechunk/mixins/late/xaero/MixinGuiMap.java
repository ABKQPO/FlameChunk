package com.hfstudio.flamechunk.mixins.late.xaero;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hfstudio.flamechunk.client.integration.MapOverlayControls;
import com.hfstudio.flamechunk.client.integration.XaeroOverlayRenderer;
import com.hfstudio.flamechunk.common.data.WeakChunkSnapshot.EntityTypeCount;

import xaero.map.gui.GuiMap;
import xaero.map.gui.RightClickOption;

@Mixin(value = GuiMap.class, remap = false)
public abstract class MixinGuiMap {

    @Unique
    private static final int FLAMECHUNK_SCAN_BUTTON = 0x465301;
    @Unique
    private static final int FLAMECHUNK_CLEAR_BUTTON = 0x465302;

    @Shadow(remap = false)
    private double cameraX;
    @Shadow(remap = false)
    private double cameraZ;
    @Shadow(remap = false)
    private double scale;

    @Shadow(remap = false)
    public abstract void addGuiButton(GuiButton b);

    @Accessor(value = "rightClickX", remap = false)
    public abstract int flamechunk$getRightClickX();

    @Accessor(value = "rightClickZ", remap = false)
    public abstract int flamechunk$getRightClickZ();

    @Accessor(value = "lastViewedDimensionId", remap = false)
    public abstract Integer flamechunk$getLastViewedDimensionId();

    @Inject(method = "initGui", at = @At("RETURN"), remap = true)
    private void flamechunk$addControls(CallbackInfo callbackInfo) {
        addGuiButton(
            new GuiButton(
                FLAMECHUNK_SCAN_BUTTON,
                4,
                4,
                80,
                20,
                StatCollector.translateToLocal("flamechunk.client.scan")));
        addGuiButton(
            new GuiButton(
                FLAMECHUNK_CLEAR_BUTTON,
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
        if (button.id == FLAMECHUNK_SCAN_BUTTON) {
            MapOverlayControls.requestScan();
        } else if (button.id == FLAMECHUNK_CLEAR_BUTTON) {
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
        Integer viewedDimensionId = flamechunk$getLastViewedDimensionId();
        if (viewedDimensionId == null || viewedDimensionId != dimensionId) {
            return;
        }
        int chunkX = flamechunk$getRightClickX() >> 4;
        int chunkZ = flamechunk$getRightClickZ() >> 4;
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
