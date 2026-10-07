package com.hfstudio.flamechunk.client.integration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.hfstudio.flamechunk.client.render.ColorCalculator;
import com.hfstudio.flamechunk.common.data.ChunkSnapshot;
import com.hfstudio.flamechunk.common.data.DimensionSnapshot;
import com.hfstudio.flamechunk.common.data.ScanSnapshot;

import lombok.Getter;

@Getter
public class MapOverlayModel {

    private static final float DEFAULT_BUDGET_MSPT = 50.0F;
    private static final ColorCalculator COLOR_CALCULATOR = new ColorCalculator();

    private final List<MapOverlayCell> cells;

    public MapOverlayModel(List<MapOverlayCell> cells) {
        if (cells == null) {
            throw new IllegalArgumentException("Overlay cells must not be null");
        }
        this.cells = List.copyOf(cells);
    }

    public static MapOverlayModel from(ScanSnapshot snapshot) {
        if (snapshot == null) {
            return new MapOverlayModel(Collections.emptyList());
        }
        List<MapOverlayCell> cells = new ArrayList<>();
        long sampledTicks = Math.max(1L, snapshot.getSampledTicks());
        for (DimensionSnapshot dimension : snapshot.getDimensions()) {
            for (ChunkSnapshot chunk : dimension.getChunks()) {
                float mspt = chunk.calculateMspt(sampledTicks);
                int color = COLOR_CALCULATOR.colorForMspt(mspt, DEFAULT_BUDGET_MSPT);
                float opacity = 0.25F + 0.65F * COLOR_CALCULATOR.normalize(mspt, DEFAULT_BUDGET_MSPT);
                cells.add(
                    new MapOverlayCell(
                        dimension.getDimensionId(),
                        chunk.getChunkX(),
                        chunk.getChunkZ(),
                        color,
                        opacity,
                        String.format(Locale.ENGLISH, "%.3f ms/t", mspt)));
            }
        }
        return new MapOverlayModel(cells);
    }

}
