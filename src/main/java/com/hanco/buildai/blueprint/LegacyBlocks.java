package com.hanco.buildai.blueprint;

import java.util.Map;

/** Conservative translation for the MCEdit numeric palette; reject ambiguous blocks. */
public final class LegacyBlocks {
    private static final String[] COLOR={"white","orange","magenta","light_blue","yellow","lime","pink","gray","light_gray","cyan","purple","blue","brown","green","red","black"};
    private static final Map<Integer,String> SIMPLE=Map.ofEntries(
        Map.entry(0,"air"),Map.entry(1,"stone"),Map.entry(2,"grass_block"),Map.entry(3,"dirt"),
        Map.entry(4,"cobblestone"),Map.entry(7,"bedrock"),Map.entry(12,"sand"),Map.entry(13,"gravel"),
        Map.entry(14,"gold_ore"),Map.entry(15,"iron_ore"),Map.entry(16,"coal_ore"),
        Map.entry(20,"glass"),Map.entry(22,"lapis_block"),Map.entry(41,"gold_block"),
        Map.entry(42,"iron_block"),Map.entry(45,"bricks"),Map.entry(47,"bookshelf"),
        Map.entry(48,"mossy_cobblestone"),Map.entry(49,"obsidian"),Map.entry(54,"chest"),
        Map.entry(56,"diamond_ore"),Map.entry(57,"diamond_block"),Map.entry(58,"crafting_table"),
        Map.entry(61,"furnace"),Map.entry(73,"redstone_ore"),Map.entry(79,"ice"),Map.entry(80,"snow_block"),
        Map.entry(82,"clay"),Map.entry(87,"netherrack"),Map.entry(89,"glowstone"),
        Map.entry(103,"melon"),Map.entry(112,"nether_bricks"),Map.entry(121,"end_stone"),
        Map.entry(133,"emerald_block"),Map.entry(152,"redstone_block"),Map.entry(172,"terracotta"),
        Map.entry(173,"coal_block"));
    public static String convert(int id,int meta) {
        if(id==95)return "minecraft:"+COLOR[meta&15]+"_stained_glass";
        if(id==160)return "minecraft:"+COLOR[meta&15]+"_stained_glass_pane";
        if(id==35)return "minecraft:"+COLOR[meta&15]+"_wool";
        if(id==159)return "minecraft:"+COLOR[meta&15]+"_terracotta";
        if(id==251)return "minecraft:"+COLOR[meta&15]+"_concrete";
        if(id==252)return "minecraft:"+COLOR[meta&15]+"_concrete_powder";
        if(id==5)return "minecraft:"+switch(meta&7){case 1->"spruce";case 2->"birch";case 3->"jungle";case 4->"acacia";case 5->"dark_oak";default->"oak";}+"_planks";
        if(id==44){
            String slab=switch(meta&7){case 0->"stone";case 1->"sandstone";case 3->"cobblestone";case 4->"brick";case 5->"stone_brick";case 6->"nether_brick";case 7->"quartz";default->null;};
            if(slab==null)return null;
            return "minecraft:"+slab+"_slab[type="+((meta&8)!=0?"top":"bottom")+"]";
        }
        if(meta!=0 && (id==1||id==2||id==3||id==4||id==12||id==13||id==20||id==54))
            return null;
        String name=SIMPLE.get(id);
        return name==null ? null : "minecraft:"+name;
    }
}
