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
        assertEquals(new ConstructionOptions(false,false),
            ConstructionOptions.parse("foundation:no"));
        assertEquals(new ConstructionOptions(false,true),
            ConstructionOptions.parse("foundation:no excavate:yes"),
            "Full world excavation requires explicit opt-in when omitting foundation");
    }
    @Test void schematicFoundationSanitizationNeverMutatesSourceBlocks(){
        Blueprint blueprint=new Blueprint("Sanitize, don't excavate",12,7,12);
        for(int y=0;y<3;y++)for(int x=0;x<12;x++)for(int z=0;z<12;z++)
            blueprint.set(x,y,z,y==2?"minecraft:grass_block":"minecraft:stone");
        for(int y=3;y<6;y++){
            blueprint.set(3,y,3,"minecraft:stone_bricks");
            blueprint.set(8,y,8,"minecraft:stone_bricks");
        }
        var omissions=FoundationPlan.analyze(blueprint,Set.of()).omissions();
        assertEquals(3*12*12,omissions.size(),
            "All qualifying foundation layers should be removed from the placement plan");
        var queue=blueprint.orderedOmitting(omissions);
        assertEquals(6,queue.size(),
            "Only the six actual design blocks should be queued for construction");
        for(var p:queue)
            assertFalse(omissions.contains(blueprint.index(p.getX(),p.getY(),p.getZ())),
                "A foundation block must NEVER re-enter the build queue");
        assertEquals("minecraft:stone",blueprint.get(0,0,0),
            "Source schematic data remains unchanged after sanitization");
        assertEquals("minecraft:grass_block",blueprint.get(0,2,0),
            "No omitted schematic voxel gets overwritten with AIR");
        assertEquals(3*12*12+6,blueprint.ordered().size(),
            "Original schematic must still contain all its original blocks");
        assertThrows(IllegalArgumentException.class,()->
            blueprint.orderedOmitting(Set.of(blueprint.width*blueprint.height*blueprint.length)));
    }

    @Test void sanitizeFoundationIsNotAnExcavationRequest(){
        var onlyFoundation=ConstructionOptions.parse("foundation:no");
        assertFalse(onlyFoundation.foundation());
        assertFalse(onlyFoundation.excavate(),
            "foundation:no NEVER turns on full world excavation by default");
        assertFalse(onlyFoundation.hollow(),
            "No interior excavation without explicit hollow:yes");
        var overlay=ConstructionOptions.parse("foundation:no excavate:no");
        assertFalse(overlay.excavate());
        assertFalse(overlay.hollow());
        var chosenHollow=ConstructionOptions.parse("foundation:no excavate:no hollow:yes");
        assertFalse(chosenHollow.excavate());
        assertTrue(chosenHollow.hollow(),
            "Hollowing is the separately requested interior-terrain edit");
        var explicitFull=ConstructionOptions.parse("foundation:no excavate:yes");
        assertTrue(explicitFull.excavate(),"Player can still explicitly request full clearing");
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

    @Test void abovegroundCorridorDoesNotProtectFloatingFoundationStone(){
        Blueprint b=filled("Temple podium",10,8,10,"minecraft:grass_block");
        for(int y=1;y<=6;y++)for(int z=2;z<=7;z++){
            b.set(2,y,z,"minecraft:stone_bricks");
            b.set(7,y,z,"minecraft:stone_bricks");
        }
        var skip=ConstructionOptions.identifiedFoundation(b);
        assertEquals(100,skip.size(),
            "An ABOVE-ground hallway does not justify keeping a pillar underneath");
        assertTrue(skip.contains(b.index(2,0,4)),"Delete dangling support below temple side wall");
        assertTrue(skip.contains(b.index(5,0,5)),"Delete platform below aboveground corridor");
        assertTrue(skip.contains(b.index(0,0,0)),"Outside grass skirt is omitted");
        assertFalse(skip.contains(b.index(2,2,4)),"Retain actual masonry");
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

    @Test void bottomPlatformMayExcludeSchematicAirPadding(){
        Blueprint b=new Blueprint("Full schematic coverage",12,7,12);
        for(int x=1;x<=10;x++)for(int z=1;z<=10;z++)
            b.set(x,0,z,"minecraft:grass_block");
        assertEquals(100,ConstructionOptions.identifiedFoundation(b).size(),
            "Connected 10x10 platform qualifies despite 12x12 AIR-padded schematic");
    }

    @Test void singleMissingCellStillQualifiesAtNinetyPercent(){
        Blueprint b=filled("One gap",10,5,10,"minecraft:grass_block");
        b.set(5,0,5,"minecraft:air");
        var result=FoundationPlan.analyze(b,Set.of());
        assertEquals(1,result.qualifyingLayers());
        assertEquals(99,result.omissions().size(),
            "A hole in an anchored 10x10 footprint must NOT veto a 99%-filled foundation layer");
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

    @Test void ninetyNinePercentSecondLayerDoesNotStopScan(){
        Blueprint b=filled("Interrupted platform",10,5,10,
            "minecraft:dirt","minecraft:grass_block","minecraft:dirt");
        b.set(9,1,9,"minecraft:air");
        var skip=ConstructionOptions.identifiedFoundation(b);
        assertEquals(299,skip.size(),
            "100% + 99% + 100% qualifying layers must all be omitted");
        assertTrue(skip.contains(b.index(9,2,9)),"Keep scanning after 99%-filled layer");
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
        assertEquals(199,ConstructionOptions.identifiedFoundation(b,hints).size(),
            "A single air cell in the 100-cell bottom layer still exceeds 90% coverage");
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


    @Test void fiveLayerPaddedStoneAndGrassPlatformWithBasement(){
        Blueprint b=new Blueprint("Elevated jungle temple",20,14,21);
        for(int y=0;y<5;y++)for(int x=2;x<=17;x++)for(int z=3;z<=18;z++)
            b.set(x,y,z,y==4?"minecraft:grass_block":"minecraft:stone");
        for(int y=5;y<=11;y++){
            for(int x=7;x<=12;x++){
                b.set(x,y,7,"minecraft:stone_bricks");
                b.set(x,y,12,"minecraft:stone_bricks");
            }
            for(int z=7;z<=12;z++){
                b.set(7,y,z,"minecraft:stone_bricks");
                b.set(12,y,z,"minecraft:stone_bricks");
            }
        }
        var result=FoundationPlan.analyze(b,Set.of("minecraft:stone","minecraft:grass_block"));
        assertEquals(5,result.qualifyingLayers());
        assertTrue(result.omissions().contains(b.index(3,1,4)),"Outside stone skirt");
        assertTrue(result.omissions().contains(b.index(3,4,4)),"Outside grass cap");
        assertTrue(result.omissions().contains(b.index(9,1,9)),
            "Upper-floor AIR must not preserve five floating stone layers");
        assertTrue(result.omissions().contains(b.index(7,3,9)),
            "Supporting stone under an aboveground wall is foundation, not a basement room");
        assertTrue(result.reason().contains("16x16"));
    }

    @Test void interiorGapIsAllowedWithinNinetyPercentFootprint(){
        Blueprint b=new Blueprint("Foundation with missing cell",14,6,14);
        for(int x=2;x<=11;x++)for(int z=2;z<=11;z++)
            b.set(x,0,z,"minecraft:stone");
        b.set(6,0,6,"minecraft:air");
        var result=FoundationPlan.analyze(b,Set.of());
        assertEquals(99,result.omissions().size(),
            "A single basement opening is not grounds to reject a 99%-filled stone base");
    }

    @Test void lShapedPlatformUsesRealSilhouetteNotRectangle(){
        Blueprint b=new Blueprint("L shaped base",16,5,16);
        for(int x=2;x<=12;x++)for(int z=2;z<=12;z++)
            if(x<=5||z<=5)b.set(x,0,z,"minecraft:stone");
        var result=FoundationPlan.analyze(b,Set.of());
        assertEquals(72,result.omissions().size());
        assertFalse(result.omissions().contains(b.index(10,0,10)));
    }

    @Test void sixFoundationLayersWithBasementVoidsPassAtNinetyPercent(){
        Blueprint b=new Blueprint("Six-level underground platform",26,17,27);
        // 20x20 true connected footprint within a padded schematic.
        for(int y=0;y<6;y++)for(int x=3;x<23;x++)for(int z=4;z<24;z++)
            b.set(x,y,z,y==5?"minecraft:grass_block":"minecraft:stone");
        // Interior basement voids reach 10% on each of the six levels,
        // while leaving a four-connected platform surrounding them.
        for(int y=0;y<6;y++)for(int x=10;x<18;x++)for(int z=11;z<16;z++)
            b.set(x,y,z,"minecraft:air");
        // Genuine architectural room above: protect its belowground area.
        for(int y=6;y<=13;y++){
            for(int x=9;x<=18;x++){
                b.set(x,y,10,"minecraft:stone_bricks");
                b.set(x,y,17,"minecraft:stone_bricks");
            }
            for(int z=10;z<=17;z++){
                b.set(9,y,z,"minecraft:stone_bricks");
                b.set(18,y,z,"minecraft:stone_bricks");
            }
        }
        var scan=FoundationPlan.analyze(b,Set.of());
        assertEquals(6,scan.qualifyingLayers(),"Exactly 90% of the real footprint qualifies");
        assertTrue(scan.omissions().contains(b.index(4,1,5)),
            "Majority stone OUTSIDE building walls should be omitted");
        assertTrue(scan.omissions().contains(b.index(4,5,5)),
            "The grass top cap also qualifies when >=51% of the footprint");
        assertFalse(scan.omissions().contains(b.index(12,1,12)),
            "Actual basement air never enters omissions");
        assertFalse(scan.omissions().contains(b.index(9,1,12)),
            "Basement wall footprint and underlying support remain");
    }

    @Test void coverageBelowNinetyPercentStopsAtTheFirstFailedLayer(){
        Blueprint b=new Blueprint("Stop at 89%",20,7,20);
        for(int y=0;y<4;y++)for(int x=0;x<20;x++)for(int z=0;z<20;z++)
            b.set(x,y,z,"minecraft:stone");
        // 360/400 = 90% at Y=1.
        for(int x=5;x<13;x++)for(int z=5;z<10;z++)
            b.set(x,1,z,"minecraft:air");
        // 356/400 = 89% at Y=2. The third layer must fail,
        // and no Y=3 blocks may be included even though it's solid.
        for(int x=5;x<13;x++)for(int z=5;z<10;z++)
            b.set(x,2,z,"minecraft:air");
        for(int x=5;x<9;x++)b.set(x,2,10,"minecraft:air");
        var scan=FoundationPlan.analyze(b,Set.of());
        assertEquals(2,scan.qualifyingLayers());
        assertEquals(2,scan.stoppedAtY());
        assertTrue(scan.omissions().contains(b.index(0,0,0)),
            "Outside majority stone on qualifying Y=0 is omitted");
        assertTrue(scan.omissions().contains(b.index(0,1,0)),
            "Outside majority stone on exactly-90%-filled Y=1 is omitted");
        assertTrue(scan.omissions().size()<=760 && scan.omissions().size()>0,
            "Enclosed basement positions may be protected; never inflate the raw 760 candidates");
        assertTrue(scan.reason().contains("356/400"));
        assertTrue(scan.reason().contains("90%"));
        assertFalse(scan.omissions().contains(b.index(0,3,0)));
    }

    @Test void majorityStillUsesWholeFootprintRatherThanOnlyOccupiedCells(){
        Blueprint b=new Blueprint("Coverage with mixed types",10,5,10);
        for(int x=0;x<10;x++)for(int z=0;z<10;z++)
            b.set(x,0,z,"minecraft:stone");
        // 90 populated positions: 50 stone, 40 grass, 10 AIR.
        // Interior AIR holes remain part of the anchored footprint.
        // Stone is 55.55% of OCCUPIED but only 50% of its true 10x10
        // footprint, so the >=51% majority rule must reject it.
        for(int x=0;x<10;x++)for(int z=0;z<10;z++){
            int i=z*10+x;
            b.set(x,0,z,i<50?"minecraft:stone":"minecraft:grass_block");
        }
        for(int z=5;z<=7;z++)for(int x=3;x<=5;x++)
            b.set(x,0,z,"minecraft:air");
        b.set(4,0,8,"minecraft:air");
        var scan=FoundationPlan.analyze(b,Set.of());
        assertEquals(0,scan.qualifyingLayers());
        assertTrue(scan.omissions().isEmpty());
        assertTrue(scan.reason().contains("51%"));
    }

    @Test void acceptsUsersExactStoneAndGrassCommand(){
        var actual=ConstructionOptions.parse(
            "foundation:no:stone, grass_block hollow:yes");
        assertFalse(actual.foundation());
        assertTrue(actual.hollow());
        assertFalse(actual.excavate());
        assertEquals(Set.of("minecraft:stone","minecraft:grass_block"),
            actual.foundationMaterials());
    }


    @Test void decorativeSixSidedAirPocketDoesNotProtectFiveStoneFoundationLayers(){
        Blueprint b=new Blueprint("Decoration above floating stone base",18,13,18);
        for(int y=0;y<5;y++)for(int x=0;x<18;x++)for(int z=0;z<18;z++)
            b.set(x,y,z,"minecraft:stone");
        // A hollow six-face leaf/decor pocket high ABOVE the foundation
        // appears enclosed to the normal hollow:yes air classifier, but
        // DOES NOT make a load-bearing wall or basement. The previous
        // projected-air algorithm left a full-height STONE PILLAR below it.
        b.set(8,7,8,"minecraft:oak_leaves");
        b.set(8,9,8,"minecraft:oak_leaves");
        b.set(7,8,8,"minecraft:oak_leaves");
        b.set(9,8,8,"minecraft:oak_leaves");
        b.set(8,8,7,"minecraft:oak_leaves");
        b.set(8,8,9,"minecraft:oak_leaves");
        assertTrue(InteriorHollowPlan.enclosedAir(b).get(b.index(8,8,8)),
            "A fully enclosed decorative AIR pocket may correctly be considered hollow AIR");
        var result=FoundationPlan.analyze(b,Set.of());
        assertEquals(5,result.qualifyingLayers());
        assertEquals(5*18*18,result.omissions().size(),
            "A decorative air pocket must not leave a floating foundation pillar");
        for(int y=0;y<5;y++)
            assertTrue(result.omissions().contains(b.index(8,y,8)),
                "The stone under decorative AIR remains disposable at layer "+y);
    }

    @Test void allDisqualifiedPlatformStoneIsFilteredFromConstructionOrder(){
        Blueprint b=new Blueprint("Mixed corner defects",12,10,12);
        for(int y=0;y<6;y++)for(int x=0;x<12;x++)for(int z=0;z<12;z++)
            b.set(x,y,z,"minecraft:stone");
        b.set(6,8,6,"minecraft:oak_leaves");
        var skipped=FoundationPlan.analyze(b,Set.of()).omissions();
        assertEquals(6*12*12,skipped.size());
        var selected=b.ordered().stream()
            .filter(pos->!skipped.contains(b.index(pos.getX(),pos.getY(),pos.getZ())))
            .toList();
        assertEquals(1,selected.size(),"No skipped stone can be placed by the filtered order");
        assertEquals("minecraft:oak_leaves",b.get(
            selected.get(0).getX(),selected.get(0).getY(),selected.get(0).getZ()));
    }

    @Test void floatingStoneBasementAndGrassTopAreBothRemovedWhenEachLayerQualifies(){
        Blueprint b=new Blueprint("Floating stone basement",14,9,16);
        for(int x=0;x<b.width;x++)for(int z=0;z<b.length;z++){
            b.set(x,0,z,"minecraft:stone");
            b.set(x,1,z,"minecraft:stone");
            b.set(x,2,z,"minecraft:grass_block");
        }
        // Above the continuous platform: genuine asymmetric architecture.
        for(int y=3;y<8;y++)for(int x=3;x<=11;x++)
            b.set(x,y,4,"minecraft:stone_bricks");
        var flags=ConstructionOptions.parse("foundation:no:stone, grass_block hollow:yes");
        var analysis=FoundationPlan.analyze(b,flags.foundationMaterials());
        assertEquals(3*14*16,analysis.omissions().size(),
            "All fully occupied stone and grass layers are omitted");
        assertEquals(3,analysis.qualifyingLayers());
        assertEquals(3,analysis.stoppedAtY());
        assertTrue(analysis.reason().contains("90%"),
            "Latest diagnostics must explain the 90% footprint gate");
        assertFalse(analysis.omissions().contains(b.index(5,3,4)),
            "The temple itself is left intact");
    }


    @Test void narrowLowestStoneSupportsCannotShrinkBroadFoundationFootprint(){
        Blueprint b=new Blueprint("Wide moss platform with hanging stone tails",30,16,30);
        // Lowest occupied level is JUST two disconnected stone columns.
        // Above them sit five full, 24x24 stone/moss foundation layers.
        for(int z=5;z<9;z++)for(int x=5;x<8;x++)
            b.set(x,0,z,"minecraft:stone");
        for(int z=17;z<20;z++)for(int x=19;x<22;x++)
            b.set(x,0,z,"minecraft:stone");
        for(int y=1;y<=5;y++)for(int z=3;z<27;z++)for(int x=3;x<27;x++)
            b.set(x,y,z,y==5?"minecraft:moss_block":"minecraft:stone");
        // Upper temple only starts AFTER foundation layers.
        for(int y=6;y<=12;y++)for(int z=6;z<=23;z++){
            b.set(6,y,z,"minecraft:stone_bricks");
            b.set(23,y,z,"minecraft:stone_bricks");
        }
        var analysis=FoundationPlan.analyze(b,Set.of());
        assertEquals(5,analysis.qualifyingLayers(),
            "Recognize five broad main foundation layers, not tiny lowest struts");
        assertEquals(5*24*24+21,analysis.omissions().size(),
            "Remove all broad layers plus the 21 vertically attached low stone tails");
        assertTrue(analysis.reason().contains("anchored at schematic Y=1"));
        assertTrue(analysis.reason().contains("support-tail cells 21"));
        assertTrue(analysis.omissions().contains(b.index(6,0,6)));
        assertTrue(analysis.omissions().contains(b.index(20,0,18)));
        assertTrue(analysis.omissions().contains(b.index(25,5,25)));
        assertFalse(analysis.omissions().contains(b.index(6,7,10)),
            "Real upper masonry must remain intact");
    }

    @Test void loneWiderUpperFloorDoesNotReplaceRealRepeatedBottomFoundation(){
        Blueprint b=new Blueprint("Two real base layers and a wide one-off terrace",30,13,30);
        for(int y=0;y<2;y++)for(int z=5;z<17;z++)for(int x=5;x<17;x++)
            b.set(x,y,z,"minecraft:stone");
        for(int z=2;z<28;z++)for(int x=2;x<28;x++)
            b.set(x,6,z,"minecraft:stone");
        var analysis=FoundationPlan.analyze(b,Set.of());
        assertTrue(analysis.reason().contains("anchored at schematic Y=0"),
            "A decorative standalone upper floor must not expand the foundation footprint");
        assertEquals(2,analysis.qualifyingLayers());
        assertTrue(analysis.omissions().contains(b.index(6,0,6)));
        assertFalse(analysis.omissions().contains(b.index(6,6,6)));
    }

    @Test void foundationNoRemovesAllSixMajorityLayersBelowWallsAndDecorations(){
        Blueprint b=new Blueprint("Temple on intrusive stone and moss platform",24,17,24);
        for(int y=0;y<6;y++)for(int z=1;z<23;z++)for(int x=1;x<23;x++)
            b.set(x,y,z,y==5?"minecraft:moss_block":"minecraft:stone");
        // Real upper building is visually rich and includes paired walls,
        // columns, leaves, pistons, and a six-faced AIR pocket. None of that
        // can protect a 6-block-high stone pillar BELOW the foundation.
        for(int y=6;y<14;y++)for(int z=5;z<=18;z++){
            b.set(5,y,z,"minecraft:stone_bricks");
            b.set(18,y,z,"minecraft:stone_bricks");
        }
        b.set(8,12,8,"minecraft:oak_leaves");
        b.set(8,14,8,"minecraft:oak_leaves");
        b.set(7,13,8,"minecraft:oak_leaves");
        b.set(9,13,8,"minecraft:oak_leaves");
        b.set(8,13,7,"minecraft:oak_leaves");
        b.set(8,13,9,"minecraft:oak_leaves");
        var result=FoundationPlan.analyze(b,Set.of());
        assertEquals(6,result.qualifyingLayers());
        assertEquals(6*22*22,result.omissions().size(),
            "Remove all six fully qualifying layers; no vertical pillar exemptions");
        for(int y=0;y<6;y++){
            assertTrue(result.omissions().contains(b.index(5,y,10)),
                "Omit stone directly under temple wall at Y="+y);
            assertTrue(result.omissions().contains(b.index(8,y,8)),
                "Omit stone under six-sided decorative cavity at Y="+y);
        }
        assertFalse(result.omissions().contains(b.index(5,7,10)),
            "Keep the real aboveground wall");
    }

    @Test void diagnosticExplainsRejectionBelowNinetyPercentCoverage(){
        Blueprint b=new Blueprint("One air gap breaks coverage",12,7,12);
        for(int x=0;x<12;x++)for(int z=0;z<12;z++)
            b.set(x,0,z,"minecraft:stone");
        // 15 interior holes in a 12x12 anchored footprint leave
        // 129/144 = 89.58% occupancy, just below the 90% requirement.
        for(int z=3;z<=7;z++)for(int x=3;x<=5;x++)
            b.set(x,0,z,"minecraft:air");
        var diagnosis=FoundationPlan.analyze(b,Set.of("minecraft:stone"));
        assertTrue(diagnosis.omissions().isEmpty());
        assertEquals(0,diagnosis.stoppedAtY());
        assertTrue(diagnosis.summary().contains("129/144"));
        assertTrue(diagnosis.summary().contains("at least 90%"));
        assertTrue(diagnosis.summary().contains("stone"));
    }

    @Test void diagnosticExplainsMixedMaterialWithoutSingle51PercentMajority(){
        Blueprint b=new Blueprint("Mixed base",10,4,10);
        for(int z=0;z<10;z++)for(int x=0;x<10;x++){
            int i=z*10+x;
            b.set(x,0,z,i<40?"minecraft:stone":
                i<80?"minecraft:grass_block":"minecraft:gold_block");
        }
        var diagnosis=FoundationPlan.analyze(b,Set.of("minecraft:stone",
            "minecraft:grass_block"));
        assertTrue(diagnosis.omissions().isEmpty(),
            "Two distinct 40% block types may not combine into one 51% majority");
        assertTrue(diagnosis.reason().contains("no single eligible"));
        assertTrue(diagnosis.reason().contains("40.00%"));
    }

    @Test void diagnosticNamesDominantUnrecognizedMaterial(){
        Blueprint b=new Blueprint("Nonstandard underside",8,5,8);
        for(int x=0;x<8;x++)for(int z=0;z<8;z++)
            b.set(x,0,z,"custommod:polished_rock");
        var diagnosis=FoundationPlan.analyze(b,Set.of("minecraft:stone",
            "minecraft:grass_block"));
        assertTrue(diagnosis.omissions().isEmpty());
        assertTrue(diagnosis.reason().contains("custommod:polished_rock"));
        assertEquals(64,FoundationPlan.analyze(b,
            Set.of("custommod:polished_rock")).omissions().size());
    }
}
