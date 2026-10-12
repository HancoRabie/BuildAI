package com.hanco.buildai.blueprint;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public final class InspirationEvidenceTest {
    private static JsonObject json(String source){
        return JsonParser.parseString(source).getAsJsonObject();
    }
    private static final String TECHNIQUES="""
        MASSING: Vary building mass in overlapping volumes that match the player's program and scale.
        DEPTH: Recess openings and offset facade layers to create readable light and shadow.
        MATERIALS: Anchor the palette in one primary family with selectively contrasted trim.
        DETAILS: Use stair, slab, and fence transitions where they articulate real structural edges.
        LANDSCAPE: Extend roots, terrain and footpaths into the structure's own site composition.
        ACCESS: Keep accessible paths and headroom while hiding circulation within the architecture.
        """;

    private static JsonObject researched(String notes) {
        JsonObject payload=json("""
          {"status":"completed","output":[
            {"type":"web_search_call","status":"completed","action":{"type":"search","sources":[
              {"title":"Example One","url":"https://minecraftbuilds.example/tree"},
              {"title":"Example Two","url":"https://builders.example/castle"},
              {"title":"Example Three","url":"https://minecraftbuilds.example/arch"}]}},
            {"type":"message","content":[{"type":"output_text","text":""}]}
          ]}
          """);
        payload.getAsJsonArray("output").get(1).getAsJsonObject()
            .getAsJsonArray("content").get(0).getAsJsonObject().addProperty("text",notes);
        return payload;
    }

    @Test void combinesMultipleSitesAndOnlyPassesAbstractTechniquesToGenerator() {
        InspirationEvidence.Brief brief=InspirationEvidence.parse(researched(TECHNIQUES));
        assertTrue(brief.verified());
        assertEquals(3,brief.sources().size());
        String context=brief.promptContext();
        assertTrue(context.contains("MASSING: Vary building mass"));
        assertTrue(context.contains("player's requested purpose"));
        assertTrue(context.contains("Do NOT reproduce any referenced layout"));
        assertFalse(context.contains("https://"),"Cited websites belong only in UI, not generation prompt");
        assertFalse(context.contains("Example One"),"Source-specific build titles should not steer design");
    }

    @Test void aSingleInternetBuildCannotBecomeTheDesignTemplate() {
        var response=json("""
            {"status":"completed","output":[
              {"type":"web_search_call","action":{"type":"search","sources":[
                {"title":"A Great Castle","url":"https://builders.example/great-castle"}]}},
              {"type":"message","content":[{"type":"output_text","text":""}]}]}
            """);
        response.getAsJsonArray("output").get(1).getAsJsonObject()
            .getAsJsonArray("content").get(0).getAsJsonObject().addProperty("text",TECHNIQUES);
        assertFalse(InspirationEvidence.parse(response).verified());
    }

    @Test void threeRepostsOnOneDomainDoNotCountAsIndependentResearch(){
        var response=json("""
            {"status":"completed","output":[
              {"type":"web_search_call","action":{"type":"search","sources":[
                {"url":"https://single.example/1"},
                {"url":"https://single.example/2"},
                {"url":"https://single.example/3"}]}},
              {"type":"message","content":[{"type":"output_text","text":""}]}]}
            """);
        response.getAsJsonArray("output").get(1).getAsJsonObject()
            .getAsJsonArray("content").get(0).getAsJsonObject().addProperty("text",TECHNIQUES);
        assertFalse(InspirationEvidence.parse(response).verified());
    }

    @Test void proseAboutSearchingWithoutActualWebToolCallIsNotEvidence() {
        var response=json("""
            {"status":"completed","output":[{"type":"message","content":[
              {"type":"output_text","text":"I used three example builds.",
               "annotations":[
                 {"type":"url_citation","url":"https://minecraftbuilds.example/1"},
                 {"type":"url_citation","url":"https://builders.example/2"},
                 {"type":"url_citation","url":"https://minecraftbuilds.example/3"}]}
            ]}]}
            """);
        assertFalse(InspirationEvidence.parse(response).verified());
    }

    @Test void sourceLinkOrCopyDirectionsInsideTechniqueAreDiscarded(){
        String notes=TECHNIQUES.replace(
            "MASSING: Vary building mass in overlapping volumes that match the player's program and scale.",
            "MASSING: Copy the exact Example One castle outline and room floorplan.")
            .replace("DEPTH: Recess openings and offset facade layers to create readable light and shadow.",
                "DEPTH: Get the 12x14 layout from https://builders.example/castle.");
        var brief=InspirationEvidence.parse(researched(notes));
        assertTrue(brief.verified(),"Four other clean techniques are still available");
        String context=brief.promptContext();
        assertFalse(context.contains("Copy the exact"));
        assertFalse(context.contains("12x14"));
        assertFalse(context.contains("https://"));
        assertTrue(context.contains("DETAILS:"));
    }

    @Test void rejectsUnstructuredSourceSummaryInsteadOfForwardingIt(){
        assertFalse(InspirationEvidence.parse(researched(
            "Builder A created a perfect treehouse. Copy its iconic balconies and canopy."
        )).verified());
    }

    @Test void failureIncompleteOrInvalidSourcesStayUnverified(){
        var source=researched(TECHNIQUES);
        source.addProperty("status","incomplete");
        assertFalse(InspirationEvidence.parse(source).verified());
        var invalid=researched(TECHNIQUES);
        var sources=invalid.getAsJsonArray("output").get(0).getAsJsonObject()
            .getAsJsonObject("action").getAsJsonArray("sources");
        sources.get(0).getAsJsonObject().addProperty("url","http://127.0.0.1:8080/");
        sources.get(1).getAsJsonObject().addProperty("url","https://localhost/private");
        assertFalse(InspirationEvidence.parse(invalid).verified());
    }

    @Test void evidenceSizeIsBoundedAndCopyableCitationRecordIsRetained(){
        String huge=TECHNIQUES+"JUNK".repeat(4000);
        InspirationEvidence.Brief brief=InspirationEvidence.parse(researched(huge));
        assertTrue(brief.verified());
        assertTrue(brief.designNotes().length()<=3000);
        assertEquals("https://minecraftbuilds.example/tree",brief.sources().get(0).url());
        assertFalse(brief.promptContext().contains("JUNK"));
    }

    @Test void manuallyConstructedSingleSourceCannotBypassVerification() {
        var brief=new InspirationEvidence.Brief(TECHNIQUES,
            List.of(new InspirationEvidence.Source("Original Castle","https://example.org/a")));
        assertFalse(brief.verified());
        assertEquals("",brief.promptContext());
    }
}
