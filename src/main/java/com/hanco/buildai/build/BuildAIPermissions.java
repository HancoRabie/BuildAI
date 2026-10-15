package com.hanco.buildai.build;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * BuildAI permissions are completely independent of Minecraft's Allow Cheats.
 *
 * SINGLEPLAYER: integrated world HOST is admin even with cheats OFF.
 * MULTIPLAYER: OP4 is admin; OP2 is a builder; explicit UUID grant is builder.
 * LAN visitors are not the integrated host and do not inherit host authority.
 *
 * Server-owned per-world grant list is persisted under <world>/buildai/access.json.
 * Grants never raise vanilla permissions or allow command-block scripting/force
 * undo. Existing project ownership remains UUID-based.
 */
public final class BuildAIPermissions {
    public enum Role { NONE, BUILDER, ADMIN }
    private static Path loadedPath;
    private static final Set<UUID> GRANTED=new LinkedHashSet<>();
    private BuildAIPermissions(){}

    /** Pure policy function, fully testable without starting Minecraft. */
    public static Role role(boolean integratedHost, boolean op2,
                            boolean op4, boolean granted){
        if(integratedHost || op4)return Role.ADMIN;
        if(op2 || granted)return Role.BUILDER;
        return Role.NONE;
    }

    private static Path path(MinecraftServer server){
        return server.getSavePath(WorldSavePath.ROOT).resolve("buildai").resolve("access.json");
    }
    private static synchronized void ensureLoaded(MinecraftServer server){
        Path file=path(server).toAbsolutePath().normalize();
        if(file.equals(loadedPath))return;
        // Always invalidate permissions on server/world change; no sharing
        // allowlists between saves in the same long-lived client process.
        GRANTED.clear();
        loadedPath=null;
        GRANTED.addAll(BuildAIAccessStore.read(file));
        loadedPath=file;
    }
    private static synchronized boolean hasGrant(MinecraftServer server,UUID id){
        ensureLoaded(server);
        return GRANTED.contains(id);
    }
    private static boolean host(ServerPlayerEntity p){
        MinecraftServer server=p.getServer();
        return !server.isDedicated() && server.isHost(p.getGameProfile());
    }
    public static Role role(ServerPlayerEntity player){
        boolean integratedHost=host(player);
        boolean op4=player.hasPermissionLevel(4);
        boolean op2=player.hasPermissionLevel(2);
        if(integratedHost||op4)return Role.ADMIN;
        if(op2)return Role.BUILDER;
        return role(false,false,false,hasGrant(player.getServer(),player.getUuid()));
    }
    public static boolean canBuild(ServerPlayerEntity player){
        return role(player)!=Role.NONE;
    }
    public static boolean canAdmin(ServerPlayerEntity player){
        return role(player)==Role.ADMIN;
    }
    public static boolean canBuild(ServerCommandSource source){
        return source.getEntity() instanceof ServerPlayerEntity player && canBuild(player);
    }
    public static boolean canAdmin(ServerCommandSource source){
        return source.getEntity() instanceof ServerPlayerEntity player && canAdmin(player);
    }

    public static synchronized boolean grant(MinecraftServer server,UUID id){
        ensureLoaded(server);
        if(!GRANTED.add(id))return false;
        try{save();return true;}catch(RuntimeException e){GRANTED.remove(id);throw e;}
    }
    public static synchronized boolean revoke(MinecraftServer server,UUID id){
        ensureLoaded(server);
        if(!GRANTED.remove(id))return false;
        try{save();return true;}catch(RuntimeException e){GRANTED.add(id);throw e;}
    }
    public static synchronized Set<UUID> grants(MinecraftServer server){
        ensureLoaded(server);
        return Set.copyOf(GRANTED);
    }
    private static void save(){
        BuildAIAccessStore.write(loadedPath,GRANTED);
    }
}
