package com.hanco.buildai.blueprint;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public final class InspirationEvidenceTest {
    private static JsonObject json(String source){
        return JsonParser.parseString(source).getAsJsonObject();
    }

    @Test void acceptsActualHostedSearchWithCitedExamples(){
        JsonObject response=json("""
            {"status":"completed","output":[
              {"type":"web_search_call","status":"completed","action":{
                "type":"search","queries":["Minecraft giant fantasy treehouse build"],
                "sources":[{"title":"Builder One","url":"https://example.org/treehouse"}]}},
              {"type":"message","content":[
                {"type":"output_text",
                 "text":"Use asymmetric canopies, layered roots, staggered balconies.",
                 "annotations":[{"type":"url_citation","title":"Builder Two",
                                 "url":"https://example.org/tower"}]}
              ]}
            ]}
            """);
        InspirationEvidence.Brief brief=InspirationEvidence.parse(response);
        assertTrue(brief.verified());
        assertEquals(2,brief.sources().size());
        assertTrue(brief.designNotes().contains("layered roots"));
        assertTrue(brief.promptContext().contains("https://example.org/treehouse"));
    }

    @Test void unverifiedModelProseNeverClaimsAWebSearch(){
        var response=json("""
            {"status":"completed","output":[{"type":"message","content":[
              {"type":"output_text","text":"I searched 20 references and they were great.",
               "annotations":[{"type":"url_citation","url":"https://example.org/fake"}]}
            ]}]}
            """);
        assertFalse(InspirationEvidence.parse(response).verified());
        assertEquals("",InspirationEvidence.parse(response).promptContext());
    }

    @Test void failureAndMissingSourcesRemainUnverified(){
        assertFalse(InspirationEvidence.parse(json("""
            {"status":"incomplete","output":[{"type":"web_search_call",
                "action":{"type":"search","sources":[{"url":"https://example.org"}]}}]}
            """)).verified());
        assertFalse(InspirationEvidence.parse(json("""
            {"status":"completed","output":[
              {"type":"web_search_call","action":{"type":"search"}},
              {"type":"message","content":[{"type":"output_text","text":"Unsourced ideas"}]}
            ]}
            """)).verified());
    }

    @Test void unsafeAndExcessiveLinksAreFiltered(){
        var response=json("""
            {"status":"completed","output":[
              {"type":"web_search_call","action":{"type":"search","sources":[
                {"title":"Local","url":"https://localhost/admin"},
                {"title":"Private","url":"http://127.0.0.1/"},
                {"title":"Good","url":"https://builder.example/projects/1"},
                {"title":"Duplicate","url":"https://builder.example/projects/1"},
                {"title":"Bad fragment","url":"https://builder.example/other#frag"}]}},
              {"type":"message","content":[{"type":"output_text",
                "text":"Public roof and timber trim inspiration."}]}
            ]}
            """);
        InspirationEvidence.Brief brief=InspirationEvidence.parse(response);
        assertEquals(1,brief.sources().size());
        assertEquals("https://builder.example/projects/1",brief.sources().get(0).url());
    }

    @Test void outputNotesAreBounded(){
        String response="{\"status\":\"completed\",\"output\":["+
            "{\"type\":\"web_search_call\",\"action\":{\"type\":\"search\","+
            "\"sources\":[{\"title\":\"Source\",\"url\":\"https://example.org/sample\"}]}},"+
            "{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\""+
            "a".repeat(4500)+"\"}]}]}";
        InspirationEvidence.Brief brief=InspirationEvidence.parse(json(response));
        assertTrue(brief.verified());
        assertEquals(3000,brief.designNotes().length());
    }
}
