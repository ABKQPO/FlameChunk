package com.hfstudio.flamechunk.server.sampler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldSavedData;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

public class LoaderControlData extends WorldSavedData {

    public static final String DATA_NAME = "flamechunk_loader_control";
    private static final int MAX_FROZEN_CHUNKS = 4096;
    private static final String FROZEN_CHUNKS_TAG = "FrozenChunks";
    private static final String CHUNK_X_TAG = "X";
    private static final String CHUNK_Z_TAG = "Z";

    private final LongSet frozenChunks = new LongOpenHashSet();

    public LoaderControlData(String name) {
        super(name);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        frozenChunks.clear();
        NBTTagList chunks = tag.getTagList(FROZEN_CHUNKS_TAG, 10);
        for (int index = 0; index < chunks.tagCount(); index++) {
            if (frozenChunks.size() >= MAX_FROZEN_CHUNKS) {
                break;
            }
            NBTTagCompound chunk = chunks.getCompoundTagAt(index);
            if (!chunk.hasKey(CHUNK_X_TAG, 3) || !chunk.hasKey(CHUNK_Z_TAG, 3)) {
                continue;
            }
            frozenChunks
                .add(ChunkCoordIntPair.chunkXZ2Int(chunk.getInteger(CHUNK_X_TAG), chunk.getInteger(CHUNK_Z_TAG)));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList chunks = new NBTTagList();
        for (long key : frozenChunks) {
            NBTTagCompound chunk = new NBTTagCompound();
            chunk.setInteger(CHUNK_X_TAG, (int) key);
            chunk.setInteger(CHUNK_Z_TAG, (int) (key >> 32));
            chunks.appendTag(chunk);
        }
        tag.setTag(FROZEN_CHUNKS_TAG, chunks);
    }

    public boolean isFrozen(ChunkCoordIntPair chunk) {
        return frozenChunks.contains(key(chunk));
    }

    public boolean setFrozen(ChunkCoordIntPair chunk, boolean frozen) {
        if (frozen && !frozenChunks.contains(key(chunk)) && frozenChunks.size() >= MAX_FROZEN_CHUNKS) {
            return false;
        }
        boolean changed = frozen ? frozenChunks.add(key(chunk)) : frozenChunks.remove(key(chunk));
        if (changed) {
            markDirty();
        }
        return changed;
    }

    public List<ChunkCoordIntPair> frozenChunks() {
        List<ChunkCoordIntPair> chunks = new ArrayList<>(frozenChunks.size());
        for (long key : frozenChunks) {
            chunks.add(new ChunkCoordIntPair((int) key, (int) (key >> 32)));
        }
        chunks.sort(
            Comparator.comparingInt((ChunkCoordIntPair left) -> left.chunkXPos)
                .thenComparingInt(left -> left.chunkZPos));
        return chunks;
    }

    private static long key(ChunkCoordIntPair chunk) {
        return ChunkCoordIntPair.chunkXZ2Int(chunk.chunkXPos, chunk.chunkZPos);
    }
}
