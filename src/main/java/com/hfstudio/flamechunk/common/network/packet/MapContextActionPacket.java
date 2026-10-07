package com.hfstudio.flamechunk.common.network.packet;

import java.nio.charset.StandardCharsets;

import com.hfstudio.flamechunk.common.data.WeakChunkSnapshot;
import com.hfstudio.flamechunk.common.network.NetworkHandler;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import lombok.Getter;

@Getter
public class MapContextActionPacket implements IMessage {

    public static final int WEAK_ENTITY_CLEAR = 1;
    public static final int LOADER_TOGGLE = 2;

    public int action;
    public int dimensionId;
    public int chunkX;
    public int chunkZ;
    public String entityType;
    public boolean valid;

    public MapContextActionPacket() {}

    public MapContextActionPacket(int action, int dimensionId, int chunkX, int chunkZ, String entityType) {
        if (!isValidAction(action, entityType)) {
            throw new IllegalArgumentException("Invalid map context action");
        }
        this.action = action;
        this.dimensionId = dimensionId;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.entityType = entityType;
        this.valid = true;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        valid = false;
        if (buffer.readableBytes() < 18
            || buffer.readableBytes() > 4 + 1 + 12 + 1 + WeakChunkSnapshot.MAX_TYPE_ID_LENGTH
            || buffer.readInt() != NetworkHandler.PROTOCOL_MAGIC) {
            return;
        }
        int readAction = buffer.readUnsignedByte();
        int readDimension = buffer.readInt();
        int readChunkX = buffer.readInt();
        int readChunkZ = buffer.readInt();
        int typeLength = buffer.readUnsignedByte();
        if (typeLength > WeakChunkSnapshot.MAX_TYPE_ID_LENGTH || buffer.readableBytes() != typeLength) {
            return;
        }
        byte[] encodedType = new byte[typeLength];
        buffer.readBytes(encodedType);
        String readType = new String(encodedType, StandardCharsets.UTF_8);
        if (!isValidAction(readAction, readType)) {
            return;
        }
        action = readAction;
        dimensionId = readDimension;
        chunkX = readChunkX;
        chunkZ = readChunkZ;
        entityType = readType;
        valid = true;
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (!valid) {
            throw new IllegalStateException("Map context action is unavailable");
        }
        byte[] encodedType = entityType.getBytes(StandardCharsets.UTF_8);
        buffer.writeInt(NetworkHandler.PROTOCOL_MAGIC);
        buffer.writeByte(action);
        buffer.writeInt(dimensionId);
        buffer.writeInt(chunkX);
        buffer.writeInt(chunkZ);
        buffer.writeByte(encodedType.length);
        buffer.writeBytes(encodedType);
    }

    public static boolean isValidAction(int action, String entityType) {
        if (entityType == null) {
            return false;
        }
        byte[] encodedType = entityType.getBytes(StandardCharsets.UTF_8);
        if (encodedType.length > WeakChunkSnapshot.MAX_TYPE_ID_LENGTH) {
            return false;
        }
        if (action == WEAK_ENTITY_CLEAR) {
            return encodedType.length > 0;
        }
        return action == LOADER_TOGGLE && encodedType.length == 0;
    }
}
