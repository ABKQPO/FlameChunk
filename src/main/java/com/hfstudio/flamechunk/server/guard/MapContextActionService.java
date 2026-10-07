package com.hfstudio.flamechunk.server.guard;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import com.hfstudio.flamechunk.FlameChunk;
import com.hfstudio.flamechunk.common.config.ServerConfig;
import com.hfstudio.flamechunk.common.network.packet.MapContextActionPacket;
import com.hfstudio.flamechunk.server.command.ServerMessages;
import com.hfstudio.flamechunk.server.sampler.LoaderTicketControlService;

public class MapContextActionService {

    public static void process(EntityPlayerMP player, MapContextActionPacket request) {
        if (player == null || request == null
            || !request.isValid()
            || player.isDead
            || player.worldObj == null
            || player.worldObj.provider.dimensionId != request.getDimensionId()) {
            return;
        }
        if ((ServerConfig.requireOperator && !player.canCommandSenderUseCommand(2, "flamechunk"))
            || FlameChunk.serverUtilities == null
            || !FlameChunk.serverUtilities.hasPermission(player, permission(request.getAction()))) {
            ServerMessages.send(player, "flamechunk.command.denied");
            return;
        }
        if (request.getAction() == MapContextActionPacket.WEAK_ENTITY_CLEAR) {
            submitWeakClear(player, request);
        } else if (request.getAction() == MapContextActionPacket.LOADER_TOGGLE) {
            toggleLoader(player.worldObj, request.getChunkX(), request.getChunkZ(), player);
        }
    }

    private static void submitWeakClear(EntityPlayerMP player, MapContextActionPacket request) {
        WeakChunkClearService service = FlameChunk.instance == null ? null
            : FlameChunk.instance.getWeakChunkClearService();
        if (service == null) {
            return;
        }
        int result = service.submit(player, request.getChunkX(), request.getChunkZ(), request.getEntityType());
        if (result == WeakChunkClearService.ACCEPTED) {
            ServerMessages.send(
                player,
                "flamechunk.command.weakclear.queued",
                request.getChunkX(),
                request.getChunkZ(),
                request.getEntityType());
        } else if (result == WeakChunkClearService.STALE_TARGET) {
            ServerMessages.send(player, "flamechunk.command.weakclear.stale", 0, request.getEntityType());
        } else if (result == WeakChunkClearService.ALREADY_QUEUED) {
            ServerMessages.send(player, "flamechunk.command.weakclear.already_queued");
        } else if (result == WeakChunkClearService.QUEUE_FULL) {
            ServerMessages.send(player, "flamechunk.command.weakclear.queue_full");
        } else {
            ServerMessages.send(player, "flamechunk.command.denied");
        }
    }

    private static void toggleLoader(World world, int chunkX, int chunkZ, EntityPlayerMP player) {
        int result = LoaderTicketControlService.toggle(world, chunkX, chunkZ);
        if (result == Integer.MIN_VALUE) {
            ServerMessages.send(player, "flamechunk.command.loader.limit");
            return;
        }
        String key = result > 0 ? "flamechunk.command.loader.frozen"
            : result < 0 ? "flamechunk.command.loader.unfrozen" : "flamechunk.command.loader.missing";
        int affected = result < 0 ? -result - 1 : result;
        ServerMessages.send(player, key, chunkX, chunkZ, affected);
    }

    private static String permission(int action) {
        return action == MapContextActionPacket.WEAK_ENTITY_CLEAR ? "flamechunk.weakclear" : "flamechunk.loadercontrol";
    }
}
