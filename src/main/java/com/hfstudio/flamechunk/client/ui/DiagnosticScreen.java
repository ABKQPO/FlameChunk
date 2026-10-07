package com.hfstudio.flamechunk.client.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

import com.hfstudio.flamechunk.ClientProxy;
import com.hfstudio.flamechunk.FlameChunk;
import com.hfstudio.flamechunk.client.config.ClientConfig;
import com.hfstudio.flamechunk.client.integration.MapOverlayControls;
import com.hfstudio.flamechunk.client.render.ColorCalculator;
import com.hfstudio.flamechunk.client.render.ColorUtils;
import com.hfstudio.flamechunk.client.storage.ClientSnapshotStorage;
import com.hfstudio.flamechunk.common.data.ChunkSnapshot;
import com.hfstudio.flamechunk.common.data.ChunkTypeTiming;
import com.hfstudio.flamechunk.common.data.DimensionSnapshot;
import com.hfstudio.flamechunk.common.data.ScanSnapshot;
import com.hfstudio.flamechunk.common.data.WeakChunkSnapshot;
import com.hfstudio.flamechunk.common.data.WeakChunkSnapshot.ChunkEntry;
import com.hfstudio.flamechunk.common.data.WeakChunkSnapshot.EntityTypeCount;
import com.hfstudio.flamechunk.common.network.packet.ClearSnapshotPacket;
import com.hfstudio.flamechunk.common.network.packet.ScanProgressPacket;
import com.hfstudio.flamechunk.common.tick.TickCategory;

public class DiagnosticScreen extends GuiScreen {

    private final ClientSnapshotStorage storage;
    private final ColorCalculator colors = new ColorCalculator();
    private boolean showWeakChunks;
    private int pageIndex;
    private int sortCategoryIndex = TickCategory.COUNT;
    private ScanSnapshot orderedSnapshot;
    private DimensionSnapshot orderedDimension;
    private List<ChunkSnapshot> orderedChunks = Collections.emptyList();
    private ChunkSnapshot expandedChunk;

    public DiagnosticScreen(ClientSnapshotStorage storage) {
        this.storage = storage;
    }

