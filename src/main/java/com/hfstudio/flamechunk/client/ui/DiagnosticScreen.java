package com.hfstudio.flamechunk.client.ui;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

import com.hfstudio.flamechunk.FlameChunk;
import com.hfstudio.flamechunk.client.render.ColorCalculator;
import com.hfstudio.flamechunk.client.render.RenderViewport;
import com.hfstudio.flamechunk.client.storage.ClientSnapshotStorage;
import com.hfstudio.flamechunk.common.config.ServerConfig;
import com.hfstudio.flamechunk.common.data.ChunkSnapshot;
import com.hfstudio.flamechunk.common.data.DimensionSnapshot;
import com.hfstudio.flamechunk.common.data.ScanSnapshot;
import com.hfstudio.flamechunk.common.network.packet.ClearSnapshotPacket;
import com.hfstudio.flamechunk.common.network.packet.ScanRequestPacket;

public class DiagnosticScreen extends GuiScreen {

    private final ClientSnapshotStorage storage;
    private final ColorCalculator colors = new ColorCalculator();
    private final RenderViewport viewport = new RenderViewport();

    public DiagnosticScreen(ClientSnapshotStorage storage) {
        this.storage = storage;
    }

    @Override
    public void initGui() {
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
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        updateButtons();
        String title = StatCollector.translateToLocal("flamechunk.client.title");
        drawCenteredString(fontRendererObj, title, width / 2, 16, 0xFFFFFF);
        if (storage.isScanning()) {
            int progress = Math.round(storage.getProgress() * 100.0F);
            drawString(
                fontRendererObj,
                StatCollector.translateToLocalFormatted("flamechunk.client.progress", progress),
                16,
                42,
                0xE5E7EB);
        }
        ScanSnapshot snapshot = storage.getSnapshot();
        if (snapshot == null) {
            drawString(fontRendererObj, StatCollector.translateToLocal("flamechunk.client.empty"), 16, 58, 0xE5E7EB);
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
            58,
            0xE5E7EB);
        drawDimension(snapshot);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            requestScan();
        } else if (button.id == 1) {
            FlameChunk.proxy.handleClear(new ClearSnapshotPacket());
        }
    }

    private void requestScan() {
        if (storage.isScanning() || FlameChunk.network == null) {
            return;
        }
        try {
            FlameChunk.network.sendToServer(new ScanRequestPacket(ServerConfig.scanSeconds));
        } catch (RuntimeException exception) {
            FlameChunk.LOG.warn("Unable to request a FlameChunk scan", exception);
        }
    }

    private void updateButtons() {
        if (buttonList.size() < 2) {
            return;
        }
        buttonList.get(0).enabled = !storage.isScanning();
        buttonList.get(1).enabled = storage.isScanning() || storage.getSnapshot() != null;
    }

    private void drawDimension(ScanSnapshot snapshot) {
        Minecraft minecraft = Minecraft.getMinecraft();
        int dimensionId = minecraft.theWorld == null ? 0 : minecraft.theWorld.provider.dimensionId;
        DimensionSnapshot dimension = findDimension(snapshot, dimensionId);
        if (dimension == null) {
            drawString(
                fontRendererObj,
                StatCollector.translateToLocal("flamechunk.client.dimension_empty"),
                16,
                74,
                0xE5E7EB);
            return;
        }
        int centerX = minecraft.thePlayer == null ? 0 : minecraft.thePlayer.chunkCoordX;
        int centerZ = minecraft.thePlayer == null ? 0 : minecraft.thePlayer.chunkCoordZ;
        int minX = centerX - 8;
        int maxX = centerX + 8;
        int minZ = centerZ - 5;
        int maxZ = centerZ + 5;
        List<ChunkSnapshot> chunks = dimension.getChunks();
        int row = 74;
        for (ChunkSnapshot chunk : chunks) {
            if (!viewport.contains(chunk, minX, minZ, maxX, maxZ) || row > height - 16) {
                continue;
            }
            float mspt = calculateMspt(chunk, snapshot.getSampledTicks());
            int color = colors.colorForMspt(mspt, 50.0F);
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

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
