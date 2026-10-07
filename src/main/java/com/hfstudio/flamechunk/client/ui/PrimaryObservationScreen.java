package com.hfstudio.flamechunk.client.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

import com.hfstudio.flamechunk.FlameChunk;
import com.hfstudio.flamechunk.client.render.ColorUtils;
import com.hfstudio.flamechunk.common.data.ObservationSnapshot;
import com.hfstudio.flamechunk.common.data.ObservationSnapshot.Entry;
import com.hfstudio.flamechunk.common.data.ObservationSnapshot.StackDetail;
import com.hfstudio.flamechunk.common.network.packet.UnknownStackDetailsPacket;
import com.hfstudio.flamechunk.common.network.packet.UnknownStackDetailsRequestPacket;
import com.hfstudio.flamechunk.common.tick.TickCategory;

public class PrimaryObservationScreen extends GuiScreen {

    public static final AtomicLong REQUEST_IDS = new AtomicLong();
    public final GuiScreen parent;
    public final ObservationSnapshot snapshot;
    public final long reportId;
    public List<StackDetail> unknownStacks;
    public long pendingRequestId;
    public long requestDeadlineNanos;
    public int requestStatus = -1;
    public final List<Entry> rows = new ArrayList<>();
    public int page;
    public int sortMode;
    public int categoryIndex = TickCategory.COUNT;

    public PrimaryObservationScreen(GuiScreen parent, ObservationSnapshot snapshot, long reportId) {
        this.parent = parent;
        this.snapshot = snapshot;
        this.reportId = reportId;
        this.unknownStacks = snapshot.unknownStacks();
        refreshRows();
    }

    public PrimaryObservationScreen(GuiScreen parent, ObservationSnapshot snapshot) {
        this(parent, snapshot, 0L);
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
        GuiButton copyStacks = new GuiButton(5, 124, height - 28, 120, 20, unknownStackButtonLabel());
        copyStacks.enabled = !unknownStacks.isEmpty() || hasUnknownWork() && reportId > 0L && pendingRequestId == 0L;
        buttonList.add(copyStacks);
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
            case 5 -> requestOrCopyUnknownStacks(button);
            default -> {}
        }
    }

    public String unknownStackText() {
        StringBuilder text = new StringBuilder("FlameChunk TASK/Unknown stack details");
        for (StackDetail detail : unknownStacks) {
            text.append("\n\n")
                .append(detail.samples())
                .append(" samples: ")
                .append(detail.anchor());
            for (String frame : detail.frames()) {
                text.append("\n  at ")
                    .append(frame);
            }
        }
        return text.toString();
    }

    public boolean hasUnknownWork() {
        for (Entry entry : snapshot.entries()) {
            if (entry.category() == TickCategory.TASK && "Unknown".equals(entry.typeName())) {
                return true;
            }
        }
        return false;
    }

    public String unknownStackButtonLabel() {
        if (!unknownStacks.isEmpty()) {
            return StatCollector.translateToLocal("flamechunk.client.unknownStack.copy");
        }
        if (pendingRequestId != 0L) {
            return StatCollector.translateToLocal("flamechunk.client.unknownStack.fetching");
        }
        if (requestStatus >= 0) {
            return StatCollector.translateToLocal("flamechunk.client.unknownStack.status." + requestStatus);
        }
        return StatCollector.translateToLocal("flamechunk.client.unknownStack.request");
    }

    public void requestOrCopyUnknownStacks(GuiButton button) {
        if (!unknownStacks.isEmpty()) {
            GuiScreen.setClipboardString(unknownStackText());
            requestStatus = -1;
            button.displayString = StatCollector.translateToLocal("flamechunk.client.unknownStack.copied");
            return;
        }
        if (reportId <= 0L || pendingRequestId != 0L) {
            return;
        }
        long nextRequestId = REQUEST_IDS.incrementAndGet();
        if (nextRequestId <= 0L) {
            REQUEST_IDS.set(1L);
            nextRequestId = 1L;
        }
        pendingRequestId = nextRequestId;
        requestDeadlineNanos = System.nanoTime() + 10_000_000_000L;
        requestStatus = -1;
        button.displayString = unknownStackButtonLabel();
        FlameChunk.network.sendToServer(new UnknownStackDetailsRequestPacket(nextRequestId, reportId));
    }

    public void handleUnknownStackDetails(UnknownStackDetailsPacket packet) {
        if (packet.getRequestId() != pendingRequestId || packet.getReportId() != reportId) {
            return;
        }
        pendingRequestId = 0L;
        requestDeadlineNanos = 0L;
        requestStatus = packet.getStatus();
        if (packet.getStatus() == UnknownStackDetailsPacket.OK) {
            unknownStacks = packet.getDetails();
            GuiScreen.setClipboardString(unknownStackText());
            requestStatus = -1;
        }
        for (GuiButton button : buttonList) {
            if (button.id == 5) {
                button.displayString = packet.getStatus() == UnknownStackDetailsPacket.OK
                    ? StatCollector.translateToLocal("flamechunk.client.unknownStack.copied")
                    : unknownStackButtonLabel();
                button.enabled = !unknownStacks.isEmpty()
                    || hasUnknownWork() && reportId > 0L && pendingRequestId == 0L;
                break;
            }
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
        expireUnknownStackRequest();
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

    public void expireUnknownStackRequest() {
        if (pendingRequestId == 0L || System.nanoTime() < requestDeadlineNanos) {
            return;
        }
        pendingRequestId = 0L;
        requestDeadlineNanos = 0L;
        requestStatus = 5;
        for (GuiButton button : buttonList) {
            if (button.id == 5) {
                button.displayString = unknownStackButtonLabel();
                button.enabled = hasUnknownWork() && reportId > 0L;
                break;
            }
        }
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
