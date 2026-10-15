package com.hanco.buildai.build;

import net.minecraft.server.network.ServerPlayerEntity;
import java.util.UUID;

/** Only project owner AND BuildAI ADMIN can activate server-command scripts.
 * A cheat-disabled integrated-host is an administrator for BuildAI, but
 * cannot magically bypass the world's vanilla command-block engine.
 * BuildAI builder grants never confer vanilla OP or command execution.
 */
public final class CommandAuthorization {
    private CommandAuthorization(){}
    public static boolean canApprove(ServerPlayerEntity player,UUID owner){
        return player.getUuid().equals(owner)&&BuildAIPermissions.canAdmin(player);
    }
    public static String explanation(ServerPlayerEntity player){
        return "Command-block scripting requires the project owner to have BuildAI "+
            "admin rights (singleplayer host or multiplayer OP4). "+
            "Ordinary BuildAI builder grants cannot activate server commands.";
    }
}
