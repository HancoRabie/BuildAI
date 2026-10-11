package com.hanco.buildai.blueprint;

/**
 * Deterministic offline demo: verifies preview, export, upload, materials and placement
 * without requiring network connectivity or a ChatGPT/API account.
 * Use in disposable Creative world. Never install prototype on valuable world.
 */
public final class DemoBlueprint {
    private DemoBlueprint() {}
    public static Blueprint cottage() {
        Blueprint b=new Blueprint("BuildAI Demo Cottage",15,11,13);
        fill(b,0,0,0,14,0,12,"minecraft:stone_bricks");
        fill(b,1,1,1,13,1,11,"minecraft:oak_planks");
        walls(b,1,2,1,13,6,11,"minecraft:stone_bricks");
        // Interior stays air; building is hollow, not a solid cuboid.
        // Cut a three-block-tall front entrance.
        fill(b,6,2,1,8,4,1,"minecraft:air");
        // Vertical windows on front and rear.
        for(int x:new int[]{3,4,10,11})for(int y=3;y<=4;y++){
            b.set(x,y,1,"minecraft:glass_pane");
            b.set(x,y,11,"minecraft:glass_pane");
        }
        for(int z:new int[]{4,5,8,9})for(int y=3;y<=4;y++){
            b.set(1,y,z,"minecraft:glass_pane");
            b.set(13,y,z,"minecraft:glass_pane");
        }
        // Short interior dividing wall with opening.
        fill(b,2,2,6,12,5,6,"minecraft:oak_planks");
        fill(b,7,2,6,8,4,6,"minecraft:air");
        // Sloped, block-stepped roof with ridge along Z (no untrusted fluid/NBT).
        for(int step=0;step<=4;step++){
            int y=6+step;
            int left=step,right=14-step;
            for(int z=0;z<=12;z++){
                b.set(left,y,z,"minecraft:spruce_planks");
                b.set(right,y,z,"minecraft:spruce_planks");
            }
        }
        fill(b,5,10,0,9,10,12,"minecraft:spruce_planks");
        // Visible corner posts on the facade.
        for(int y=2;y<=6;y++)for(int x:new int[]{1,13})for(int z:new int[]{1,11})
            b.set(x,y,z,"minecraft:stripped_oak_log");
        b.validateSlices();
        return b;
    }
    private static void fill(Blueprint b,int x1,int y1,int z1,int x2,int y2,int z2,String block){
        for(int y=y1;y<=y2;y++)for(int z=z1;z<=z2;z++)for(int x=x1;x<=x2;x++)b.set(x,y,z,block);
    }
    private static void walls(Blueprint b,int x1,int y1,int z1,int x2,int y2,int z2,String block){
        for(int y=y1;y<=y2;y++)for(int z=z1;z<=z2;z++)for(int x=x1;x<=x2;x++)
            if(x==x1||x==x2||z==z1||z==z2)b.set(x,y,z,block);
    }
}
