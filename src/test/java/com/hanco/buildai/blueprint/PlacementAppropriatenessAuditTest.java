package com.hanco.buildai.blueprint;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure geometry tests. NOT Minecraft redstone gameplay/simulation tests. */
public class PlacementAppropriatenessAuditTest {
    private static Blueprint layout(){
        Blueprint b=new Blueprint("Electrical bench",14,5,10);
        for(int z=0;z<10;z++)for(int x=0;x<14;x++)
            b.set(x,0,z,"minecraft:stone_bricks");
        return b;
    }

    @Test void flagsXorLampSeparatedFromSignalByOneAirCell(){
        Blueprint b=layout();
        b.set(4,1,4,"minecraft:redstone_wire");
        b.set(6,1,4,"minecraft:redstone_lamp");
        var found=PlacementAppropriatenessAudit.inspect(b,"Build a functional XOR gate");
        assertTrue(found.blockers().stream().anyMatch(x->
            x.contains("Lamp (6,1,4)")&&x.contains("one-cell AIR gap")&&
            x.contains("(5,1,4)")),found.toString());
    }

    @Test void acceptsDirectDustRepeaterAndValidlyPoweredConductor(){
        Blueprint b=layout();
        b.set(4,1,4,"minecraft:redstone_wire");
        b.set(5,1,4,"minecraft:redstone_lamp");
        assertTrue(PlacementAppropriatenessAudit.inspect(b,"XOR gate").blockers().isEmpty());
        b.set(4,1,4,"minecraft:repeater[facing=west,powered=false]");
        assertTrue(PlacementAppropriatenessAudit.inspect(b,"XOR gate").blockers().isEmpty());
        // A conductor between a powered repeater and a lamp is legal.
        b.set(5,1,4,"minecraft:stone_bricks");
        b.set(6,1,4,"minecraft:redstone_lamp");
        assertTrue(PlacementAppropriatenessAudit.inspect(b,"XOR gate").blockers().isEmpty());
    }

    @Test void flagsRepeaterPointingAwayButDoesNotFlipIt(){
        Blueprint b=layout();
        b.set(4,1,4,"minecraft:repeater[facing=east,powered=false]");
        b.set(5,1,4,"minecraft:redstone_lamp");
        var found=PlacementAppropriatenessAudit.inspect(b,"XOR gate");
        assertTrue(found.blockers().stream().anyMatch(x->
            x.contains("Lamp (5,1,4)")),found.toString());
        assertEquals("minecraft:repeater[facing=east,powered=false]",
            b.get(4,1,4));
    }

    @Test void supportsAllFourRepeaterOrientations(){
        String[] facing={"north","south","east","west"};
        int[][] offsets={{0,1},{0,-1},{-1,0},{1,0}};
        for(int i=0;i<4;i++){
            Blueprint b=layout();
            b.set(7,1,5,"minecraft:repeater[facing="+facing[i]+"]");
            b.set(7+offsets[i][0],1,5+offsets[i][1],
                "minecraft:redstone_lamp");
            assertTrue(PlacementAppropriatenessAudit.inspect(b,"Redstone circuit")
                .blockers().isEmpty(),"facing="+facing[i]);
        }
    }

    @Test void identifiesSupportlessWireAndWallTorch(){
        Blueprint b=layout();
        b.set(2,2,2,"minecraft:redstone_wire");
        b.set(6,2,6,"minecraft:redstone_wall_torch[facing=east]");
        var findings=PlacementAppropriatenessAudit.inspect(b,"functional redstone");
        assertTrue(findings.suggestions().stream().anyMatch(x->
            x.contains("AIR beneath")),findings.toString());
        assertTrue(findings.suggestions().stream().anyMatch(x->
            x.contains("no support")),findings.toString());
    }

    @Test void doesNotForceDecorativeUnpoweredLampIntoAIRepair(){
        Blueprint b=layout();
        b.set(6,1,4,"minecraft:redstone_lamp");
        var findings=PlacementAppropriatenessAudit.inspect(b,
            "Build a decorative art display featuring an unpowered lamp");
        assertTrue(findings.blockers().isEmpty(),findings.toString());
    }

    @Test void leavesExternalBoundaryWiringUnverifiedInsteadOfBlocking(){
        Blueprint b=layout();
        b.set(0,1,4,"minecraft:redstone_lamp");
        var findings=PlacementAppropriatenessAudit.inspect(b,"Redstone circuit");
        assertTrue(findings.blockers().isEmpty(),findings.toString());
        assertTrue(findings.suggestions().stream().anyMatch(x->
            x.contains("boundary")),findings.toString());
    }
}
