package com.hfstudio.flamechunk.client.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

import com.hfstudio.flamechunk.client.render.ColorUtils;
import com.hfstudio.flamechunk.common.data.ObservationSnapshot;
import com.hfstudio.flamechunk.common.data.ObservationSnapshot.Entry;
import com.hfstudio.flamechunk.common.tick.TickCategory;

public class PrimaryObservationScreen extends GuiScreen {

    public final GuiScreen parent;
    public final ObservationSnapshot snapshot;
    public final List<Entry> rows = new ArrayList<>();
    public int page;
    public int sortMode;
    public int categoryIndex = TickCategory.COUNT;

    public PrimaryObservationScreen(GuiScreen parent, ObservationSnapshot snapshot) {
        this.parent = parent;
        this.snapshot = snapshot;
        refreshRows();
    }

    @Override
    public void initGui() {
        buttonList.clear();
        buttonList.add(
            new GuiButton(0, 8, height - 28, 76, 20, StatCollector.translateToLocal("flamechunk.client.detail.back")));
        buttonList.add(new GuiButton(1, width - 64, height - 28, 24, 20, "<"));
        buttonList.add(new GuiButton(2, width - 32, height - 28, 24, 20, ">"));
        buttonList.add(new GuiButton(3, 8, 30, 112, 20, sortLabel()));
        buttonList.add(new GuiButton(4, width - 120, 30, 112, 20, categoryLabel()));
    }

    @Override
    public void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 0 -> mc.displayGuiScreen(parent);
            case 1 -> page = Math.max(0, page - 1);
            case 2 -> page++;
            case 3 -> {
                sortMode = (sortMode + 1) % 3;
                refreshRows();
                button.displayString = sortLabel();
            }
            case 4 -> {
                categoryIndex = (categoryIndex + 1) % (TickCategory.COUNT + 1);
                refreshRows();
                button.displayString = categoryLabel();
            }
            default -> {}
        }
    }

    public void refreshRows() {
        rows.clear();
        for (Entry entry : snapshot.entries()) {
            if (categoryIndex == TickCategory.COUNT || entry.category()
                .ordinal() == categoryIndex) {
                rows.add(entry);
            }
        }
        Comparator<Entry> order = switch (sortMode) {
            case 1 -> Comparator.comparingLong(Entry::peakNanos);
            case 2 -> Comparator.comparingLong(Entry::samples);
            default -> Comparator.comparingLong(Entry::nanos);
        };
        rows.sort(
            order.reversed()
                .thenComparing(Entry::typeName)
                .thenComparingInt(Entry::dimensionId));
        page = 0;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(
            fontRendererObj,
            StatCollector.translateToLocal("flamechunk.client.observations"),
            width / 2,
            12,
            ColorUtils.TEXT_PRIMARY.getColor());
        String summary = StatCollector.translateToLocalFormatted(
            "flamechunk.command.report.observations",
            snapshot.averageMspt(),
            snapshot.peakTickNanos() / 1000000.0D,
            snapshot.sampleAttempts(),
            snapshot.completedTicks());
        drawString(
            fontRendererObj,
            fontRendererObj.trimStringToWidth(summary, width - 16),
            8,
            56,
            ColorUtils.TEXT_SECONDARY.getColor());
        if (!snapshot.degradationReason()
            .isEmpty()) {
            drawString(
                fontRendererObj,
                fontRendererObj
                    .trimStringToWidth(StatCollector.translateToLocal(snapshot.degradationReason()), width - 16),
                8,
                68,
                ColorUtils.TEXT_WARNING.getColor());
        }
        int first = page * pageSize();
        int last = Math.min(rows.size(), first + pageSize());
        for (int index = first; index < last; index++) {
            Entry entry = rows.get(index);
            int y = 86 + (index - first) * 28;
            String title = StatCollector.translateToLocal(
                "flamechunk.category." + entry.category()
                    .name()
                    .toLowerCase(Locale.ENGLISH))
                + " | "
                + entry.typeName()
                + " | "
                + entry.dimensionId();
            drawString(
                fontRendererObj,
                fontRendererObj.trimStringToWidth(title, width - 16),
                8,
                y,
                ColorUtils.TEXT_PRIMARY.getColor());
            String metrics = StatCollector.translateToLocalFormatted(
                "flamechunk.client.observationMetrics",
                entry.nanos() / 1000000.0D / Math.max(1L, snapshot.completedTicks()),
                entry.peakNanos() / 1000000.0D,
                entry.samples());
            drawString(
                fontRendererObj,
                fontRendererObj.trimStringToWidth(metrics, width - 16),
                8,
                y + 12,
                ColorUtils.TEXT_MUTED.getColor());
        }
        buttonList.get(1).enabled = page > 0;
        buttonList.get(2).enabled = last < rows.size();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    public int pageSize() {
        return Math.max(1, (height - 122) / 28);
    }

    public String sortLabel() {
        return StatCollector.translateToLocal("flamechunk.client.objectSort." + sortMode);
    }

    public String categoryLabel() {
        return StatCollector.translateToLocal(
            categoryIndex == TickCategory.COUNT ? "flamechunk.client.total"
                : "flamechunk.category." + TickCategory.values()[categoryIndex].name()
                    .toLowerCase(Locale.ENGLISH));
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
