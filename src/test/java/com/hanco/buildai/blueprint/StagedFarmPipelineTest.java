package com.hanco.buildai.blueprint;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;

public final class StagedFarmPipelineTest {
    private static final String REQUEST="""
        Build a multi-story automated farm with a single master button.
        - Floor 1: Carrot farm, 8x20 planted crops with 9x20 patch.
        - Floor 2: Potato farm, 8x20 planted crops.
        - Floor 3: Sugar cane farm, at least 160 planted stalks.
        - Ground floor: separate dedicated storage and stairs.
        Use redstone, correct fluid flow, pistons and collection.
        """;
    @Test void realFourStoryFarmIsSegmentedWithoutShrinkingPlot(){
        assertTrue(StagedFarmPlan.applies(REQUEST));
        var p=StagedFarmPlan.from(REQUEST);
        assertEquals(5,p.stages().size());
        assertTrue(p.width()>=27 && p.length()>=35 && p.height()>=41);
        assertEquals(9,p.stages().get(1).firstY());
        assertEquals(17,p.stages().get(1).lastY());
        assertEquals(18,p.stages().get(2).firstY());
        assertEquals(27,p.stages().get(3).firstY());
        for(int i=0;i<p.stages().size();i++){
            String scoped=p.stagePrompt(REQUEST,p.stages().get(i),i);
            assertTrue(scoped.contains(REQUEST.trim()));
            assertTrue(scoped.contains("GLOBAL coordinates"));
            assertTrue(scoped.length()<=4000);
        }
    }
    @Test void unrelatedBuildingsUseExistingFastArchitect(){
        assertFalse(StagedFarmPlan.applies("Build a castle with four towers."));
        assertFalse(StagedFarmPlan.applies("Build a normal compact farm."));
        assertFalse(StagedFarmPlan.applies("Floor 1: Carrots\nFloor 2: Potatoes"));
    }
    private static String payload(StagedFarmPlan p,String name,int y){
        return """
            {"name":"%s","width":%d,"height":%d,"length":%d,
             "operations":[{"kind":"fill","block":"minecraft:stone_bricks",
               "x1":1,"y1":%d,"z1":1,"x2":3,"y2":%d,"z2":3}]}
            """.formatted(name,p.width(),p.height(),p.length(),y,y);
    }
    @Test void modulesMergeIntoOneRealSchematicWithGlobalCoordinates(){
        var p=StagedFarmPlan.from(REQUEST);
        var work=new StagedFarmAssembler.Assembly(p);
        for(int i=0;i<p.stages().size();i++){
            var stage=p.stages().get(i);
            int y=stage.fullBounds()?i:stage.firstY();
            work.add(stage,payload(p,"Unique Farm",y));
            assertEquals(i+1,work.completed());
        }
        String full=work.finish();
        var compiled=ProceduralGenerator.compile(full);
        assertEquals(p.width(),compiled.width);
        assertEquals(p.height(),compiled.height);
        assertEquals(p.length(),compiled.length);
        assertEquals("minecraft:stone_bricks",compiled.get(1,27,1));
        assertEquals("minecraft:stone_bricks",compiled.get(1,18,1));
        assertTrue(full.contains("staged_modules"));
    }
    @Test void stageCannotWriteIntoAnotherFloor(){
        var p=StagedFarmPlan.from(REQUEST);
        var work=new StagedFarmAssembler.Assembly(p);
        work.add(p.stages().get(0),payload(p,"Shell",0));
        IllegalArgumentException ex=assertThrows(IllegalArgumentException.class,()->
            work.add(p.stages().get(1),payload(p,"Invalid floor",18)));
        assertTrue(ex.getMessage().contains("outside assigned"));
        assertEquals(1,work.completed());
    }
    @Test void cannotClaimSuccessOnIncompleteModulesOrShrunkFootprint(){
        var p=StagedFarmPlan.from(REQUEST);
        var work=new StagedFarmAssembler.Assembly(p);
        assertThrows(IllegalStateException.class,work::finish);
        String wrong=payload(p,"Small",9).replace(
            "\"width\":"+p.width(),"\"width\":12");
        work.add(p.stages().get(0),payload(p,"Shell",0));
        assertThrows(IllegalArgumentException.class,()->
            work.add(p.stages().get(1),wrong));
    }
    @Test void retainCommandPlanAndSetupWarningsFromAllStages(){
        var p=StagedFarmPlan.from(REQUEST);
        var merged=new StagedFarmAssembler.Assembly(p);
        for(int i=0;i<p.stages().size();i++){
            String part=payload(p,"Test",p.stages().get(i).firstY());
            if(i==2)part=part.substring(0,part.length()-2)+
                ",\"limitations\":[\"Check item transfer\"]}";
            merged.add(p.stages().get(i),part);
        }
        assertTrue(merged.finish().contains("Check item transfer"));
    }
}
