package com.hfstudio.flamechunk.common.network.packet;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.hfstudio.flamechunk.common.config.ServerConfig;
import com.hfstudio.flamechunk.common.data.WeakChunkSnapshot;
import com.hfstudio.flamechunk.common.data.WeakChunkSnapshot.ChunkEntry;
import com.hfstudio.flamechunk.common.data.WeakChunkSnapshot.EntityTypeCount;
import com.hfstudio.flamechunk.common.network.NetworkHandler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import lombok.Getter;

@Getter
public class WeakChunkSnapshotPacket implements IMessage {

    private WeakChunkSnapshot snapshot;
    private boolean valid = true;

    public WeakChunkSnapshotPacket() {}

    public WeakChunkSnapshotPacket(WeakChunkSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Weak chunk snapshot must not be null");
        }
        this.snapshot = snapshot;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        valid = false;
        if (buffer.readableBytes() < 18 || buffer.readableBytes() > ServerConfig.maxPacketBytes
            || buffer.readInt() != NetworkHandler.PROTOCOL_MAGIC) {
            return;
        }
        int dimensionId = buffer.readInt();
        long generatedAtTick = buffer.readLong();
        int truncatedValue = buffer.readUnsignedByte();
        int chunkCount = buffer.readUnsignedByte();
        if (generatedAtTick < 0L || truncatedValue > 1 || chunkCount > WeakChunkSnapshot.MAX_CHUNKS) {
            return;
        }
        List<ChunkEntry> chunks = new ArrayList<>(chunkCount);
        try {
            for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
                if (buffer.readableBytes() < 13) {
                    return;
                }
                int chunkX = buffer.readInt();
                int chunkZ = buffer.readInt();
                int entityCount = buffer.readInt();
                int typeCount = buffer.readUnsignedByte();
                if (entityCount < 1 || typeCount > WeakChunkSnapshot.MAX_ENTITY_TYPES) {
                    return;
                }
                List<EntityTypeCount> entityTypes = new ArrayList<>(typeCount);
                for (int typeIndex = 0; typeIndex < typeCount; typeIndex++) {
                    if (buffer.readableBytes() < 2) {
                        return;
                    }
                    int length = buffer.readUnsignedShort();
                    if (length < 1 || length > WeakChunkSnapshot.MAX_TYPE_ID_LENGTH
                        || buffer.readableBytes() < length + 4) {
                        return;
                    }
                    byte[] encodedType = new byte[length];
                    buffer.readBytes(encodedType);
                    int count = buffer.readInt();
                    entityTypes.add(new EntityTypeCount(new String(encodedType, StandardCharsets.UTF_8), count));
                }
                chunks.add(new ChunkEntry(chunkX, chunkZ, entityCount, entityTypes));
            }
            if (buffer.isReadable()) {
                return;
            }
            snapshot = new WeakChunkSnapshot(dimensionId, generatedAtTick, truncatedValue != 0, chunks);
            valid = true;
        } catch (IllegalArgumentException exception) {
            snapshot = null;
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (snapshot == null) {
            throw new IllegalStateException("Weak chunk snapshot is unavailable");
        }
        int startIndex = buffer.writerIndex();
        buffer.writeInt(NetworkHandler.PROTOCOL_MAGIC);
        buffer.writeInt(snapshot.getDimensionId());
        buffer.writeLong(snapshot.getGeneratedAtTick());
        buffer.writeByte(snapshot.isTruncated() ? 1 : 0);
        buffer.writeByte(
            snapshot.getChunks()
                .size());
        for (ChunkEntry chunk : snapshot.getChunks()) {
            buffer.writeInt(chunk.getChunkX());
            buffer.writeInt(chunk.getChunkZ());
            buffer.writeInt(chunk.getEntityCount());
            buffer.writeByte(
                chunk.getEntityTypes()
                    .size());
            for (EntityTypeCount entityType : chunk.getEntityTypes()) {
                byte[] encodedType = entityType.getTypeId()
                    .getBytes(StandardCharsets.UTF_8);
                if (encodedType.length < 1 || encodedType.length > WeakChunkSnapshot.MAX_TYPE_ID_LENGTH
                    || buffer.writerIndex() - startIndex + encodedType.length + 6 > ServerConfig.maxPacketBytes) {
                    throw new IllegalArgumentException("Weak chunk payload exceeds configured packet limit");
                }
                buffer.writeShort(encodedType.length);
                buffer.writeBytes(encodedType);
                buffer.writeInt(entityType.getCount());
            }
        }
        if (buffer.writerIndex() - startIndex > ServerConfig.maxPacketBytes) {
            throw new IllegalArgumentException("Weak chunk payload exceeds configured packet limit");
        }
    }
}
