package com.hanco.buildai.blueprint;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

public final class FluidAndRepeaterGuardTest {
    @Test void fluidSupplyIsTwoWaterBucketsPerSurvivalProjectAndLavaPerSource() {
        assertEquals(0,BuildFluidRules.requiredWaterBuckets(0));
        assertEquals(2,BuildFluidRules.requiredWaterBuckets(1));
        assertEquals(2,BuildFluidRules.requiredWaterBuckets(100));
        assertEquals(0,BuildFluidRules.requiredLavaBuckets(0));
        assertEquals(5,BuildFluidRules.requiredLavaBuckets(5));
        assertThrows(IllegalArgumentException.class,()->
            BuildFluidRules.requiredWaterBuckets(-1));
    }

    @Test void materialsUseBucketItemsAndPlaceFluidsAfterSolidAndWiring() {
        Blueprint b=new Blueprint("Farm",12,4,12);
        for(int x=0;x<8;x++)b.set(x,1,1,"minecraft:water[level=0]");
        for(int x=0;x<3;x++)b.set(x,1,3,"minecraft:lava[level=0]");
        b.set(9,1,1,"minecraft:stone_bricks");
        b.set(10,1,1,"minecraft:redstone_wire");
        var items=b.materials();
        assertEquals(2,items.get("minecraft:water_bucket"));
        assertEquals(3,items.get("minecraft:lava_bucket"));
        assertFalse(items.containsKey("minecraft:water"));
        assertFalse(items.containsKey("minecraft:lava"));
        var ordered=b.ordered();
        boolean seenFluid=false;
        for(var pos:ordered){
            boolean fluid=BuildFluidRules.isFluidSource(b.get(pos.getX(),pos.getY(),pos.getZ()));
            if(fluid)seenFluid=true;
            else assertFalse(seenFluid,"Fluids must be deferred past all geometry");
        }
    }

    @Test void bucketItemNotMistakenForSourceBlock() {
        assertTrue(BuildFluidRules.isFluidBucket("minecraft:water_bucket"));
        assertTrue(BuildFluidRules.isFluidBucket("minecraft:lava_bucket"));
        assertFalse(BuildFluidRules.isFluidSource("minecraft:water_bucket"));
        assertEquals("minecraft:water",BuildFluidRules.sourceForBucket("minecraft:water_bucket"));
        assertEquals("minecraft:lava",BuildFluidRules.sourceForBucket("minecraft:lava_bucket"));
    }

    @Test void javaRepeaterFacingPointsToInputWithOppositeOutput() {
        Blueprint b=new Blueprint("Single path",8,4,8);
        b.set(1,1,3,"minecraft:redstone_block");
        b.set(2,1,3,"minecraft:repeater[facing=east,delay=2,locked=false,powered=false]");
        b.set(3,1,3,"minecraft:redstone_lamp");
        var fixes=RedstoneFacingGuard.unambiguousReversals(b);
        assertEquals(1,fixes.size());
        assertEquals("minecraft:repeater[facing=west,delay=2,locked=false,powered=false]",
            fixes.get(0).after());
        b.set(2,1,3,fixes.get(0).after());
        assertTrue(RedstoneFacingGuard.unambiguousReversals(b).isEmpty());
    }

    @Test void ambiguousWireAndSideLockingMustNotBeAutoReversed(){
        Blueprint b=new Blueprint("Locked sequence",8,4,8);
        b.set(1,1,3,"minecraft:redstone_wire");
        b.set(2,1,3,"minecraft:repeater[facing=east,delay=2,locked=false,powered=false]");
        b.set(3,1,3,"minecraft:redstone_lamp");
        assertTrue(RedstoneFacingGuard.unambiguousReversals(b).isEmpty());
        b.set(1,1,3,"minecraft:redstone_block");
        b.set(2,1,2,"minecraft:repeater[facing=north]");
        assertTrue(RedstoneFacingGuard.unambiguousReversals(b).isEmpty(),
            "Nearby potential repeater lock must remain unmodified");
    }
}
