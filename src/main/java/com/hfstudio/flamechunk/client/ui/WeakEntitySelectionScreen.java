package com.hfstudio.flamechunk.client.ui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

import com.hfstudio.flamechunk.client.integration.MapOverlayControls;
import com.hfstudio.flamechunk.client.render.ColorUtils;
import com.hfstudio.flamechunk.common.data.WeakChunkSnapshot.EntityTypeCount;

public class WeakEntitySelectionScreen extends GuiScreen {

    public static final int PAGE_SIZE = 8;
    public static final int PREVIOUS_BUTTON = -1;
    public static final int NEXT_BUTTON = -2;
    public static final int CANCEL_BUTTON = -3;
    public static final int LOADER_CONTROL_BUTTON = -4;

    public final GuiScreen parent;
    public final int dimensionId;
    public final int chunkX;
    public final int chunkZ;
    public final List<EntityTypeCount> entityTypes;
    public final boolean showLoaderControl;
    public int page;

    public WeakEntitySelectionScreen(GuiScreen parent, int dimensionId, int chunkX, int chunkZ,
        List<EntityTypeCount> entityTypes) {
        this(parent, dimensionId, chunkX, chunkZ, entityTypes, false);
    }

    public WeakEntitySelectionScreen(GuiScreen parent, int dimensionId, int chunkX, int chunkZ,
        List<EntityTypeCount> entityTypes, boolean showLoaderControl) {
        this.parent = parent;
        this.dimensionId = dimensionId;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.entityTypes = new ArrayList<>(entityTypes);
        this.showLoaderControl = showLoaderControl;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int pageSize = showLoaderControl ? 6 : PAGE_SIZE;
        int start = page * pageSize;
        int end = Math.min(entityTypes.size(), start + pageSize);
        int width = Math.min(260, this.width - 20);
        int left = (this.width - width) / 2;
        for (int index = start; index < end; index++) {
            EntityTypeCount entityType = entityTypes.get(index);
            buttonList.add(
                new GuiButton(
                    index,
                    left,
                    42 + (index - start) * 21,
                    width,
                    20,
                    StatCollector.translateToLocalFormatted(
                        "flamechunk.client.map.weakclear.type",
                        displayType(entityType.getTypeId()),
                        entityType.getCount())));
        }
        if (page > 0) {
            buttonList.add(
                new GuiButton(
                    PREVIOUS_BUTTON,
                    left,
                    this.height - 28,
                    72,
                    20,
                    StatCollector.translateToLocal("flamechunk.client.map.previous")));
        }
        if (end < entityTypes.size()) {
            buttonList.add(
                new GuiButton(
                    NEXT_BUTTON,
                    left + 76,
                    this.height - 28,
                    72,
                    20,
                    StatCollector.translateToLocal("flamechunk.client.map.next")));
        }
        buttonList.add(
            new GuiButton(
                CANCEL_BUTTON,
                this.width - 76,
                this.height - 28,
                72,
                20,
                StatCollector.translateToLocal("flamechunk.client.map.cancel")));
        if (showLoaderControl) {
            buttonList.add(
                new GuiButton(
                    LOADER_CONTROL_BUTTON,
                    (this.width - 160) / 2,
                    this.height - 50,
                    160,
                    20,
                    StatCollector.translateToLocal("flamechunk.client.map.loader.toggle")));
        }
    }

    @Override
    public void actionPerformed(GuiButton button) {
        if (button.id == PREVIOUS_BUTTON) {
            page--;
            initGui();
        } else if (button.id == NEXT_BUTTON) {
            page++;
            initGui();
        } else if (button.id == CANCEL_BUTTON) {
            mc.displayGuiScreen(parent);
        } else if (button.id == LOADER_CONTROL_BUTTON) {
            MapOverlayControls.confirmLoaderToggle(parent, dimensionId, chunkX, chunkZ);
        } else if (button.id >= 0 && button.id < entityTypes.size()) {
            EntityTypeCount entityType = entityTypes.get(button.id);
            MapOverlayControls.confirmWeakClear(parent, dimensionId, chunkX, chunkZ, entityType.getTypeId());
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(
            fontRendererObj,
            StatCollector.translateToLocal("flamechunk.client.map.weakclear.select"),
            this.width / 2,
            18,
            ColorUtils.TEXT_PRIMARY.getColor());
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    public static String displayType(String typeId) {
        return typeId.length() <= 30 ? typeId : typeId.substring(0, 27) + "...";
    }
}
