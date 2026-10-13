package com.hanco.buildai.blueprint;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class RedstoneLogicAuditTest {
    @Test void flagsUncontrolledTorchTouchingOutputLamp(){
        Blueprint b=new Blueprint("Uncontrolled inverter",8,5,8);
        b.set(3,0,3,"minecraft:stone_bricks");
        b.set(3,1,3,"minecraft:redstone_torch[lit=true]");
        b.set(4,1,3,"minecraft:redstone_lamp[lit=false]");
        List<String> issues=RedstoneLogicAudit.inspect(b,
            "Create a 3-input AND logic gate with a lamp");
        assertTrue(issues.stream().anyMatch(x->x.contains("ALWAYS ON")),issues.toString());
    }
    @Test void potentialRealControlAvoidsFalseAlwaysOnFinding(){
        Blueprint b=new Blueprint("Inverter support",8,5,8);
        b.set(3,0,3,"minecraft:stone_bricks");
        b.set(3,1,3,"minecraft:redstone_torch");
        b.set(4,1,3,"minecraft:redstone_lamp");
        b.set(2,0,3,"minecraft:redstone_wire");
        List<String> issues=RedstoneLogicAudit.inspect(b,
            "Create a 3-input AND logic gate with a lamp");
        assertTrue(issues.stream().noneMatch(x->x.contains("ALWAYS ON")),issues.toString());
    }
    @Test void correctlyTracksWallTorchAttachmentInAllFourDirections(){
        String[] facing={"north","south","east","west"};
        int[][] support={{0,0,1},{0,0,-1},{-1,0,0},{1,0,0}};
        for(int i=0;i<facing.length;i++){
            Blueprint b=new Blueprint("wall",9,5,9);
            b.set(4,2,4,"minecraft:redstone_wall_torch[facing="+facing[i]+",lit=true]");
            b.set(4,3,4,"minecraft:redstone_lamp");
            int x=4+support[i][0],y=2,z=4+support[i][2];
            b.set(x,y,z,"minecraft:stone");
            var warnings=RedstoneLogicAudit.inspect(b,"XOR gate");
            assertTrue(warnings.stream().anyMatch(issue->issue.contains(
                "attachment block at "+x+","+y+","+z)),warnings.toString());
        }
    }
    @Test void noWarningsForDecorativeHouseAndNoFalseCertainty(){
        Blueprint b=new Blueprint("House",8,5,8);
        b.set(2,1,2,"minecraft:redstone_torch");
        b.set(3,1,2,"minecraft:redstone_lamp");
        assertTrue(RedstoneLogicAudit.inspect(b,"Build a stone house").isEmpty());
    }
    @Test void warnsWhenXorOnlyContainsJoinedDustAndRepeaters(){
        Blueprint b=new Blueprint("joined lines",8,4,8);
        b.set(1,1,1,"minecraft:lever");
        b.set(2,1,1,"minecraft:redstone_wire");
        b.set(3,1,1,"minecraft:repeater[facing=west,delay=1]");
        b.set(4,1,1,"minecraft:redstone_lamp");
        var warnings=RedstoneLogicAudit.inspect(b,"Two-lever XOR circuit");
        assertTrue(warnings.stream().anyMatch(x->x.contains("only transmit/delay")),
            warnings.toString());
    }
}
