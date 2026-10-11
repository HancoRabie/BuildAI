package com.hanco.buildai.build;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.PaneBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Restores naturally-connected pane shapes after force-state schematic placement.
 *
 * Does not "place" an item, change the pane block ID, or touch other world
 * positions. Uses the pane's own vanilla neighbor-state logic (including
 * glass panes, stained panes, and iron bars).
 */
public final class PaneNormalizer {
    private PaneNormalizer() {}

    private static final Direction[] SIDES = {
        Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    /**
     * Manual repair for existing builds from older mod versions that no longer
     * have an in-memory ConstructionProject after relaunch. Server op only.
     */
    public static int normalizeNearby(ServerWorld world, BlockPos center) {
        final int horizontal=20, vertical=14;
        int repaired=0;
        for(int dy=-vertical;dy<=vertical;dy++)
            for(int dz=-horizontal;dz<=horizontal;dz++)
                for(int dx=-horizontal;dx<=horizontal;dx++) {
                    BlockPos pos=center.add(dx,dy,dz);
                    if(!world.isInBuildLimit(pos)||!world.isChunkLoaded(pos))continue;
                    if(normalize(world,pos))repaired++;
                }
        return repaired;
    }
    public static boolean normalize(ServerWorld world, BlockPos pos) {
        if (!world.isChunkLoaded(pos)) return false;
        BlockState before = world.getBlockState(pos);
        if (!(before.getBlock() instanceof PaneBlock)) return false;
        BlockState next = before;
        for (Direction side : SIDES) {
            BlockPos adjacent = pos.offset(side);
            if (!world.isChunkLoaded(adjacent)) return false;
            next = next.getStateForNeighborUpdate(side, world.getBlockState(adjacent),
                world, pos, adjacent);
        }
        // An unexpected replacement is not part of connection normalization.
        if (next.getBlock() != before.getBlock() || next.equals(before)) return false;
        world.setBlockState(pos, next, Block.NOTIFY_ALL);
        return true;
    }
}
