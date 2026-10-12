package com.hanco.buildai.build;

import com.hanco.buildai.blueprint.CommandBlockPlan;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.block.entity.CommandBlockBlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Server-only, post-normalization installer for a human-approved command plan.
 * Never receives or executes a command from chat, schematic NBT or network
 * traffic until an op-4 reviewer approves the project in the locked location.
 */
public final class CommandBlockInstaller {
    private CommandBlockInstaller(){}

    /** Populate the block entity without powering it during assembly. */
    public static void program(ServerWorld world,BlockPos pos,CommandBlockPlan.Entry entry){
        CommandBlockPlan.validateCommand(entry.command());
        if(!world.isChunkLoaded(pos))
            throw new IllegalStateException("Command block chunk unloaded at "+pos.toShortString());
        if(!(world.getBlockEntity(pos) instanceof CommandBlockBlockEntity tile))
            throw new IllegalStateException("Command block missing at "+pos.toShortString());
        if(!CommandBlockPlan.isCommandBlock(Registries.BLOCK.getId(world.getBlockState(pos).getBlock()).toString()))
            throw new IllegalStateException("Expected command block state at "+pos.toShortString());
        tile.setAuto(false);
        tile.getCommandExecutor().setCommand(entry.command());
        tile.getCommandExecutor().setTrackOutput(entry.trackOutput());
        tile.markDirty();
        world.updateListeners(pos,world.getBlockState(pos),world.getBlockState(pos),Block.NOTIFY_LISTENERS);
    }

    /** Arm only after EVERY command block has been programmed. */
    public static void arm(ServerWorld world,BlockPos pos,CommandBlockPlan.Entry entry){
        if(!(world.getBlockEntity(pos) instanceof CommandBlockBlockEntity tile))
            throw new IllegalStateException("Command block missing while arming at "+pos.toShortString());
        if(!tile.getCommandExecutor().getCommand().equals(entry.command()))
            throw new IllegalStateException("Command changed before arming at "+pos.toShortString());
        tile.setAuto(entry.auto());
        tile.markDirty();
        world.updateListeners(pos,world.getBlockState(pos),world.getBlockState(pos),Block.NOTIFY_LISTENERS);
    }
}
