package com.hanco.buildai.blueprint;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;

public class ConstructionOptionsTest {
    @Test void defaultModeKeepsFullExcavationAndBase(){
        assertEquals(new ConstructionOptions(true,true),
            ConstructionOptions.parse(""));
        assertEquals(ConstructionOptions.DEFAULT,ConstructionOptions.parse(null));
        assertEquals(ConstructionOptions.DEFAULT,
            ConstructionOptions.parse("foundation:yes excavate:yes"));
    }
    @Test void acceptsIndependentAndReorderedFlags(){
        assertEquals(new ConstructionOptions(false,false),
            ConstructionOptions.parse("foundation:no excavate:no"));
        assertEquals(new ConstructionOptions(false,false),
            ConstructionOptions.parse("excavate:no foundation:no"));
        assertEquals(new ConstructionOptions(true,false),
            ConstructionOptions.parse("excavate:no"));
        assertEquals(new ConstructionOptions(false,true),
            ConstructionOptions.parse("foundation:no"));
    }
    @Test void rejectsUnsafeTyposAndDuplicates(){
        for(String bad:new String[]{"foundation:maybe","excavate:false",
            "force:yes","foundation:no foundation:yes","foundation:",
            "excavate:no:yes","no","excavate:no unknown:yes"}){
            assertThrows(IllegalArgumentException.class,
                ()->ConstructionOptions.parse(bad),bad);
        }
    }
    @Test void hollowIsItsOwnInteriorOnlyMode(){
        assertEquals(new ConstructionOptions(true,false,true),
            ConstructionOptions.parse("hollow:yes"));
        assertEquals(new ConstructionOptions(false,false,true),
            ConstructionOptions.parse("foundation:no hollow:yes"));
        assertEquals(new ConstructionOptions(false,false,true),
            ConstructionOptions.parse("excavate:no foundation:no hollow:yes"));
        assertEquals(new ConstructionOptions(false,false,false),
            ConstructionOptions.parse("hollow:no excavate:no foundation:no"));
        assertThrows(IllegalArgumentException.class,
            ()->ConstructionOptions.parse("hollow:yes excavate:yes"));
        assertThrows(IllegalArgumentException.class,
            ()->ConstructionOptions.parse("hollow:yes hollow:no"));
    }
    @Test void parsesExplicitFoundationMaterialFallbacksWithoutRelaxingModes(){
        var flags=ConstructionOptions.parse(
            "hollow:yes foundation:no:bedrock,stonebrick,grass_block,dirt");
        assertTrue(flags.hollow());
        assertFalse(flags.foundation());
        assertFalse(flags.excavate());
        assertEquals(Set.of("minecraft:bedrock","minecraft:stone_bricks",
            "minecraft:grass_block","minecraft:dirt"),flags.foundationMaterials());
        assertEquals(Set.of("minecraft:stone_bricks","minecraft:grass_block"),
            ConstructionOptions.parse(
                "foundation:no:minecraft:stone_bricks,minecraft:grass_block excavate:no")
            .foundationMaterials());
        assertEquals(Set.of("minecraft:bedrock","minecraft:stone_bricks",
            "minecraft:grass_block"),
            ConstructionOptions.parse(
                "foundation:no:bedrock, stonebrick, grass_block hollow:yes")
            .foundationMaterials());
        for(String bad:new String[]{
            "foundation:yes:dirt","foundation:no:","foundation:no:air",
            "foundation:no:stone_bricks,","foundation:no:dirt:what",
            "excavate:no:dirt","hollow:yes:stone","foundation:no:stonebrick foundation:no:dirt"
        })assertThrows(IllegalArgumentException.class,
            ()->ConstructionOptions.parse(bad),bad);
    }

    @Test void stripsAllMixedDirtAndGrassFoundationLayersIncludingTop(){
        // The screenshot defect: a full dirt platform capped in grass,
        // while the actual building occupies only a small central area.
        Blueprint b=new Blueprint("Hillside house",25,14,25);
        for(int y=0;y<=5;y++)
            for(int z=2;z<=22;z++)for(int x=2;x<=22;x++)
                b.set(x,y,z,(y==5&&(x+z)%7!=0)
                    ?"minecraft:grass_block":"minecraft:dirt");
        // House walls (including dirt-and-stone finish) must survive even
        // though its footprint is substantially narrower than the base.
        for(int y=6;y<=11;y++)for(int z=8;z<=16;z++)
            for(int x=8;x<=16;x++)
                if(x==8||x==16||z==8||z==16)
                    b.set(x,y,z,"minecraft:stone_bricks");
        b.set(12,9,8,"minecraft:chiseled_stone_bricks");
        var skip=ConstructionOptions.identifiedFoundation(b);
        assertEquals(6*21*21,skip.size(),
            "Entire 6-layer platform including mixed grass cap must be omitted");
        assertTrue(skip.contains(b.index(6,5,7)),"Top grass layer omitted");
        assertTrue(skip.contains(b.index(3,5,9)),"Top dirt patch omitted");
        assertFalse(skip.contains(b.index(8,9,8)),"Wall remains");
        assertFalse(skip.contains(b.index(12,9,8)),"Chiseled decoration remains");
        assertFalse(skip.contains(b.index(11,9,16)),"Other wall remains");
    }