    @Override
    public void initGui() {
        MapOverlayControls.requestWeakSnapshot();
        int buttonWidth = 90;
        int buttonY = height - 28;
        buttonList.add(
            new GuiButton(
                0,
                width / 2 - buttonWidth - 4,
                buttonY,
                buttonWidth,
                20,
                StatCollector.translateToLocal("flamechunk.client.scan")));
        buttonList.add(
            new GuiButton(
                1,
                width / 2 + 4,
                buttonY,
                buttonWidth,
                20,
                StatCollector.translateToLocal("flamechunk.client.clear")));
        buttonList.add(new GuiButton(2, 6, 6, 76, 20, StatCollector.translateToLocal("flamechunk.client.settings")));
        buttonList.add(
            new GuiButton(
                3,
                86,
                6,
                96,
                20,
                StatCollector.translateToLocal(
                    showWeakChunks ? "flamechunk.client.performance" : "flamechunk.client.weakChunks")));
        buttonList.add(new GuiButton(4, width - 64, 6, 26, 20, "<"));
        buttonList.add(new GuiButton(5, width - 34, 6, 26, 20, ">"));
        int categoryWidth = Math.min(112, Math.max(20, width - 182));
        buttonList.add(
            new GuiButton(
                6,
                86 + Math.max(0, width - 182 - categoryWidth) / 2,
                30,
                categoryWidth,
                20,
                sortCategoryLabel()));
        buttonList
            .add(new GuiButton(7, 6, 30, 76, 20, StatCollector.translateToLocal("flamechunk.client.detail.back")));
        buttonList
            .add(new GuiButton(8, width - 90, 30, 84, 20, StatCollector.translateToLocal("flamechunk.client.objects")));
        buttonList.add(
            new GuiButton(
                9,
                6,
                height - 28,
                56,
                20,
                StatCollector.translateToLocal("flamechunk.client.observationButton")));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        updateButtons();
        String title = StatCollector.translateToLocal("flamechunk.client.title");
        drawCenteredString(fontRendererObj, title, width / 2, 54, ColorUtils.TEXT_PRIMARY.getColor());
        if (storage.isScanning()) {
            int progress = Math.round(storage.getProgress() * 100.0F);
            drawString(
                fontRendererObj,
                StatCollector.translateToLocalFormatted("flamechunk.client.progress", progress),
                16,
                72,
                ColorUtils.TEXT_SECONDARY.getColor());
        } else if (storage.getStatus() >= ScanProgressPacket.QUEUED) {
            drawString(
                fontRendererObj,
                StatCollector.translateToLocal("flamechunk.client.scanStatus." + storage.getStatus()),
                16,
                72,
                ColorUtils.TEXT_SECONDARY.getColor());
        }
        ScanSnapshot snapshot = storage.getSnapshot();
        if (snapshot == null) {
            drawString(
                fontRendererObj,
                StatCollector.translateToLocal("flamechunk.client.empty"),
                16,
                106,
                ColorUtils.TEXT_SECONDARY.getColor());
            if (showWeakChunks) {
                drawWeakChunks(currentDimensionId());
            }
            super.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }
        drawString(
            fontRendererObj,
            StatCollector.translateToLocalFormatted(
                "flamechunk.client.summary",
                snapshot.getDurationSeconds(),
                snapshot.getChunkCount()),
            16,
            88,
            ColorUtils.TEXT_SECONDARY.getColor());
        if (showWeakChunks) {
            drawWeakChunks(currentDimensionId());
        } else {
            drawDimension(snapshot);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            if (storage.isScanning()) {
                MapOverlayControls.requestStop();
            } else {
                requestScan();
            }
        } else if (button.id == 1) {
            FlameChunk.proxy.handleClear(new ClearSnapshotPacket());
        } else if (button.id == 2) {
            Minecraft.getMinecraft()
                .displayGuiScreen(new ClientSettingsScreen(this));
        } else if (button.id == 3) {
            showWeakChunks = !showWeakChunks;
            pageIndex = 0;
            button.displayString = StatCollector
                .translateToLocal(showWeakChunks ? "flamechunk.client.performance" : "flamechunk.client.weakChunks");
        } else if (button.id == 4) {
            pageIndex = Math.max(0, pageIndex - 1);
        } else if (button.id == 5) {
            pageIndex++;
        } else if (button.id == 6) {
            sortCategoryIndex = (sortCategoryIndex + 1) % (TickCategory.COUNT + 1);
            pageIndex = 0;
            orderedSnapshot = null;
            button.displayString = sortCategoryLabel();
        } else if (button.id == 7) {
            expandedChunk = null;
        } else if (button.id == 8) {
            ScanSnapshot snapshot = storage.getSnapshot();
            DimensionSnapshot dimension = snapshot == null ? null : findDimension(snapshot, currentDimensionId());
            if (dimension != null) {
                mc.displayGuiScreen(new ObjectHotspotScreen(this, dimension, snapshot.getSampledTicks()));
            }
        } else if (button.id == 9 && storage.getSnapshot() != null) {
            mc.displayGuiScreen(new PrimaryObservationScreen(this, storage.getSnapshot().observations));
        }
    }

    private void requestScan() {
        MapOverlayControls.requestScan();
    }

    private void updateButtons() {
        if (buttonList.size() < 2) {
            return;
        }
        buttonList.get(0).enabled = storage.isScanning() || !storage.hasPendingScan();
        buttonList.get(0).displayString = StatCollector
            .translateToLocal(storage.isScanning() ? "flamechunk.client.stop" : "flamechunk.client.scan");
        buttonList.get(1).enabled = storage.isScanning() || storage.getSnapshot() != null;
        ScanSnapshot snapshot = storage.getSnapshot();
        DimensionSnapshot dimension = snapshot == null ? null : findDimension(snapshot, currentDimensionId());
        int pageCount = dimension == null ? 0
            : pageCount(
                dimension.getChunks()
                    .size());
        if (buttonList.size() > 7) {
            buttonList.get(4).enabled = !showWeakChunks && pageIndex > 0;
            buttonList.get(5).enabled = !showWeakChunks && pageIndex + 1 < pageCount;
            buttonList.get(6).enabled = !showWeakChunks && expandedChunk == null;
            buttonList.get(7).enabled = !showWeakChunks && expandedChunk != null;
            buttonList.get(8).enabled = !showWeakChunks && dimension != null && !dimension.objectHotspots.isEmpty();
            if (buttonList.size() > 9) {
                buttonList.get(9).enabled = snapshot != null && snapshot.observations.completedTicks() > 0;
            }
        }
    }

