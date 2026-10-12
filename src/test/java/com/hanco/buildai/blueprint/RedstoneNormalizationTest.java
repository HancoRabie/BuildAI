package com.hanco.buildai.blueprint;

import com.hanco.buildai.build.RedstoneNormalizer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RedstoneNormalizationTest {
    @Test void commonPowerSourcesAndListenersAreIncluded(){
        for(String id:new String[]{
            "minecraft:redstone_wire","minecraft:repeater","minecraft:comparator",
            "minecraft:observer","minecraft:redstone_torch","minecraft:lever",
            "minecraft:oak_button","minecraft:stone_button",
            "minecraft:polished_blackstone_pressure_plate",
            "minecraft:powered_rail","minecraft:detector_rail",
            "minecraft:dispenser","minecraft:dropper",
            "minecraft:hopper","minecraft:redstone_lamp"})
            assertTrue(RedstoneNormalizer.signalDeviceId(id),id);
    }

    @Test void commandBlocksAndUnrelatedDecorationAreNeverPowerRefreshSources(){
        for(String id:new String[]{
            "minecraft:command_block","minecraft:chain_command_block",
            "minecraft:repeating_command_block","minecraft:stone",
            "minecraft:oak_fence","minecraft:glass_pane","minecraft:chain",
            "minecraft:air","minecraft:leveraged_fake"})
            assertFalse(RedstoneNormalizer.signalDeviceId(id),id);
    }

    @Test void nonVanillaBlocksAreNotAccidentallyIncludedViaSuffix(){
        assertFalse(RedstoneNormalizer.signalDeviceId("othermod:dangerous_button"));
        assertFalse(RedstoneNormalizer.signalDeviceId("othermod:magic_pressure_plate"));
    }
}