    @Test void detectsFoundationFootprintInsideOversizedSchematic(){
        Blueprint b=new Blueprint("Oversized design including detached banner",48,12,40);
        for(int y=0;y<=2;y++)
            for(int z=9;z<=30;z++)for(int x=10;x<=31;x++)
                b.set(x,y,z,y==2?"minecraft:grass_block":"minecraft:dirt");
        b.set(45,7,35,"minecraft:gold_block"); // not part of the base
        var skip=ConstructionOptions.identifiedFoundation(b);
        assertEquals(22*22*3,skip.size(),
            "Use local foundation footprint, not entire blueprint bounding box");
        assertFalse(skip.contains(b.index(45,7,35)));
    }

    @Test void fallbackMaterialStillMustFormLargeDenseContiguousBase(){
        Blueprint b=new Blueprint("Custom foundation",12,8,12);
        for(int z=1;z<=10;z++)for(int x=1;x<=10;x++)
            b.set(x,0,z,"minecraft:glowstone");
        b.set(3,4,3,"minecraft:glowstone"); // unrelated interior decoration
        assertTrue(ConstructionOptions.identifiedFoundation(b).isEmpty(),
            "Non-standard base needs a material hint");
        var materials=ConstructionOptions.parse(
            "foundation:no:glowstone excavate:no").foundationMaterials();
        var skip=ConstructionOptions.identifiedFoundation(b,materials);
        assertEquals(100,skip.size());
        assertFalse(skip.contains(b.index(3,4,3)),
            "Extra hint may not delete arbitrary matching blocks elsewhere");
    }

    @Test void noUniformBaseMeansNoGlobalDirtErasure(){
        Blueprint b=new Blueprint("Organic hillside art",18,12,18);
        for(int y=0;y<=8;y++)for(int x=3;x<=14;x++)for(int z=2;z<=15;z++)
            if((x+y+z)%5==0)
                b.set(x,y,z,"minecraft:grass_block");
        assertTrue(ConstructionOptions.identifiedFoundation(b).isEmpty(),
            "No dense footprint-wide platform; keep all terrain features");
    }

    @Test void identifiesMultipleBaseLayersIncludingAirPadding(){
        Blueprint b=new Blueprint("Tall mountain house",8,9,8);
        for(int y=1;y<=3;y++)for(int x=0;x<8;x++)for(int z=0;z<8;z++)
            b.set(x,y,z,"minecraft:stone_bricks");
        b.set(2,4,2,"minecraft:oak_stairs");
        b.set(3,4,2,"minecraft:lantern");
        var omission=ConstructionOptions.identifiedFoundation(b);
        assertEquals(192,omission.size(),"All three base layers should be omitted");
        assertFalse(omission.contains(b.index(2,4,2)),"Living area must survive");
    }

    @Test void detectsObviousFourByFourSolidPlatformButKeepsStatue(){
        Blueprint b=new Blueprint("Mountain statue",6,10,6);
        for(int z=0;z<6;z++)for(int x=0;x<6;x++)
            b.set(x,0,z,"minecraft:stone_bricks");
        b.set(2,1,2,"minecraft:gold_block"); // statue legs
        b.set(3,1,2,"minecraft:gold_block");
        Set<Integer> removed=ConstructionOptions.identifiedFoundation(b);
        assertEquals(36,removed.size());
        assertTrue(removed.contains(b.index(0,0,0)));
        assertFalse(removed.contains(b.index(2,1,2)));
    }
    @Test void doesNotDeleteArtFirstRowOrMechanicalFirstLayer(){
        Blueprint pixel=new Blueprint("Bee artwork",1,20,30);
        for(int z=0;z<30;z++)
            pixel.set(0,0,z,"minecraft:black_concrete");
        assertTrue(ConstructionOptions.identifiedFoundation(pixel).isEmpty());
        Blueprint machine=new Blueprint("Machine",5,3,5);
        for(int z=0;z<5;z++)for(int x=0;x<5;x++)
            machine.set(x,0,z,"minecraft:stone_bricks");
        machine.set(2,0,2,"minecraft:redstone_wire");
        assertTrue(ConstructionOptions.identifiedFoundation(machine).isEmpty());
    }
    @Test void refusesUncertainBasesRatherThanRemovingSculptureFeet(){
        Blueprint legs=new Blueprint("Feet",10,8,10);
        for(int z=0;z<10;z++)for(int x=0;x<10;x++)
            if(x==2||x==7)
                legs.set(x,0,z,"minecraft:stone_bricks");
        assertTrue(ConstructionOptions.identifiedFoundation(legs).isEmpty());
    }
}
