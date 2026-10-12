package com.hanco.buildai.build;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Restores naturally connected fence, wall, pane, and iron-bar states after FORCE_STATE placement.
 *
 * Does not "place" an item, change the pane block ID, or touch other world
 * positions. Uses each block's own vanilla neighbor-state logic (including
 * wooden fences, fence gates' neighbors, walls, stained panes, and iron bars).
 */
public final class PaneNormalizer {
    private PaneNormalizer() {}

    /**
     * Manual repair for existing builds from older mod versions that no longer
     * have an in-memory ConstructionProject after relaunch. Server op only.
     * Signal refresh can pulse already-programmed existing redstone devices;
     * run this only on a test site or after disabling live machinery.
     */
    public static int normalizeNearby(ServerWorld world, BlockPos center) {
        final int horizontal=20, vertical=14;
        int repaired=0;
        java.util.List<BlockPos> dust=new java.util.ArrayList<>();
        java.util.List<BlockPos> signals=new java.util.ArrayList<>();
        for(int dy=-vertical;dy<=vertical;dy++)
            for(int dz=-horizontal;dz<=horizontal;dz++)
                for(int dx=-horizontal;dx<=horizontal;dx++) {
                    BlockPos pos=center.add(dx,dy,dz);
                    if(!world.isInBuildLimit(pos)||!world.isChunkLoaded(pos))continue;
                    if(normalize(world,pos))repaired++;
                    BlockState state=world.getBlockState(pos);
                    if(RedstoneNormalizer.isDust(state))dust.add(pos);
                    if(RedstoneNormalizer.isSignalDevice(state))signals.add(pos);
                }
        // Match automatic construction: repair all wire arms first, and
        // only then refresh native redstone neighbor/power propagation.
        for(BlockPos pos:dust)
            if(RedstoneNormalizer.normalizeDust(world,pos))repaired++;
        for(BlockPos pos:signals)RedstoneNormalizer.refreshSignal(world,pos);
        return repaired;
    }
    public static boolean normalize(ServerWorld world, BlockPos pos) {
        if (!world.isChunkLoaded(pos)) return false;
        BlockState before = world.getBlockState(pos);
        if (!(before.getBlock() instanceof PaneBlock ||
              before.getBlock() instanceof FenceBlock ||
              before.getBlock() instanceof WallBlock)) return false;
        BlockState next = before;
        // Walls also depend on the block above for low/tall/post shapes.
        // Use all six directions for native block-state recomputation.
        for (Direction side : Direction.values()) {
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
