package com.hfstudio.flamechunk.server.command;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;

import com.hfstudio.flamechunk.common.network.PeerChannels;

public class ServerMessages {

    public static IChatComponent translated(ICommandSender receiver, String key, Object... arguments) {
        if (receiver instanceof EntityPlayerMP player && PeerChannels.canSend(player)) {
            return new ChatComponentTranslation(key, arguments);
        }
        return new ChatComponentText(StatCollector.translateToLocalFormatted(key, arguments));
    }

    public static void send(ICommandSender receiver, String key, Object... arguments) {
        if (receiver != null) {
            receiver.addChatMessage(translated(receiver, key, arguments));
        }
    }
}