    private void drawDimension(ScanSnapshot snapshot) {
        int dimensionId = currentDimensionId();
        DimensionSnapshot dimension = findDimension(snapshot, dimensionId);
        if (dimension == null) {
            drawString(
                fontRendererObj,
                StatCollector.translateToLocal("flamechunk.client.dimension_empty"),
                16,
                106,
                ColorUtils.TEXT_SECONDARY.getColor());
            return;
        }
        float colorBudget = colorBudget(snapshot);
        long[] globalNanos = dimension.getGlobalNanos();
        long categoryNanos = sortCategoryIndex == TickCategory.COUNT ? totalCategoryNanos(globalNanos)
            : globalNanos[sortCategoryIndex];
        String category = sortCategoryIndex == TickCategory.COUNT ? "flamechunk.client.total"
            : "flamechunk.category." + TickCategory.values()[sortCategoryIndex].name()
                .toLowerCase(Locale.ENGLISH);
        drawString(
            fontRendererObj,
            StatCollector.translateToLocalFormatted(
                "flamechunk.client.reportCategory",
                StatCollector.translateToLocal(category),
                String.format(
                    Locale.ENGLISH,
                    "%.3f",
                    categoryNanos / 1000000.0D / Math.max(1L, snapshot.getSampledTicks()))),
            16,
            106,
            ColorUtils.TEXT_SECONDARY.getColor());
        int globalDetailCount = Math.min(
            2,
            dimension.getGlobalTypeTimings()
                .size());
        for (int index = 0; index < globalDetailCount; index++) {
            ChunkTypeTiming timing = dimension.getGlobalTypeTimings()
                .get(index);
            drawString(
                fontRendererObj,
                StatCollector.translateToLocalFormatted(
                    "flamechunk.client.detail.hotspot",
                    StatCollector.translateToLocal(
                        "flamechunk.category." + timing.getCategory()
                            .name()
                            .toLowerCase(Locale.ENGLISH)),
                    trim(timing.getTypeName(), 28),
                    timing.getNanos() / 1000000.0D / Math.max(1L, snapshot.getSampledTicks()),
                    timing.getCount()),
                16,
                122 + index * 12,
                ColorUtils.TEXT_SECONDARY.getColor());
        }
        List<ChunkSnapshot> sorted = orderedChunks(snapshot, dimension);
        if (expandedChunk != null) {
            drawChunkDetails(snapshot, expandedChunk);
            return;
        }
        int row = chunkListStart(dimension);
        int first = pageIndex * pageSize();
        int last = Math.min(sorted.size(), first + pageSize());
        for (int index = first; index < last && row <= height - 40; index++) {
            ChunkSnapshot chunk = sorted.get(index);
            float mspt = sortCategoryIndex == TickCategory.COUNT ? calculateMspt(chunk, snapshot.getSampledTicks())
                : (float) (chunk.getCategoryNanos(TickCategory.values()[sortCategoryIndex]) / 1000000.0D
                    / Math.max(1L, snapshot.getSampledTicks()));
            if (!ClientConfig.showWeakIdleChunks && chunk.isWeakLoaded() && mspt <= 0.0F) {
                continue;
            }
            int color = colors.colorForMspt(mspt, colorBudget);
            String line = StatCollector
                .translateToLocalFormatted("flamechunk.client.chunk", chunk.getChunkX(), chunk.getChunkZ(), mspt);
            drawString(fontRendererObj, line, 16, row, color);
            row += 12;
        }
    }

    private DimensionSnapshot findDimension(ScanSnapshot snapshot, int dimensionId) {
        for (DimensionSnapshot dimension : snapshot.getDimensions()) {
            if (dimension.getDimensionId() == dimensionId) {
                return dimension;
            }
        }
        return null;
    }

    private float calculateMspt(ChunkSnapshot chunk, long sampledTicks) {
        return chunk.calculateMspt(Math.max(1L, sampledTicks));
    }

    private List<ChunkSnapshot> orderedChunks(ScanSnapshot snapshot, DimensionSnapshot dimension) {
        if (snapshot != orderedSnapshot || dimension != orderedDimension) {
            orderedSnapshot = snapshot;
            orderedDimension = dimension;
            orderedChunks = new ArrayList<>(dimension.getChunks());
            orderedChunks.sort(chunkComparator());
            pageIndex = Math.min(pageIndex, Math.max(0, pageCount(orderedChunks.size()) - 1));
            expandedChunk = null;
        }
        return orderedChunks;
    }

