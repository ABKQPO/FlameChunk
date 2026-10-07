package com.hfstudio.flamechunk.server.sampler;

import java.util.Locale;
import java.util.Map;

import net.minecraftforge.common.ForgeChunkManager;

import com.hfstudio.flamechunk.common.integration.Mods;
import com.hfstudio.flamechunk.server.integration.ServerUtilitiesBridge;

public class LoaderTicketSource {

    public static final String SERVER_UTILITIES_TEAM_KEY = "Team";

    public static int code(ForgeChunkManager.Ticket ticket) {
        if (ticket.isPlayerTicket()) {
            return 2;
        }
        return ticket.getEntity() == null ? 4 : 3;
    }

    public static String describe(ForgeChunkManager.Ticket ticket, ServerUtilitiesBridge serverUtilities) {
        return describe(ticket, serverUtilities, null);
    }

    public static String describe(ForgeChunkManager.Ticket ticket, ServerUtilitiesBridge serverUtilities,
        Map<String, String> teamNames) {
        if (ticket.isPlayerTicket()) {
            String player = ticket.getPlayerName();
            return player == null || player.isEmpty() ? "player" : "player:" + player;
        }
        if (ticket.getEntity() != null) {
            return "entity:" + ticket.getEntity()
                .getClass()
                .getSimpleName();
        }
        String modId = ticket.getModId();
        String type = ticket.getType() == null ? "unknown"
            : ticket.getType()
                .name()
                .toLowerCase(Locale.ENGLISH);
        if (Mods.ServerUtilities.modid.equals(modId) && Mods.ServerUtilities.isModLoaded()
            && serverUtilities != null
            && serverUtilities.isAvailable()) {
            String teamId = ticket.getModData()
                .getString(SERVER_UTILITIES_TEAM_KEY);
            String teamName = teamNames != null && teamNames.containsKey(teamId) ? teamNames.get(teamId)
                : serverUtilities.describeTeam(teamId);
            if (teamNames != null) {
                teamNames.put(teamId, teamName);
            }
            if (teamName != null && !teamName.isEmpty()) {
                return trim(modId + ":" + teamName + " [" + teamId + "]");
            }
        }
        return trim((modId == null || modId.isEmpty() ? "unknown" : modId) + ":" + type);
    }

    public static String trim(String value) {
        return value.length() <= 64 ? value : value.substring(0, 64);
    }
}
