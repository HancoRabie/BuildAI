package com.hanco.buildai.blueprint;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ChainGapAuditTest {
    @Test void warnsWhenChainSegmentsHaveAirGapButDoesNotChangeBlueprint() {
        Blueprint b=new Blueprint("Chain test",4,3,4);
        b.set(0,1,1,"minecraft:chain[axis=x,waterlogged=false]");
        b.set(2,1,1,"minecraft:chain[axis=x,waterlogged=false]");
        assertTrue(ChainGapAudit.inspect(b).stream().anyMatch(x->x.contains("one-block gaps")));
        assertEquals("minecraft:air",b.get(1,1,1));
    }
    @Test void continuousOrDifferentlyOrientedChainsAreNotAutomaticallyFlagged() {
        Blueprint b=new Blueprint("Complete",4,3,4);
        b.set(0,1,1,"minecraft:chain[axis=x,waterlogged=false]");
        b.set(1,1,1,"minecraft:chain[axis=x,waterlogged=false]");
        b.set(2,1,1,"minecraft:chain[axis=x,waterlogged=false]");
        assertTrue(ChainGapAudit.inspect(b).isEmpty());
    }
}
