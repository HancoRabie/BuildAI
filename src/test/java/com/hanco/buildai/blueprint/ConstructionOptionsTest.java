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
        assertEquals(6*(21*21-9*9),skip.size(),
            "Omit every exterior foundation layer but KEEP the 9x9 wall-enclosed "+
            "building footprint, including its intended basement dirt");
        assertTrue(skip.contains(b.index(6,5,7)),"Top grass layer omitted");
        assertTrue(skip.contains(b.index(3,5,9)),"Top dirt patch omitted");
        assertFalse(skip.contains(b.index(8,9,8)),"Wall remains");
        assertFalse(skip.contains(b.index(12,9,8)),"Chiseled decoration remains");
        assertFalse(skip.contains(b.index(11,9,16)),"Other wall remains");
    }

    @Test void preservesIntentionalDirtBasementWithinWallsAtEveryFoundationY(){
        Blueprint b=new Blueprint("Mountain basement and exterior dirt",18,11,18);
        for(int y=0;y<=4;y++)for(int x=1;x<=16;x++)for(int z=1;z<=16;z++)
            b.set(x,y,z,y==4?"minecraft:grass_block":"minecraft:dirt");
        // Multi-storey wall enclosing a dirt-floored basement, with other
        // deliberately placed dirt inside the rooms.
        for(int y=5;y<=9;y++)for(int x=5;x<=12;x++)for(int z=5;z<=12;z++)
            if(x==5||x==12||z==5||z==12)
                b.set(x,y,z,((x+y+z)%2==0)?
                    "minecraft:mossy_stone_bricks":
                    "minecraft:chiseled_stone_bricks");
        b.set(8,5,8,"minecraft:dirt");
        b.set(8,6,8,"minecraft:grass_block");
        var skip=ConstructionOptions.identifiedFoundation(b);
        assertEquals(5*(16*16-8*8),skip.size(),
            "Only foundation OUTSIDE the walls should be omitted");
        for(int y=0;y<=4;y++){
            assertFalse(skip.contains(b.index(8,y,8)),
                "Intentional dirt under/inside basement must stay at Y "+y);
            assertFalse(skip.contains(b.index(5,y,8)),
                "Wall support remains at Y "+y);
            assertTrue(skip.contains(b.index(2,y,8)),
                "Exterior layer may be omitted at Y "+y);
        }
        assertFalse(skip.contains(b.index(8,5,8)));
        assertFalse(skip.contains(b.index(8,6,8)));
    }

    @Test void materialOverrideUsesIdenticalWallProtection(){
        Blueprint b=new Blueprint("Glowstone-supported greenhouse",14,8,14);
        for(int y=0;y<3;y++)for(int x=0;x<14;x++)for(int z=0;z<14;z++)
            b.set(x,y,z,"minecraft:glowstone");
        for(int y=3;y<=7;y++)for(int x=4;x<=9;x++)for(int z=4;z<=9;z++)
            if(x==4||x==9||z==4||z==9)
                b.set(x,y,z,"minecraft:stone_bricks");
        var hinted=ConstructionOptions.parse(
            "foundation:no:glowstone hollow:yes").foundationMaterials();
        var skip=ConstructionOptions.identifiedFoundation(b,hinted);
        assertEquals(3*(14*14-6*6),skip.size());
        assertTrue(skip.contains(b.index(0,0,0)));
        assertFalse(skip.contains(b.index(6,1,6)),
            "Custom material hints NEVER bypass protected interior");
    }

    @Test void fiftyPercentIsNotEnoughAndFiftyOnePercentKeepsOtherBlocks(){
        Blueprint b=new Blueprint("Ratio threshold",10,5,10);
        // Full 10x10 footprint: stone-like gold is a deliberate other
        // material, not in the foundation material set.
        for(int z=0;z<10;z++)for(int x=0;x<10;x++)
            b.set(x,0,z,"minecraft:gold_block");
        int added=0;
        // Full perimeter gives coverage on all four footprint edges.
        for(int z=0;z<10;z++)for(int x=0;x<10;x++)
            if(x==0||x==9||z==0||z==9){
                b.set(x,0,z,"minecraft:dirt");
                added++;
            }
        for(int z=1;z<9&&added<50;z++)for(int x=1;x<9&&added<50;x++){
            b.set(x,0,z,"minecraft:dirt");added++;
        }
        assertEquals(50,added);
        assertTrue(ConstructionOptions.identifiedFoundation(b).isEmpty(),
            "The threshold is AT LEAST 51%, never 50%");
        b.set(8,0,8,"minecraft:dirt"); // Increase to 51% across full footprint.
        int dirt=0;
        for(int x=0;x<10;x++)for(int z=0;z<10;z++)
            if(Blueprint.id(b.get(x,0,z)).equals("minecraft:dirt"))dirt++;
        assertEquals(51,dirt);
        var skip=ConstructionOptions.identifiedFoundation(b);
        assertEquals(51,skip.size());
        assertFalse(skip.contains(b.index(7,0,7)),
            "Other nonfoundation blocks in a qualifying layer stay intact");
    }

    @Test void stopsAtNextLayerOnceFiftyOnePercentThresholdIsLost(){
        Blueprint b=new Blueprint("Floor with basement",10,6,10);
        for(int z=0;z<10;z++)for(int x=0;x<10;x++){
            b.set(x,0,z,"minecraft:dirt");
            b.set(x,1,z,z<5?"minecraft:dirt":"minecraft:gold_block");
        }
        var skip=ConstructionOptions.identifiedFoundation(b);
        assertEquals(100,skip.size(),"Skip bottom 100% layer only");
        assertFalse(skip.contains(b.index(0,1,0)),
            "50%-coverage upper layer must not be stripped");
    }


    @Test void rejectsPartialPlatformEvenIfItTouchesEveryEdgeAndIsOver51Percent(){
        Blueprint b=new Blueprint("Missing foundation square",10,5,10);
        for(int x=0;x<10;x++)for(int z=0;z<10;z++)
            b.set(x,0,z,"minecraft:dirt");
        b.set(5,0,5,"minecraft:air");
        assertTrue(ConstructionOptions.identifiedFoundation(b).isEmpty(),
            "A 99% dirt layer with a non-basement hole does not cover the entire footprint");
    }

    @Test void refusesBaseThatDoesNotCoverWiderAboveGroundStructure(){
        Blueprint b=new Blueprint("Wide temple with partial footing",21,9,21);
        for(int x=1;x<=15;x++)for(int z=1;z<=15;z++)
            b.set(x,0,z,"minecraft:dirt");
        // A genuine roof/terrace at X=18 is outside the bottom patch.
        for(int y=1;y<=6;y++)for(int z=3;z<=17;z++){
            b.set(3,y,z,"minecraft:stone_bricks");
            b.set(18,y,z,"minecraft:stone_bricks");
        }
        assertTrue(ConstructionOptions.identifiedFoundation(b).isEmpty(),
            "A base spanning only its OWN bounds is not full building coverage");
    }

    @Test void preservesExteriorVerticalSupportsOnEveryBelowGroundLevel(){
        Blueprint b=new Blueprint("Exterior facade supports",18,9,18);
        for(int y=0;y<=3;y++)for(int x=0;x<18;x++)for(int z=0;z<18;z++)
            b.set(x,y,z,"minecraft:dirt");
        // Outdoor facade, not a fully enclosed room. The wall columns
        // themselves still need stable underpinnings all the way down.
        for(int y=4;y<=8;y++)for(int z=4;z<=13;z++)
            b.set(14,y,z,"minecraft:stone_bricks");
        var skip=ConstructionOptions.identifiedFoundation(b);
        assertFalse(skip.isEmpty(),"Exposed earth away from architecture can still go");
        for(int y=0;y<=3;y++){
            assertFalse(skip.contains(b.index(14,y,9)),
                "Never undermine a genuine standing side wall");
            assertTrue(skip.contains(b.index(1,y,1)),
                "Outside dirt without structural support can be omitted");
        }
    }

    @Test void retainsWallProtectedBasementVoidsWithoutCountingThemAsMissingCoverage(){
        Blueprint b=new Blueprint("Open underground hall",15,10,15);
        for(int y=0;y<4;y++)for(int x=0;x<15;x++)for(int z=0;z<15;z++)
            b.set(x,y,z,"minecraft:dirt");
        for(int y=4;y<=8;y++)for(int x=3;x<=11;x++)for(int z=3;z<=11;z++)
            if(x==3||x==11||z==3||z==11)
                b.set(x,y,z,"minecraft:mossy_stone_bricks");
        // Intentional below-ground void and rare planted block within walls.
        b.set(7,1,7,"minecraft:air");
        b.set(7,2,7,"minecraft:grass_block");
        var skip=ConstructionOptions.identifiedFoundation(b);
        assertFalse(skip.isEmpty(),"Enclosed underground void should not veto a valid base");
        assertFalse(skip.contains(b.index(7,1,7)));
        assertFalse(skip.contains(b.index(7,2,7)));
        assertFalse(skip.contains(b.index(3,0,7)),"Basement wall footprint protected");
        assertTrue(skip.contains(b.index(1,0,7)),"Outdoor platform removable");
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
