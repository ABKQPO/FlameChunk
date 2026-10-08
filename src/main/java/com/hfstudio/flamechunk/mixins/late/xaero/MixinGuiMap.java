package com.hfstudio.flamechunk.mixins.late.xaero;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hfstudio.flamechunk.client.integration.MapOverlayControls;
import com.hfstudio.flamechunk.client.integration.NavigatorMapBridge;
import com.hfstudio.flamechunk.client.integration.XaeroOverlayRenderer;
import com.hfstudio.flamechunk.client.integration.XaeroRightClickOption;
import com.hfstudio.flamechunk.common.data.WeakChunkSnapshot.EntityTypeCount;

import xaero.map.gui.GuiDropDown;
import xaero.map.gui.GuiMap;
import xaero.map.gui.RightClickOption;
import xaero.map.gui.ScreenBase;

@Mixin(value = GuiMap.class, remap = false)
public abstract class MixinGuiMap extends ScreenBase {

    @Shadow(remap = false)
    private List<GuiDropDown> dropdowns;

    @Shadow(remap = false)
    private GuiTextField waypointFilterField;

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

    protected MixinGuiMap(GuiScreen parent, GuiScreen escape) {
        super(parent, escape);
    }

    @Inject(
        method = "drawScreen",
        at = @At(
            value = "INVOKE",
            target = "Lxaero/map/mods/SupportMods;minimap()Z",
            ordinal = 1,
            shift = At.Shift.BEFORE,
            remap = false),
        remap = true)
    private void flamechunk$renderHeatmap(int scaledMouseX, int scaledMouseY, float partialTicks,
        CallbackInfo callbackInfo) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.currentScreen == null || minecraft.theWorld == null) {
            return;
        }
        int dimensionId = lastViewedDimensionId == null ? minecraft.theWorld.provider.dimensionId
            : lastViewedDimensionId;
        if (NavigatorMapBridge.ownsXaeroWorldMap() && !NavigatorMapBridge.isXaeroWorldMapLayerActive()) {
            return;
        }
        if (!NavigatorMapBridge.ownsXaeroWorldMap() || !NavigatorMapBridge.hasXaeroWorldMapRenderSteps()) {
            XaeroOverlayRenderer
                .renderWorldMap(cameraX, cameraZ, scale, dimensionId, minecraft.displayWidth, minecraft.displayHeight);
        }
    }

    @Inject(method = "drawScreen", at = @At("RETURN"), remap = true)
    private void flamechunk$renderTooltip(int scaledMouseX, int scaledMouseY, float partialTicks,
        CallbackInfo callbackInfo) {
        if (NavigatorMapBridge.ownsXaeroWorldMap() && NavigatorMapBridge.isXaeroWorldMapLayerActive()
            && NavigatorMapBridge.hasXaeroWorldMapRenderSteps()) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.currentScreen == null || minecraft.theWorld == null
            || flamechunk$isUiHovered(scaledMouseX, scaledMouseY)) {
            return;
        }
        int dimensionId = lastViewedDimensionId == null ? minecraft.theWorld.provider.dimensionId
            : lastViewedDimensionId;
        int displayMouseX = scaledMouseX * minecraft.displayWidth / Math.max(1, width);
        int displayMouseY = scaledMouseY * minecraft.displayHeight / Math.max(1, height);
        XaeroOverlayRenderer.renderWorldMapTooltip(
            cameraX,
            cameraZ,
            scale,
            dimensionId,
            displayMouseX,
            displayMouseY,
            scaledMouseX,
            scaledMouseY,
            width,
            height,
            minecraft.displayWidth,
            minecraft.displayHeight);
    }

    @Unique
    private boolean flamechunk$isUiHovered(int mouseX, int mouseY) {
        for (GuiButton button : buttonList) {
            if (button.visible && mouseX >= button.xPosition
                && mouseY >= button.yPosition
                && mouseX < button.xPosition + button.width
                && mouseY < button.yPosition + button.height) {
                return true;
            }
        }
        if (waypointFilterField != null && mouseX >= waypointFilterField.xPosition
            && mouseY >= waypointFilterField.yPosition
            && mouseX < waypointFilterField.xPosition + waypointFilterField.width
            && mouseY < waypointFilterField.yPosition + waypointFilterField.height) {
            return true;
        }
        if (dropdowns != null) {
            for (GuiDropDown dropdown : dropdowns) {
                if (dropdown != null && dropdown.onDropDown(mouseX, mouseY, height)) {
                    return true;
                }
            }
        }
        return false;
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
        if (lastViewedDimensionId == null || lastViewedDimensionId != dimensionId) {
            return;
        }
        int chunkX = rightClickX >> 4;
        int chunkZ = rightClickZ >> 4;
        ArrayList<RightClickOption> options = callbackInfo.getReturnValue();
        options.add(
            new XaeroRightClickOption(
                "flamechunk.client.map.scan",
                options.size(),
                map,
                XaeroRightClickOption.ACTION_SCAN,
                dimensionId,
                0,
                0,
                null));
        options.add(
            new XaeroRightClickOption(
                "flamechunk.client.map.clear",
                options.size(),
                map,
                XaeroRightClickOption.ACTION_CLEAR,
                dimensionId,
                0,
                0,
                null));
        List<EntityTypeCount> types = MapOverlayControls.weakClearTargets(dimensionId, chunkX, chunkZ);
        for (EntityTypeCount type : types) {
            options.add(
                new XaeroRightClickOption(
                    "flamechunk.client.map.weakclear.type",
                    options.size(),
                    map,
                    XaeroRightClickOption.ACTION_WEAK_CLEAR,
                    dimensionId,
                    chunkX,
                    chunkZ,
                    type.getTypeId()).setNameFormatArgs(type.getTypeId(), type.getCount()));
        }
        if (MapOverlayControls.hasLoaderControlTarget(dimensionId, chunkX, chunkZ)) {
            options.add(
                new XaeroRightClickOption(
                    "flamechunk.client.map.loader.toggle",
                    options.size(),
                    map,
                    XaeroRightClickOption.ACTION_LOADER,
                    dimensionId,
                    chunkX,
                    chunkZ,
                    null));
        }
    }
}
