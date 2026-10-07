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

    private static final int PAGE_SIZE = 8;
    private static final int PREVIOUS_BUTTON = -1;
    private static final int NEXT_BUTTON = -2;
    private static final int CANCEL_BUTTON = -3;

    private final GuiScreen parent;
    private final int dimensionId;
    private final int chunkX;
    private final int chunkZ;
    private final List<EntityTypeCount> entityTypes;
    private int page;

    public WeakEntitySelectionScreen(GuiScreen parent, int dimensionId, int chunkX, int chunkZ,
        List<EntityTypeCount> entityTypes) {
        this.parent = parent;
        this.dimensionId = dimensionId;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.entityTypes = new ArrayList<>(entityTypes);
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int start = page * PAGE_SIZE;
        int end = Math.min(entityTypes.size(), start + PAGE_SIZE);
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
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == PREVIOUS_BUTTON) {
            page--;
            initGui();
        } else if (button.id == NEXT_BUTTON) {
            page++;
            initGui();
        } else if (button.id == CANCEL_BUTTON) {
            mc.displayGuiScreen(parent);
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

    private static String displayType(String typeId) {
        return typeId.length() <= 30 ? typeId : typeId.substring(0, 27) + "...";
    }
}
