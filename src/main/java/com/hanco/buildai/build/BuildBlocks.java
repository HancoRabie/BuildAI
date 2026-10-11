package com.hanco.buildai.build;

import net.minecraft.block.*;
import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** Non-obtainable temporary structural staging blocks, never part of a material manifest. */
public final class BuildBlocks {
    private BuildBlocks(){}
    public static final Block CONSTRUCTION_BLOCK = new Block(AbstractBlock.Settings.copy(Blocks.LIGHT_BLUE_STAINED_GLASS)
        .strength(0.1f).nonOpaque());
    public static final SlabBlock CONSTRUCTION_SLAB = new SlabBlock(AbstractBlock.Settings.copy(Blocks.LIGHT_BLUE_STAINED_GLASS)
        .strength(0.1f).nonOpaque());
    public static void register(){
        Registry.register(Registries.BLOCK,Identifier.of("buildai","construction_block"),CONSTRUCTION_BLOCK);
        Registry.register(Registries.BLOCK,Identifier.of("buildai","construction_slab"),CONSTRUCTION_SLAB);
    }
}
