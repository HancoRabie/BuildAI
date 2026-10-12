package com.hanco.buildai.blueprint;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BuildRequirementAuditTest {
    @Test void castleWithoutFunctionalMachineDoesNotRequireHoppers(){
        Blueprint b=new Blueprint("Castle",12,8,12);
        b.set(1,1,1,"minecraft:stone_bricks");
        var result=BuildRequirementAudit.inspect(b,"Build a stone castle",null);
        assertTrue(result.blockers().isEmpty(),result.warnings().toString());
    }
    @Test void reportsMissingButtonInAnyKindOfRequestedBuild(){
        Blueprint b=new Blueprint("Weather console",10,6,10);
        b.set(1,1,1,"minecraft:command_block");
        var result=BuildRequirementAudit.inspect(b,
            "Build a working weather station with a button",null);
        assertTrue(result.blockers().stream().anyMatch(s->s.contains("button")),
            result.warnings().toString());
    }
    @Test void catchesIncorrectDirectedItemOutputsInGeneralStorageSystems(){
        Blueprint b=new Blueprint("Storage",8,5,8);
        b.set(2,2,2,"minecraft:hopper[facing=north,enabled=true]");
        b.set(2,1,2,"minecraft:chest");
        var wrong=BuildRequirementAudit.inspect(b,
            "Make a compact item sorting storage system with hoppers",null);
        assertTrue(wrong.blockers().stream().anyMatch(s->s.contains("directed hopper")));
        b.set(2,2,2,"minecraft:hopper[facing=down,enabled=true]");
        var fixed=BuildRequirementAudit.inspect(b,
            "Make a compact item sorting storage system with hoppers",null);
        assertFalse(fixed.blockers().stream().anyMatch(s->s.contains("directed hopper")),
            fixed.warnings().toString());
    }
    @Test void manifestEvidenceMustMatchActualVoxel(){
        Blueprint b=new Blueprint("Bridge",8,6,8);
        b.set(3,2,3,"minecraft:stone_bricks");
        String missing="""
            {"requirements":[{"description":"Control station","status":"missing"}]}
            """;
        var unmet=BuildRequirementAudit.inspect(b,"Make a control station",missing);
        assertTrue(unmet.suggestions().stream().anyMatch(x->x.contains("missing")),unmet.warnings().toString());
        String falseClaim="""
            {"requirements":[{"description":"Lever panel","status":"implemented",
              "evidence":[{"x":3,"y":2,"z":3,"block":"minecraft:lever"}]}]}
            """;
        var invalid=BuildRequirementAudit.inspect(b,"Bridge with detail",falseClaim);
        assertTrue(invalid.suggestions().stream().anyMatch(s->s.contains("UNVERIFIED")));
    }
    @Test void cannotImproveByShrinkingAnUnfinishedDesign(){
        Blueprint before=new Blueprint("Build",8,5,8);
        before.set(1,1,1,"minecraft:stone");
        Blueprint empty=new Blueprint("Empty",7,5,8);
        assertFalse(BuildRequirementAudit.improves(
            BuildRequirementAudit.inspect(before,"one button",null),
            BuildRequirementAudit.inspect(empty,"one button",null),before,empty));
    }
}
