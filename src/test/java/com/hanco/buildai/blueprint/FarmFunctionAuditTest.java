package com.hanco.buildai.blueprint;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class FarmFunctionAuditTest {
    private static Blueprint carrots(){
        Blueprint b=new Blueprint("Carrots",14,8,14);
        for(int x=2;x<11;x++){
            b.set(x,2,5,"minecraft:farmland[moisture=7]");
            b.set(x,3,5,"minecraft:carrots[age=7]");
        }
        b.set(6,2,4,"minecraft:water[level=0]"); // hydration only, BELOW crop Y
        b.set(6,3,2,"minecraft:dispenser[facing=south,triggered=false]");
        b.set(6,1,9,"minecraft:hopper[facing=down,enabled=true]");
        b.set(6,0,9,"minecraft:chest[facing=north,type=single,waterlogged=false]");
        return b;
    }
    @Test void flagsBonemealDispensersAndStaticHydrationAsNotHarvesting(){
        var report=FarmFunctionAudit.inspect(carrots(),
            "Create an automated carrot farm with harvest and collection",null);
        assertTrue(report.needsRepair());
        assertTrue(report.warnings().stream().anyMatch(s->s.contains("Soil hydration")),report.warnings().toString());
        assertTrue(report.warnings().stream().anyMatch(s->s.contains("bone meal")),report.warnings().toString());
        assertTrue(report.warnings().stream().anyMatch(s->s.contains("replanting")),report.warnings().toString());
    }
    @Test void provenPistonObserverCaneIsNotFalselyRejected(){
        Blueprint b=new Blueprint("Cane",12,8,12);
        for(int z=2;z<9;z++){
            b.set(3,1,z,"minecraft:sugar_cane[age=0]");
            b.set(4,2,z,"minecraft:piston[facing=west,extended=false]");
            b.set(4,3,z,"minecraft:observer[facing=west,powered=false]");
        }
        b.set(2,1,5,"minecraft:hopper[facing=down,enabled=true]");
        b.set(2,0,5,"minecraft:chest");
        var report=FarmFunctionAudit.inspect(b,
            "Automated sugar cane farm with observers, pistons and hoppers",null);
        assertFalse(report.needsRepair(),report.warnings().toString());
    }
    @Test void rejectsRemovingCropsAndChangingScaleAsFakeRepair(){
        Blueprint first=carrots();
        var initial=FarmFunctionAudit.inspect(first,"automatic carrot farm",null);
        Blueprint emptied=new Blueprint("Empty",first.width,first.height,first.length);
        assertFalse(FarmFunctionAudit.improves(initial,
            FarmFunctionAudit.inspect(emptied,"automatic carrot farm",null),first,emptied));
        Blueprint shrunken=new Blueprint("Smaller",13,8,14);
        assertFalse(FarmFunctionAudit.improves(initial,
            FarmFunctionAudit.inspect(shrunken,"automatic carrot farm",null),first,shrunken));
    }
    @Test void detectsRootHarvestNeedDespiteDispenserAndHoppers(){
        var notes=FarmFunctionAudit.inspect(carrots(),"automated farm",null);
        assertTrue(notes.needsRepair());
    }
    @Test void unrelatedArchitectureIsNotFlagged(){
        Blueprint b=new Blueprint("House",10,6,10);
        b.set(2,1,2,"minecraft:dispenser[facing=east]");
        assertTrue(FarmFunctionAudit.inspect(b,"A modern house",null).warnings().isEmpty());
    }
}
