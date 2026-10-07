package com.hfstudio.flamechunk.mixins.late.xaero;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.StatCollector;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hfstudio.flamechunk.client.integration.MapControlIds;
import com.hfstudio.flamechunk.client.integration.MapOverlayControls;
import com.hfstudio.flamechunk.client.integration.MapScanProgressRenderer;
import com.hfstudio.flamechunk.client.integration.NavigatorMapBridge;
import com.hfstudio.flamechunk.client.integration.XaeroOverlayRenderer;
import com.hfstudio.flamechunk.common.data.WeakChunkSnapshot.EntityTypeCount;

import xaero.map.gui.GuiDropDown;
import xaero.map.gui.GuiMap;
import xaero.map.gui.RightClickOption;
import xaero.map.gui.ScreenBase;

@Mixin(value = GuiMap.class, remap = false)
public abstract class MixinGuiMap extends ScreenBase {

    @Unique
    private static final int FLAMECHUNK_CONTROL_X = 4;
    @Unique
    private static final int FLAMECHUNK_SCAN_Y = 34;
    @Unique
    private static final int FLAMECHUNK_CLEAR_Y = 56;

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

    @Shadow(remap = false)
    public abstract void addGuiButton(GuiButton b);

    @Inject(method = "initGui", at = @At("RETURN"), remap = true)
    private void flamechunk$addControls(CallbackInfo callbackInfo) {
        addGuiButton(
            new GuiButton(
                MapControlIds.XAERO_SCAN,
                FLAMECHUNK_CONTROL_X,
                FLAMECHUNK_SCAN_Y,
                80,
                20,
                StatCollector.translateToLocal("flamechunk.client.scan")));
        addGuiButton(
            new GuiButton(
                MapControlIds.XAERO_CLEAR,
                FLAMECHUNK_CONTROL_X,
                FLAMECHUNK_CLEAR_Y,
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
            MapOverlayControls.toggleScan();
        } else if (button.id == MapControlIds.XAERO_CLEAR) {
            MapOverlayControls.clear();
        }
    }

    @Inject(method = "drawScreen", at = @At("HEAD"), remap = true)
    private void flamechunk$updateScanControl(int scaledMouseX, int scaledMouseY, float partialTicks,
        CallbackInfo callbackInfo) {
        MapOverlayControls.updateScanButton(buttonList, MapControlIds.XAERO_SCAN);
    }

    @Inject(
        method = "drawScreen",
        at = @At(
            value = "INVOKE",
            target = "Lxaero/map/gui/ScreenBase;drawScreen(IIF)V",
            shift = At.Shift.BEFORE,
            remap = false),
        remap = true)
    private void flamechunk$renderHeatmap(int scaledMouseX, int scaledMouseY, float partialTicks,
        CallbackInfo callbackInfo) {
        boolean navigatorOwnsLayer = NavigatorMapBridge.ownsXaeroWorldMap();
        int tooltipMouseX = scaledMouseX;
        int tooltipMouseY = scaledMouseY;
        for (GuiButton button : buttonList) {
            if (button.visible && tooltipMouseX >= button.xPosition
                && tooltipMouseY >= button.yPosition
                && tooltipMouseX < button.xPosition + button.width
                && tooltipMouseY < button.yPosition + button.height) {
                tooltipMouseX = -1;
                tooltipMouseY = -1;
                break;
            }
        }
        if (tooltipMouseX >= 0 && waypointFilterField != null
            && tooltipMouseX >= waypointFilterField.xPosition
            && tooltipMouseY >= waypointFilterField.yPosition
            && tooltipMouseX < waypointFilterField.xPosition + waypointFilterField.width
            && tooltipMouseY < waypointFilterField.yPosition + waypointFilterField.height) {
            tooltipMouseX = -1;
            tooltipMouseY = -1;
        }
        if (tooltipMouseX >= 0 && dropdowns != null) {
            int screenHeight = this.height;
            for (GuiDropDown dropdown : dropdowns) {
                if (dropdown != null && dropdown.onDropDown(tooltipMouseX, tooltipMouseY, screenHeight)) {
                    tooltipMouseX = -1;
                    tooltipMouseY = -1;
                    break;
                }
            }
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        GuiScreen screen = minecraft.currentScreen;
        if (screen != null && minecraft.theWorld != null) {
            int dimensionId = lastViewedDimensionId == null ? minecraft.theWorld.provider.dimensionId
                : lastViewedDimensionId;
            if (navigatorOwnsLayer) {
                if (NavigatorMapBridge.isXaeroWorldMapLayerActive()) {
                    XaeroOverlayRenderer.renderNavigatorSupplements(
                        cameraX,
                        cameraZ,
                        scale,
                        tooltipMouseX,
                        tooltipMouseY,
                        screen.width,
                        screen.height,
                        dimensionId);
                }
            } else {
                XaeroOverlayRenderer.render(
                    cameraX,
                    cameraZ,
                    scale,
                    tooltipMouseX,
                    tooltipMouseY,
                    screen.width,
                    screen.height,
                    dimensionId);
            }
        }
    }

    @Inject(method = "drawScreen", at = @At("RETURN"), remap = true)
    private void flamechunk$renderScanProgress(int scaledMouseX, int scaledMouseY, float partialTicks,
        CallbackInfo callbackInfo) {
        MapScanProgressRenderer
            .render(FLAMECHUNK_CONTROL_X, FLAMECHUNK_CLEAR_Y + 22, FLAMECHUNK_CONTROL_X, FLAMECHUNK_SCAN_Y, 80, 20);
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
