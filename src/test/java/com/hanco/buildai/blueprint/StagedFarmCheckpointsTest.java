package com.hanco.buildai.blueprint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

public final class StagedFarmCheckpointsTest {
    @TempDir Path root;

    @Test void failedSessionReusesValidatedStagesButNewModelOrPromptDoesNot(){
        var first=new StagedFarmCheckpoints(root,"Original huge farm","gpt-6","rules-v1");
        first.begin();
        assertTrue(first.save(0,"{\"operations\":[1]}"));
        assertTrue(first.save(1,"{\"operations\":[2]}"));
        var restarted=new StagedFarmCheckpoints(root,"Original huge farm","gpt-6","rules-v1");
        restarted.begin();
        assertEquals("{\"operations\":[1]}",restarted.load(0));
        assertEquals("{\"operations\":[2]}",restarted.load(1));
        var changed=new StagedFarmCheckpoints(root,"More crop floors","gpt-6","rules-v1");
        changed.begin();
        assertNull(changed.load(0));
        var newerModel=new StagedFarmCheckpoints(root,"Original huge farm","gpt-6.1","rules-v1");
        newerModel.begin();
        assertNull(newerModel.load(0));
    }
    @Test void successfulProjectNeverLocksARegenerateOfSamePrompt(){
        var stages=new StagedFarmCheckpoints(root,"Some specific farm","model","rules");
        stages.begin();
        assertTrue(stages.save(0,"{\"design\":\"first result\"}"));
        stages.complete();
        stages.begin();
        assertNull(stages.load(0),"Remake must start fresh after successful generation");
    }
    @Test void incompleteOrOversizedSaveCannotOverwritePreviousProgress(){
        var store=new StagedFarmCheckpoints(root,"Farm","model","rules");
        store.begin();
        assertTrue(store.save(2,"stable stage"));
        assertFalse(store.save(2,"x".repeat(1_450_001)));
        assertEquals("stable stage",store.load(2));
        assertThrows(IllegalArgumentException.class,()->store.load(16));
    }
}
