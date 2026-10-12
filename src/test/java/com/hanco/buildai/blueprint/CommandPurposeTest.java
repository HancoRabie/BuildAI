package com.hanco.buildai.blueprint;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CommandPurposeTest {
    @Test void explainsTypicalCTFSubsystemsWithoutInventingFunctionality(){
        assertTrue(CommandPurpose.summary("scoreboard objectives add vtState dummy")
            .contains("objective"));
        assertTrue(CommandPurpose.summary("team join red @p").contains("team"));
        assertTrue(CommandPurpose.summary(
            "execute if score $phase vtState matches 1 run title @a actionbar {\"text\":\"Ready\"}")
            .contains("Check game conditions"));
        assertTrue(CommandPurpose.summary("give @p minecraft:diamond")
            .contains("loadout"));
        assertTrue(CommandPurpose.summary("time set day").contains("daytime"));
        assertTrue(CommandPurpose.summary("weather clear").contains("weather"));
    }
    @Test void unknownCommandDoesNotClaimItIsTestedOrSafe(){
        assertTrue(CommandPurpose.summary("some-other-command arg")
            .contains("inspect full command"));
    }
}
