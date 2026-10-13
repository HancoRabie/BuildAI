package com.hanco.buildai.blueprint;

import java.util.BitSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Tests only conservative BLUEPRINT classification, never simulates Minecraft. */
public class InteriorHollowPlanTest {
    private static Blueprint house(boolean roof,boolean door,boolean decoration){
        Blueprint b=new Blueprint("Compact interior",10,8,10);
        for(int x=1;x<=8;x++)for(int z=1;z<=8;z++){
            b.set(x,0,z,"minecraft:stone_bricks");
            if(roof)b.set(x,6,z,"minecraft:stone_bricks");
        }
        for(int y=1;y<=5;y++){
            for(int x=1;x<=8;x++){
                b.set(x,y,1,"minecraft:stone_bricks");
                b.set(x,y,8,"minecraft:stone_bricks");
            }
            for(int z=1;z<=8;z++){
                b.set(1,y,z,"minecraft:stone_bricks");
                b.set(8,y,z,"minecraft:stone_bricks");
            }
        }
        if(door){
            b.set(4,1,1,"minecraft:air");
            b.set(4,2,1,"minecraft:air");
        }
        if(decoration){
            b.set(5,1,5,"minecraft:oak_stairs[facing=north,half=bottom]");
            b.set(5,2,5,"minecraft:lantern");
            b.set(2,1,3,"minecraft:bookshelf");
        }
        return b;
    }
    @Test void findsRoomDespiteTwoBlockDoorGapAndPreservesDecor(){
        Blueprint b=house(true,true,true);
        BitSet mask=InteriorHollowPlan.enclosedAir(b);
        assertFalse(mask.isEmpty(),"A doored house should still be hollowable");
        assertTrue(mask.get(b.index(4,2,4)),"Clear terrain in actual indoor air");
        assertTrue(mask.get(b.index(7,5,5)),"Clear upper indoor air");
        assertFalse(mask.get(b.index(5,1,5)),"Furniture stays");
        assertFalse(mask.get(b.index(5,2,5)),"Hanging lighting stays");
        assertFalse(mask.get(b.index(2,1,3)),"Bookshelves stay");
        assertFalse(mask.get(b.index(4,1,1)),"Door opening is NOT interior");
        assertFalse(mask.get(b.index(0,3,4)),"Outside terrain remains");
        assertFalse(mask.get(b.index(4,0,4)),"Blueprint floor remains");
        assertFalse(mask.get(b.index(4,6,4)),"Blueprint roof remains");
    }
    @Test void rooflessCourtYieldsNoHollowing(){
        Blueprint b=house(false,false,false);
        assertTrue(InteriorHollowPlan.enclosedAir(b).isEmpty(),
            "Roofless outdoor enclosures should not trigger clearing");
    }
    @Test void noWallsStatueAndFlatRedstonePadDoNotTriggerHollowing(){
        Blueprint statue=new Blueprint("Jaguar statue",13,15,13);
        for(int y=2;y<14;y++)for(int x=3;x<10;x++)for(int z=3;z<10;z++)
            if((x+y+z)%3==0)statue.set(x,y,z,"minecraft:prismarine_bricks");
        assertTrue(InteriorHollowPlan.enclosedAir(statue).isEmpty());
        Blueprint pad=new Blueprint("Benchy",18,3,18);
        for(int x=0;x<18;x++)for(int z=0;z<18;z++)
            pad.set(x,0,z,"minecraft:stone_bricks");
        assertTrue(InteriorHollowPlan.enclosedAir(pad).isEmpty());
    }
    @Test void roomWithNoFloorCannotAccidentallyCarveTerrain(){
        Blueprint b=house(true,false,false);
        for(int x=1;x<=8;x++)for(int z=1;z<=8;z++)
            b.set(x,0,z,"minecraft:air");
        assertTrue(InteriorHollowPlan.enclosedAir(b).isEmpty(),
            "No floor means building may be a floating facade");
    }
    @Test void multiLevelRoomsNeedFloorsAboveAndBelow(){
        Blueprint b=house(true,false,false);
        for(int x=1;x<=8;x++)for(int z=1;z<=8;z++)
            b.set(x,3,z,"minecraft:stone_bricks");
        BitSet mask=InteriorHollowPlan.enclosedAir(b);
        assertTrue(mask.get(b.index(4,1,4)));
        assertTrue(mask.get(b.index(4,4,4)));
        assertFalse(mask.get(b.index(4,3,4)));
    }
}