package com.hanco.buildai.build;

import net.minecraft.server.network.ServerPlayerEntity;
import java.util.UUID;

/**
 * Server-side authorization for privileged command-block programming.
 * Dedicated/shared servers require OP4. The single user of an integrated
 * singleplayer world may use Minecraft's "Allow Cheats" OP2 permission,
 * but that exception cannot apply when others join via LAN.
 */
public final class CommandAuthorization {
    private CommandAuthorization() {}
    public static boolean canApprove(ServerPlayerEntity player,UUID owner) {
        if(!player.getUuid().equals(owner))return false;
        if(player.hasPermissionLevel(4))return true;
        return !player.getServer().isDedicated() &&
            player.getServer().getPlayerManager().getCurrentPlayerCount()==1 &&
            player.hasPermissionLevel(2);
    }
    public static String explanation(ServerPlayerEntity player) {
        return "Permission denied: dedicated/LAN servers require operator level 4. "+
            "In a singleplayer world, enable cheats (OP2) and keep LAN closed. "+
            "Current op2="+player.hasPermissionLevel(2)+
            ", op4="+player.hasPermissionLevel(4)+
            ", dedicated="+player.getServer().isDedicated()+".";
    }
}
