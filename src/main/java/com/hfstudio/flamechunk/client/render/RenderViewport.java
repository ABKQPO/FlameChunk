package com.hfstudio.flamechunk.client.render;

import com.hfstudio.flamechunk.common.data.ChunkSnapshot;

public class RenderViewport {

    public boolean contains(ChunkSnapshot chunk, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        return chunk != null && chunk.getChunkX() >= minChunkX
            && chunk.getChunkX() <= maxChunkX
            && chunk.getChunkZ() >= minChunkZ
            && chunk.getChunkZ() <= maxChunkZ;
    }
}
