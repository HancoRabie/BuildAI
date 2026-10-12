package com.hanco.buildai.blueprint;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public final class RailEconomyAuditTest {
    @Test void warnsOnCoasterBuiltEntirelyFromPoweredRailsAndPerRailRedstoneBlocks(){
        Blueprint b=new Blueprint("Wasted resources",32,4,4);
        for(int x=0;x<24;x++){
            b.set(x,1,1,"minecraft:powered_rail[shape=east_west,powered=true,waterlogged=false]");
            b.set(x,0,1,"minecraft:redstone_block");
        }
        var warnings=RedstoneAudit.inspect(b,"Build a roller coaster",null);
        assertTrue(warnings.stream().anyMatch(s->s.contains("Most roller coasters")),
            warnings.toString());
        assertTrue(warnings.stream().anyMatch(s->s.contains("under each rail")),
            warnings.toString());
    }
    @Test void defaultCoasterUsesMostlyOrdinaryRailsWithOccasionalBoosters(){
        Blueprint b=new Blueprint("Efficient coaster",32,4,4);
        for(int x=0;x<24;x++)b.set(x,1,1,"minecraft:rail[shape=east_west,waterlogged=false]");
        b.set(3,1,1,"minecraft:powered_rail[shape=east_west,powered=false,waterlogged=false]");
        var warnings=RedstoneAudit.inspect(b,"Build a roller coaster",null);
        assertTrue(warnings.stream().noneMatch(s->s.contains("Most roller coasters")),
            warnings.toString());
        assertTrue(warnings.stream().noneMatch(s->s.contains("under each rail")),
            warnings.toString());
    }
    @Test void explicitAllPoweredRequestSkipsOverprovisionWarning(){
        Blueprint b=new Blueprint("Intentional ride",32,4,4);
        for(int x=0;x<24;x++)b.set(x,1,1,"minecraft:powered_rail");
        var warnings=RedstoneAudit.inspect(b,
            "Build a roller coaster with all rails powered",null);
        assertTrue(warnings.stream().noneMatch(s->s.contains("Most roller coasters")),
            warnings.toString());
    }
}
