package com.hfstudio.flamechunk.client.ui;

import java.util.List;

import com.hfstudio.flamechunk.client.render.ColorCalculator;
import com.hfstudio.flamechunk.client.render.RenderViewport;
import com.hfstudio.flamechunk.client.storage.ClientSnapshotStorage;
import com.hfstudio.flamechunk.common.data.ChunkSnapshot;
import com.hfstudio.flamechunk.common.data.DimensionSnapshot;
import com.hfstudio.flamechunk.common.data.ScanSnapshot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.StatCollector;

public class DiagnosticScreen extends GuiScreen {

    private final ClientSnapshotStorage storage;
    private final ColorCalculator colors = new ColorCalculator();
    private final RenderViewport viewport = new RenderViewport();

    public DiagnosticScreen(ClientSnapshotStorage storage) {
        this.storage = storage;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        String title = StatCollector.translateToLocal("flamechunk.client.title");
        drawCenteredString(fontRendererObj, title, width / 2, 16, 0xFFFFFF);
        if (storage.isScanning()) {
            int progress = Math.round(storage.getProgress() * 100.0F);
            drawString(fontRendererObj, StatCollector.translateToLocalFormatted("flamechunk.client.progress", progress),
                    16, 42, 0xE5E7EB);
        }
        ScanSnapshot snapshot = storage.getSnapshot();
        if (snapshot == null) {
            drawString(fontRendererObj, StatCollector.translateToLocal("flamechunk.client.empty"), 16, 58, 0xE5E7EB);
            super.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }
        drawString(fontRendererObj,
                StatCollector.translateToLocalFormatted("flamechunk.client.summary", snapshot.getDurationSeconds(),
                        snapshot.getChunkCount()),
                16, 58, 0xE5E7EB);
        drawDimension(snapshot);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawDimension(ScanSnapshot snapshot) {
        Minecraft minecraft = Minecraft.getMinecraft();
        int dimensionId = minecraft.theWorld == null ? 0 : minecraft.theWorld.provider.dimensionId;
        DimensionSnapshot dimension = findDimension(snapshot, dimensionId);
        if (dimension == null) {
            drawString(fontRendererObj, StatCollector.translateToLocal("flamechunk.client.dimension_empty"), 16, 74,
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
            float mspt = chunk.getNanos()[0] / 1000000.0F / Math.max(1L, snapshot.getSampledTicks());
            int color = colors.colorForMspt(mspt, 50.0F);
            String line = StatCollector.translateToLocalFormatted("flamechunk.client.chunk", chunk.getChunkX(),
                    chunk.getChunkZ(), mspt);
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

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
