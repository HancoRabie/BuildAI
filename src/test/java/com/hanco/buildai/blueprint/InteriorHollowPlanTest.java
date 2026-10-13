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
    @Test void rooflessRoomStillHollowsBetweenFourActualWalls(){
        Blueprint b=house(false,false,false);
        var mask=InteriorHollowPlan.enclosedAir(b);
        assertTrue(mask.get(b.index(4,2,4)),
            "The user's rule is walls define interiors, not roof presence");
        assertFalse(mask.get(b.index(0,2,4)),
            "Never remove outside terrain");
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
    @Test void aTerrainFloorDoesNotBlockInteriorClearing(){
        Blueprint b=house(true,false,false);
        for(int x=1;x<=8;x++)for(int z=1;z<=8;z++)
            b.set(x,0,z,"minecraft:air");
        var mask=InteriorHollowPlan.enclosedAir(b);
        assertTrue(mask.get(b.index(4,1,4)),
            "An empty blueprint floor doesn't justify leaving mountain dirt inside");
        assertFalse(mask.get(b.index(0,1,4)));
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
    /** Reproduces the user's jungle temple: two long, parallel stone
     * walls, an open entrance and exit, but NO blueprint roof/floor.
     * Dirt/leaves inside the planned AIR tunnel must go; jungle outside
     * those wall lines must remain untouched. */
    @Test void jungleTempleTunnelClearsBetweenSideWallsNotOutside(){
        Blueprint b=new Blueprint("Jungle temple hall",15,8,20);
        for(int y=0;y<=5;y++)for(int z=2;z<=17;z++){
            b.set(3,y,z,"minecraft:chiseled_stone_bricks");
            b.set(11,y,z,"minecraft:chiseled_stone_bricks");
        }
        // Builder adds a decorative lintel at one end and lanterns.
        for(int x=3;x<=11;x++)
            b.set(x,5,2,"minecraft:stone_bricks");
        b.set(4,3,10,"minecraft:lantern");
        b.set(7,2,8,"minecraft:oak_stairs[facing=south,half=bottom]");
        b.set(6,3,8,"minecraft:vine");
        // The user wanted NO prefabricated floor or roof.
        assertTrue(b.isAir(7,0,10));
        assertTrue(b.isAir(7,7,10));
        var mask=InteriorHollowPlan.enclosedAir(b);
        assertTrue(mask.get(b.index(7,1,10)),
            "Interior grass/dirt must be removed in a wall-defined passage");
        assertTrue(mask.get(b.index(7,4,10)),
            "Interior foliage at head height must also be cleared");
        assertTrue(mask.get(b.index(7,1,3)),
            "Entrance should not leave a curtain of terrain just inside");
        assertTrue(mask.get(b.index(7,1,16)),
            "Exit should also clear");
        assertFalse(mask.get(b.index(7,1,1)),
            "Outside approach must remain intact");
        assertFalse(mask.get(b.index(7,1,18)),
            "Terrain beyond the exit must remain");
        assertFalse(mask.get(b.index(2,1,10)),
            "Terrain to the left of the wall stays");
        assertFalse(mask.get(b.index(12,1,10)),
            "Terrain to the right of the wall stays");
        assertFalse(mask.get(b.index(4,3,10)),
            "Placed blueprint lanterns are not excavated");
        assertFalse(mask.get(b.index(7,2,8)),
            "Placed benches are preserved");
        assertFalse(mask.get(b.index(6,3,8)),
            "Specified blueprint vine is decoration and remains");
    }
    @Test void twoPostsCannotBeMistakenForCorridor(){
        Blueprint b=new Blueprint("Decorative columns",12,7,12);
        for(int y=1;y<=5;y++){
            b.set(2,y,6,"minecraft:stone_bricks");
            b.set(9,y,6,"minecraft:stone_bricks");
        }
        assertTrue(InteriorHollowPlan.enclosedAir(b).isEmpty(),
            "Two isolated pillars must not cause a destructive hollowing pass");
    }
    @Test void rotatedLongitudinalCorridorAlsoWorks(){
        Blueprint b=new Blueprint("East west hall",19,6,12);
        for(int y=0;y<=4;y++)for(int x=2;x<=16;x++){
            b.set(x,y,2,"minecraft:stone_bricks");
            b.set(x,y,9,"minecraft:stone_bricks");
        }
        var mask=InteriorHollowPlan.enclosedAir(b);
        assertTrue(mask.get(b.index(9,1,5)));
        assertFalse(mask.get(b.index(1,1,5)));
        assertFalse(mask.get(b.index(17,1,5)));
    }
    @Test void openBuildingFacadeCannotExcavateBeyondItsLastWall(){
        Blueprint b=new Blueprint("Two partial walls",18,8,18);
        for(int y=1;y<=6;y++)for(int z=4;z<=7;z++){
            b.set(4,y,z,"minecraft:stone_bricks");
            b.set(13,y,z,"minecraft:stone_bricks");
        }
        var mask=InteriorHollowPlan.enclosedAir(b);
        assertFalse(mask.get(b.index(8,2,8)),
            "No walls at this longitudinal point: do not excavate exterior");
        assertFalse(mask.get(b.index(8,2,3)));
    }

    @Test void mixedMossyChiseledAndInterruptedWallLevelsStillDefineInterior(){
        // Temple wall materials and relief panels differ at each level.
        // At height 3, both walls have decorative recesses with AIR;
        // the previous same-Y ray casting stopped hollowing this level.
        Blueprint b=new Blueprint("Mixed-material temple hall",17,9,21);
        String[] palette={
            "minecraft:stone_bricks",
            "minecraft:mossy_stone_bricks",
            "minecraft:chiseled_stone_bricks",
            "minecraft:cracked_stone_bricks"
        };
        for(int z=2;z<=18;z++)for(int y=1;y<=6;y++){
            String block=palette[(y+z)%palette.length];
            if(y!=3||z%4!=0){
                b.set(3,y,z,block);
                b.set(13,y,z,palette[(y+z+2)%palette.length]);
            }
        }
        // A decorative chiseled lintel and a mossy floor patch, both
        // legitimate blueprint blocks which must NOT be excavated.
        for(int x=3;x<=13;x++)
            b.set(x,7,5,"minecraft:chiseled_stone_bricks");
        b.set(6,1,10,"minecraft:mossy_stone_bricks");
        b.set(8,3,10,"minecraft:lantern");
        b.set(9,2,11,"minecraft:oak_stairs");
        var mask=InteriorHollowPlan.enclosedAir(b);
        assertTrue(mask.get(b.index(7,3,8)),
            "Hollow air behind recessed CHISELED/MOSSY walls at the missing y-slice");
        assertTrue(mask.get(b.index(7,5,8)),
            "Clear remaining floating dirt near the ceiling inside");
        assertTrue(mask.get(b.index(7,2,17)),
            "Clear further back inside the tunnel");
        assertFalse(mask.get(b.index(7,2,1)),
            "No clearing outside entrance");
        assertFalse(mask.get(b.index(7,2,19)),
            "No clearing outside exit");
        assertFalse(mask.get(b.index(2,2,8)),
            "Exterior jungle outside left wall remains");
        assertFalse(mask.get(b.index(14,2,8)),
            "Exterior jungle outside right wall remains");
        assertFalse(mask.get(b.index(7,8,8)),
            "Do not clear jungle canopy above the wall tops");
        assertFalse(mask.get(b.index(6,1,10)),
            "Decorative mossy stone remains untouched");
        assertFalse(mask.get(b.index(8,3,10)),
            "Lantern remains untouched");
        assertFalse(mask.get(b.index(9,2,11)),
            "Blueprint furniture remains untouched");
        assertFalse(mask.get(b.index(7,7,5)),
            "Chiseled roof segment remains untouched");
    }

    @Test void thickFootprintFoundationNotMistakenForInteriorCrossWalls(){
        Blueprint b=new Blueprint("Temple with 3-layer podium",16,10,16);
        for(int x=1;x<=14;x++)for(int z=1;z<=14;z++){
            for(int y=0;y<=2;y++)b.set(x,y,z,"minecraft:stone_bricks");
            for(int y=3;y<=8;y++){
                if(x==1||x==14||z==1||z==14)
                    b.set(x,y,z,"minecraft:mossy_stone_bricks");
            }
        }
        var mask=InteriorHollowPlan.enclosedAir(b);
        assertTrue(mask.get(b.index(8,5,8)),
            "A large thick foundation must not hide genuine standing sidewalls");
        assertFalse(mask.get(b.index(8,1,8)),
            "Existing solid blueprint foundation is protected");
        assertFalse(mask.get(b.index(0,5,8)),
            "Exterior remains outside the wall");
    }

    @Test void thickPlatformAloneIsNotARoomOrHallway(){
        // A three-layer podium has exposed OUTWARD sides but no inward
        // standing wall faces. Hollow mode must never excavate the space
        // above the pad merely because a footprint has a perimeter.
        Blueprint b=new Blueprint("Three-layer stone podium",18,9,18);
        for(int x=2;x<=15;x++)for(int z=2;z<=15;z++)
            for(int y=0;y<=2;y++)
                b.set(x,y,z,"minecraft:stone_bricks");
        assertTrue(InteriorHollowPlan.enclosedAir(b).isEmpty(),
            "Thick podium exterior must not become a fake enclosed interior");
    }

}