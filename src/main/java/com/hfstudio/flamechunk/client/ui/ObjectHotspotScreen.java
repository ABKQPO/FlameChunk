package com.hfstudio.flamechunk.client.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

import com.hfstudio.flamechunk.client.render.ColorUtils;
import com.hfstudio.flamechunk.common.data.DimensionSnapshot;
import com.hfstudio.flamechunk.common.data.ObjectHotspot;

public class ObjectHotspotScreen extends GuiScreen {

    public final GuiScreen parent;
    public final int dimensionId;
    public final long sampledTicks;
    public final List<ObjectHotspot> hotspots;
    public int page;
    public int sortMode;
    public ObjectHotspot selected;

    public ObjectHotspotScreen(GuiScreen parent, DimensionSnapshot dimension, long sampledTicks) {
        this.parent = parent;
        this.dimensionId = dimension.getDimensionId();
        this.sampledTicks = sampledTicks;
        this.hotspots = new ArrayList<>(dimension.objectHotspots);
    }

    @Override
    public void initGui() {
        buttonList.add(
            new GuiButton(0, 8, height - 28, 64, 20, StatCollector.translateToLocal("flamechunk.client.detail.back")));
        buttonList.add(new GuiButton(1, width - 64, 8, 24, 20, "<"));
        buttonList.add(new GuiButton(2, width - 32, 8, 24, 20, ">"));
        buttonList.add(
            new GuiButton(
                3,
                width - 104,
                height - 28,
                96,
                20,
                StatCollector.translateToLocal("flamechunk.client.teleport")));
        buttonList.add(new GuiButton(4, 8, 8, 96, 20, sortLabel()));
    }

    @Override
    public void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 0 -> mc.displayGuiScreen(parent);
            case 1 -> page = Math.max(0, page - 1);
            case 2 -> page++;
            case 3 -> {
                if (selected != null && mc.thePlayer != null
                    && mc.theWorld != null
                    && mc.theWorld.provider.dimensionId == dimensionId) {
                    mc.thePlayer
                        .sendChatMessage("/tp " + (selected.x + 0.5D) + " " + selected.y + " " + (selected.z + 0.5D));
                    mc.displayGuiScreen(null);
                }
            }
            case 4 -> {
                sortMode = (sortMode + 1) % 3;
                Comparator<ObjectHotspot> comparator = switch (sortMode) {
                    case 1 -> Comparator.comparingLong(value -> value.peakNanos);
                    case 2 -> Comparator.comparingInt(value -> value.count);
                    default -> Comparator.comparingLong(value -> value.nanos);
                };
                hotspots.sort(comparator.reversed());
                page = 0;
                button.displayString = sortLabel();
            }
            default -> {}
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(
            fontRendererObj,
            StatCollector.translateToLocal("flamechunk.client.objects"),
            width / 2,
            34,
            ColorUtils.TEXT_PRIMARY.getColor());
        int first = page * pageSize();
        int last = Math.min(hotspots.size(), first + pageSize());
        for (int index = first; index < last; index++) {
            ObjectHotspot hotspot = hotspots.get(index);
            int y = 54 + (index - first) * 28;
            if (selected == hotspot) {
                drawRect(6, y - 2, width - 6, y + 24, ColorUtils.SELECTION_BACKGROUND.getColor());
            }
            String category = StatCollector.translateToLocal(
                "flamechunk.category." + hotspot.category.name()
                    .toLowerCase(Locale.ENGLISH));
            String title = category + " | "
                + hotspot.typeName
                + " | "
                + hotspot.x
                + ", "
                + hotspot.y
                + ", "
                + hotspot.z;
            drawString(
                fontRendererObj,
                fontRendererObj.trimStringToWidth(title, width - 16),
                8,
                y,
                ColorUtils.TEXT_PRIMARY.getColor());
            String metrics = StatCollector.translateToLocalFormatted(
                "flamechunk.client.objectMetrics",
                hotspot.calculateMspt(sampledTicks),
                hotspot.peakNanos / 1000000.0D,
                hotspot.count);
            drawString(
                fontRendererObj,
                fontRendererObj.trimStringToWidth(metrics, width - 16),
                8,
                y + 12,
                ColorUtils.TEXT_MUTED.getColor());
        }
        if (hotspots.isEmpty()) {
            drawString(
                fontRendererObj,
                StatCollector.translateToLocal("flamechunk.client.empty"),
                8,
                54,
                ColorUtils.TEXT_MUTED.getColor());
        }
        buttonList.get(1).enabled = page > 0;
        buttonList.get(2).enabled = last < hotspots.size();
        buttonList.get(3).enabled = selected != null && mc.theWorld != null
            && mc.theWorld.provider.dimensionId == dimensionId;
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0 && mouseX >= 8 && mouseX < width - 8 && mouseY >= 54 && mouseY < height - 36) {
            int row = (mouseY - 54) / 28;
            int index = page * pageSize() + row;
            if (row < pageSize() && index < hotspots.size()) {
                selected = hotspots.get(index);
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    public int pageSize() {
        return Math.max(1, (height - 94) / 28);
    }

    public String sortLabel() {
        return StatCollector.translateToLocal("flamechunk.client.objectSort." + sortMode);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
