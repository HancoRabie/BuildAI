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
    /** Pure server-security check: no other project's scripts, no builder role. */
    public static boolean mayApprove(UUID actingPlayer,UUID projectOwner,
                                     BuildAIPermissions.Role role){
        return actingPlayer.equals(projectOwner)&&
            role==BuildAIPermissions.Role.ADMIN;
    }
    public static boolean canApprove(ServerPlayerEntity player,UUID owner){
        return mayApprove(player.getUuid(),owner,BuildAIPermissions.role(player));
    }
    public static String explanation(ServerPlayerEntity player){
        return "Command-block scripting requires the project owner to have BuildAI "+
            "admin rights (singleplayer host or multiplayer OP4). "+
            "Ordinary BuildAI builder grants cannot activate server commands.";
    }
}
