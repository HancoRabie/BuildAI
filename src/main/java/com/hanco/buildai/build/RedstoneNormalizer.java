package com.hanco.buildai.build;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Set;

/**
 * Conservative vanilla 1.21.1 redstone naturalization.
 *
 * Pass 1: ask redstone dust for its native side/up/down connection geometry,
 *         just as vanilla would after neighboring pieces are placed.
 * Pass 2: notify adjacent components only after all wiring exists, so
 *         repeaters, comparators, lamps, torches, rails and other redstone
 *         listeners can schedule their own vanilla power update.
 *
 * Does NOT replace missing dust, rotate engineered repeaters/observers,
 * invent new connections, write block-entity commands, or simulate circuitry.
 */
public final class RedstoneNormalizer {
    private RedstoneNormalizer() {}

    private static final Set<String> SIGNAL_DEVICES = Set.of(
        "minecraft:redstone_wire", "minecraft:redstone_block",
        "minecraft:redstone_torch", "minecraft:redstone_wall_torch",
        "minecraft:repeater", "minecraft:comparator", "minecraft:observer",
        "minecraft:piston", "minecraft:sticky_piston", "minecraft:target",
        "minecraft:daylight_detector", "minecraft:lever",
        "minecraft:tripwire_hook", "minecraft:tripwire",
        "minecraft:dispenser", "minecraft:dropper",
        "minecraft:hopper", "minecraft:redstone_lamp",
        "minecraft:powered_rail", "minecraft:detector_rail",
        "minecraft:activator_rail", "minecraft:note_block",
        "minecraft:sculk_sensor", "minecraft:calibrated_sculk_sensor",
        "minecraft:lightning_rod"
    );

    /** Pure predicate: can be unit-tested without starting a Minecraft world. */
    public static boolean signalDeviceId(String id) {
        return SIGNAL_DEVICES.contains(id) ||
            (id.startsWith("minecraft:") &&
                (id.endsWith("_button") || id.endsWith("_pressure_plate")));
    }

    public static boolean isDust(BlockState state) {
        return state.getBlock() instanceof RedstoneWireBlock;
    }

    public static boolean isSignalDevice(BlockState state) {
        // Exclude command blocks: script activation belongs exclusively
        // to the later, operator-approved commissioning phase.
        return signalDeviceId(Registries.BLOCK.getId(state.getBlock()).toString());
    }

    /** Never read/trigger adjacent unloaded chunks in the repair pass. */
    public static boolean neighborsLoaded(ServerWorld world,BlockPos pos) {
        if(!world.isChunkLoaded(pos))return false;
        for(Direction direction:Direction.values()){
            BlockPos adjacent=pos.offset(direction);
            if(world.isInBuildLimit(adjacent)&&!world.isChunkLoaded(adjacent))
                return false;
        }
        return true;
    }

    /**
     * Recalculate wire arms using 1.21.1's own neighbor state transitions.
     * Only same-ID changes are allowed; the original power value is left to
     * vanilla's signal propagation. Wiring is never extended into empty cells.
     */
    public static boolean normalizeDust(ServerWorld world,BlockPos pos) {
        if(!neighborsLoaded(world,pos))return false;
        BlockState before=world.getBlockState(pos);
        if(!isDust(before))return false;
        BlockState next=before;
        for(Direction direction:Direction.values()){
            BlockPos adjacent=pos.offset(direction);
            if(!world.isInBuildLimit(adjacent))continue;
            next=next.getStateForNeighborUpdate(direction,
                world.getBlockState(adjacent),world,pos,adjacent);
        }
        if(!next.isOf(before.getBlock()))return false;
        // Vanilla owns the power level, which may already have changed
        // during earlier wire updates. We only repair connectivity here.
        next=next.with(RedstoneWireBlock.POWER,before.get(RedstoneWireBlock.POWER));
        if(next.equals(before))return false;
        world.setBlockState(pos,next,Block.NOTIFY_ALL);
        return true;
    }

    /**
     * Once every wire has been reviewed, propagate neighbor updates from
     * known redstone devices. Minecraft itself schedules signal/power ticks.
     * This does not execute or arm the still-empty BuildAI command blocks.
     */
    public static boolean refreshSignal(ServerWorld world,BlockPos pos) {
        if(!neighborsLoaded(world,pos))return false;
        BlockState current=world.getBlockState(pos);
        if(!isSignalDevice(current))return false;
        world.updateNeighbors(pos,current.getBlock());
        return true;
    }
}