    private void drawChunkDetails(ScanSnapshot snapshot, ChunkSnapshot chunk) {
        List<String> lines = new ArrayList<>(26);
        String source = chunk.getTicketSource()
            .length() == 0 ? "none" : chunk.getTicketSource();
        lines.add(
            StatCollector.translateToLocalFormatted(
                "flamechunk.client.detail.header",
                chunk.getChunkX(),
                chunk.getChunkZ(),
                chunk.calculateMspt(snapshot.getSampledTicks()),
                chunk.getEntityCount()));
        lines.add(StatCollector.translateToLocalFormatted("flamechunk.client.detail.source", source));
        long[] categoryNanos = chunk.getNanos();
        for (TickCategory category : TickCategory.values()) {
            long nanos = categoryNanos[category.ordinal()];
            if (nanos > 0L) {
                lines.add(
                    StatCollector.translateToLocalFormatted(
                        "flamechunk.client.detail.category",
                        StatCollector.translateToLocal(
                            "flamechunk.category." + category.name()
                                .toLowerCase(Locale.ENGLISH)),
                        nanos / 1000000.0D / Math.max(1L, snapshot.getSampledTicks())));
            }
        }
        for (ChunkTypeTiming timing : chunk.getTypeTimings()) {
            lines.add(
                StatCollector.translateToLocalFormatted(
                    "flamechunk.client.detail.hotspot",
                    StatCollector.translateToLocal(
                        "flamechunk.category." + timing.getCategory()
                            .name()
                            .toLowerCase(Locale.ENGLISH)),
                    trim(timing.getTypeName(), 28),
                    timing.getNanos() / 1000000.0D / Math.max(1L, snapshot.getSampledTicks()),
                    timing.getCount()));
            lines.add(
                StatCollector.translateToLocalFormatted("flamechunk.client.peak", timing.getPeakNanos() / 1000000.0D));
        }
        int maxRows = Math.max(1, (height - 162) / 12);
        int displayed = Math.min(lines.size(), maxRows);
        int row = 122;
        for (int index = 0; index < displayed; index++) {
            drawString(fontRendererObj, lines.get(index), 16, row, ColorUtils.TEXT_SECONDARY.getColor());
            row += 12;
        }
        if (displayed < lines.size() && row <= height - 40) {
            drawString(
                fontRendererObj,
                StatCollector.translateToLocalFormatted("flamechunk.client.detail.truncated", lines.size() - displayed),
                16,
                row,
                ColorUtils.TEXT_WARNING.getColor());
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0 && mouseY <= height - 40 && expandedChunk == null && !showWeakChunks) {
            ScanSnapshot snapshot = storage.getSnapshot();
            DimensionSnapshot dimension = snapshot == null ? null : findDimension(snapshot, currentDimensionId());
            if (snapshot != null && dimension != null) {
                int firstChunkRow = chunkListStart(dimension);
                if (mouseY < firstChunkRow) {
                    super.mouseClicked(mouseX, mouseY, mouseButton);
                    return;
                }
                List<ChunkSnapshot> sorted = orderedChunks(snapshot, dimension);
                int first = pageIndex * pageSize();
                int row = firstChunkRow;
                for (int index = first; index < sorted.size() && index < first + pageSize()
                    && row <= height - 40; index++) {
                    ChunkSnapshot chunk = sorted.get(index);
                    float mspt = sortCategoryIndex == TickCategory.COUNT
                        ? calculateMspt(chunk, snapshot.getSampledTicks())
                        : (float) (chunk.getCategoryNanos(TickCategory.values()[sortCategoryIndex]) / 1000000.0D
                            / Math.max(1L, snapshot.getSampledTicks()));
                    if (!ClientConfig.showWeakIdleChunks && chunk.isWeakLoaded() && mspt <= 0.0F) {
                        continue;
                    }
                    if (mouseY >= row && mouseY < row + 12 && mouseX >= 16 && mouseX < width - 16) {
                        expandedChunk = chunk;
                        updateButtons();
                        return;
                    }
                    row += 12;
                }
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private String trim(String value, int maximumLength) {
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength - 3) + "...";
    }

    private Comparator<ChunkSnapshot> chunkComparator() {
        return (left, right) -> {
            long leftValue = sortCategoryIndex == TickCategory.COUNT ? left.totalNanos()
                : left.getCategoryNanos(TickCategory.values()[sortCategoryIndex]);
            long rightValue = sortCategoryIndex == TickCategory.COUNT ? right.totalNanos()
                : right.getCategoryNanos(TickCategory.values()[sortCategoryIndex]);
            int costOrder = Long.compare(rightValue, leftValue);
            if (costOrder != 0) {
                return costOrder;
            }
            int xOrder = Integer.compare(left.getChunkX(), right.getChunkX());
            return xOrder != 0 ? xOrder : Integer.compare(left.getChunkZ(), right.getChunkZ());
        };
    }

    private int pageSize() {
        return Math.max(1, (height - 184) / 12);
    }

    private int chunkListStart(DimensionSnapshot dimension) {
        return 122 + Math.min(
            2,
            dimension.getGlobalTypeTimings()
                .size())
            * 12;
    }

    private int pageCount(int chunks) {
        return (chunks + pageSize() - 1) / pageSize();
    }

    private String sortCategoryLabel() {
        String label = sortCategoryIndex == TickCategory.COUNT ? "flamechunk.client.total"
            : "flamechunk.category." + TickCategory.values()[sortCategoryIndex].name()
                .toLowerCase(Locale.ENGLISH);
        return StatCollector.translateToLocalFormatted("flamechunk.client.sort", StatCollector.translateToLocal(label));
    }

    private long totalCategoryNanos(long[] values) {
        long total = 0L;
        for (int index = 0; index < values.length; index++) {
            if (index != TickCategory.BLOCK_UPDATE.ordinal()) {
                total = Long.MAX_VALUE - total < values[index] ? Long.MAX_VALUE : total + values[index];
            }
        }
        return total;
    }

    private void drawWeakChunks(int dimensionId) {
        WeakChunkSnapshot snapshot = storage.getWeakSnapshot(dimensionId);
        if (snapshot == null || snapshot.getChunks()
            .isEmpty()) {
            drawString(
                fontRendererObj,
                StatCollector.translateToLocal("flamechunk.client.weakEmpty"),
                16,
                106,
                ColorUtils.TEXT_SECONDARY.getColor());
            return;
        }
        int row = 106;
        if (snapshot.isTruncated()) {
            drawString(
                fontRendererObj,
                StatCollector.translateToLocal("flamechunk.client.weakTruncated"),
                16,
                row,
                ColorUtils.TEXT_WARNING.getColor());
            row += 12;
        }
        for (ChunkEntry chunk : snapshot.getChunks()) {
            if (row > height - 16) {
                break;
            }
            drawString(
                fontRendererObj,
                StatCollector.translateToLocalFormatted(
                    "flamechunk.client.weakChunk",
                    chunk.getChunkX(),
                    chunk.getChunkZ(),
                    chunk.getEntityCount()),
                16,
                row,
                ColorUtils.TEXT_WEAK_CHUNK.getColor());
            row += 12;
            int displayedTypes = Math.min(
                3,
                chunk.getEntityTypes()
                    .size());
            for (int index = 0; index < displayedTypes && row <= height - 16; index++) {
                EntityTypeCount type = chunk.getEntityTypes()
                    .get(index);
                String typeId = type.getTypeId();
                if (typeId.length() > 30) {
                    typeId = typeId.substring(0, 27) + "...";
                }
                drawString(
                    fontRendererObj,
                    StatCollector.translateToLocalFormatted("flamechunk.client.weakType", typeId, type.getCount()),
                    24,
                    row,
                    ColorUtils.TEXT_SECONDARY.getColor());
                row += 12;
            }
        }
    }

    private int currentDimensionId() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft.theWorld == null ? 0 : minecraft.theWorld.provider.dimensionId;
    }

    public void refreshOverlay() {
        ScanSnapshot snapshot = storage.getSnapshot();
        if (snapshot != null && FlameChunk.proxy instanceof ClientProxy clientProxy) {
            clientProxy.refreshOverlay(snapshot);
        }
    }

    private float colorBudget(ScanSnapshot snapshot) {
        if (!ClientConfig.relativeHeatColor) {
            return ClientConfig.heatThresholdMspt;
        }
        float maximum = 0.0F;
        for (DimensionSnapshot value : snapshot.getDimensions()) {
            for (ChunkSnapshot chunk : value.getChunks()) {
                maximum = Math.max(maximum, calculateMspt(chunk, snapshot.getSampledTicks()));
            }
        }
        return maximum <= 0.0F ? ClientConfig.heatThresholdMspt : maximum;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
