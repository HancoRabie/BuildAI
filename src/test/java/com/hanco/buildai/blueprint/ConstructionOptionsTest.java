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
            "foundation:no:stone_bricks,","foundation:no:dirt:what:extra",
            "excavate:no:dirt","hollow:yes:stone","foundation:no:stonebrick foundation:no:dirt"
        })assertThrows(IllegalArgumentException.class,
            ()->ConstructionOptions.parse(bad),bad);
    }

    private static Blueprint filled(String name,int w,int h,int d,String... layers){
        Blueprint b=new Blueprint(name,w,h,d);
        for(int y=0;y<layers.length;y++)
            for(int x=0;x<w;x++)for(int z=0;z<d;z++)
                b.set(x,y,z,layers[y]);
        return b;
    }

    @Test void majorityBlockTypeMustBeAtLeast51Percent(){
        Blueprint b=filled("Majority",10,4,10,"minecraft:gold_block");
        for(int z=0;z<10;z++)for(int x=0;x<10;x++)
            if(z*10+x<50)b.set(x,0,z,"minecraft:grass_block");
        assertTrue(ConstructionOptions.identifiedFoundation(b).isEmpty(),
            "50% grass + 50% gold does not qualify");
        b.set(0,0,5,"minecraft:grass_block");
        var skip=ConstructionOptions.identifiedFoundation(b);
        assertEquals(51,skip.size(),"51% of the ENTIRE layer qualifies");
        assertFalse(skip.contains(b.index(9,0,9)),"Keep every other material");
    }

    @Test void doNotCombineMinorityBlockTypesToInventAMajority(){
        Blueprint b=filled("Half grass half dirt",10,4,10,"minecraft:dirt");
        for(int z=0;z<5;z++)for(int x=0;x<10;x++)
            b.set(x,0,z,"minecraft:grass_block");
        assertTrue(ConstructionOptions.identifiedFoundation(b).isEmpty(),
            "Each block type needs its own >=51% share");
    }

    @Test void stripEntireQualifyingGrassTypeWithoutRoomAndSupportExceptions(){
        Blueprint b=filled("Temple podium",10,8,10,"minecraft:grass_block");
        for(int y=1;y<=6;y++)for(int z=2;z<=7;z++){
            b.set(2,y,z,"minecraft:stone_bricks");
            b.set(7,y,z,"minecraft:stone_bricks");
        }
        var skip=ConstructionOptions.identifiedFoundation(b);
        assertEquals(100,skip.size(),
            "All grass from the qualifying layer must be omitted, including beneath walls");
        assertTrue(skip.contains(b.index(2,0,4)),"Do not hide foundation grass beneath walls");
        assertTrue(skip.contains(b.index(5,0,5)),"Do not hide foundation grass beneath rooms");
        assertFalse(skip.contains(b.index(2,2,4)),"Masonry is not part of the grass layer");
    }

    @Test void omitOnlyDominantMaterialNotAllEligibleTypes(){
        Blueprint b=filled("Mixed top grass/dirt",10,5,10,"minecraft:grass_block");
        for(int z=0;z<4;z++)for(int x=0;x<10;x++)
            b.set(x,0,z,"minecraft:dirt");
        var skip=ConstructionOptions.identifiedFoundation(b);
        assertEquals(60,skip.size(),"Only the >=51% grass type qualifies");
        assertTrue(skip.contains(b.index(0,0,8)));
        assertFalse(skip.contains(b.index(0,0,1)),"Minority dirt is NOT silently included");
    }

    @Test void platformMustCoverEntireSchematicNotOnlyItsOwnBounds(){
        Blueprint b=new Blueprint("Full schematic coverage",12,7,12);
        for(int x=1;x<=10;x++)for(int z=1;z<=10;z++)
            b.set(x,0,z,"minecraft:grass_block");
        assertTrue(ConstructionOptions.identifiedFoundation(b).isEmpty(),
            "A fully filled 10x10 island inside a 12x12 schematic does not qualify");
    }

    @Test void aSingleMissingCellDisqualifiesWholeLayer(){
        Blueprint b=filled("One gap",10,5,10,"minecraft:grass_block");
        b.set(5,0,5,"minecraft:air");
        assertTrue(ConstructionOptions.identifiedFoundation(b).isEmpty(),
            "99% coverage is NOT full schematic coverage");
    }

    @Test void consecutiveQualifyingLayersAreIndependentlyClassified(){
        Blueprint b=filled("Dirt under grass",10,5,10,
            "minecraft:dirt","minecraft:grass_block","minecraft:gold_block");
        for(int x=0;x<10;x++)for(int z=0;z<5;z++)
            b.set(x,2,z,"minecraft:stone_bricks");
        var skip=ConstructionOptions.identifiedFoundation(b);
        assertEquals(200,skip.size(),"Two independently qualifying full layers omitted");
        assertTrue(skip.contains(b.index(0,0,0)));
        assertTrue(skip.contains(b.index(0,1,0)));
        assertFalse(skip.contains(b.index(0,2,0)),"Stop at 50% third layer");
    }

    @Test void missingSecondLayerStopsScanWithoutDeletingHigherLayers(){
        Blueprint b=filled("Interrupted platform",10,5,10,
            "minecraft:dirt","minecraft:grass_block","minecraft:dirt");
        b.set(9,1,9,"minecraft:air");
        var skip=ConstructionOptions.identifiedFoundation(b);
        assertEquals(100,skip.size());
        assertFalse(skip.contains(b.index(9,2,9)),"Stop at first incomplete layer");
    }

    @Test void respectsBlankYPaddingButNotHorizontalPadding(){
        Blueprint b=new Blueprint("Padded Y",8,8,8);
        for(int x=0;x<8;x++)for(int z=0;z<8;z++)
            b.set(x,3,z,"minecraft:dirt");
        assertEquals(64,ConstructionOptions.identifiedFoundation(b).size());
    }

    @Test void customHintOnlyQualifiesWhenFullFootprintAndMajorityHold(){
        Blueprint b=filled("Glowstone",10,6,10,
            "minecraft:glowstone","minecraft:glowstone");
        assertTrue(ConstructionOptions.identifiedFoundation(b).isEmpty());
        var hints=ConstructionOptions.parse("foundation:no:glowstone").foundationMaterials();
        assertEquals(200,ConstructionOptions.identifiedFoundation(b,hints).size());
        b.set(4,0,5,"minecraft:air");
        assertTrue(ConstructionOptions.identifiedFoundation(b,hints).isEmpty());
    }

    @Test void functionalMaterialsCannotBecomeFoundationWithExplicitHints(){
        Blueprint b=filled("Chest wall",10,4,10,"minecraft:chest");
        var hints=ConstructionOptions.parse("foundation:no:chest").foundationMaterials();
        assertTrue(ConstructionOptions.identifiedFoundation(b,hints).isEmpty(),
            "Inventories remain non-removable, even with custom hints");
    }

    @Test void doNotTreatWallFootprintAsProofOfFoundationCoverage(){
        Blueprint b=new Blueprint("Wall-only base",10,7,10);
        for(int y=0;y<5;y++)for(int z=0;z<10;z++){
            b.set(0,y,z,"minecraft:stone_bricks");
            b.set(9,y,z,"minecraft:stone_bricks");
        }
        assertTrue(ConstructionOptions.identifiedFoundation(b).isEmpty());
    }

    @Test void retainingDifferentUndergroundMaterialDoesNotProtectMajorityDirt(){
        Blueprint b=filled("Mixed basement",10,6,10,"minecraft:dirt");
        for(int z=0;z<10;z++)for(int x=0;x<4;x++)
            b.set(x,0,z,"minecraft:gold_block");
        var skip=ConstructionOptions.identifiedFoundation(b);
        assertEquals(60,skip.size());
        assertFalse(skip.contains(b.index(0,0,1)),"Distinct basement masonry retained");
        assertTrue(skip.contains(b.index(5,0,1)),"No spatial interior exception for dirt");
    }
}
