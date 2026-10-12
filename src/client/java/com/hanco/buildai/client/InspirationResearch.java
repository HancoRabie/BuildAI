package com.hanco.buildai.client;

import com.google.gson.*;
import com.hanco.buildai.blueprint.InspirationEvidence;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Optional, single-pass, hosted web_search BEFORE voxel generation. Tool access
 * varies by ChatGPT plan/model/workspace; a failed research request MUST NOT
 * discard or prevent the main design request. There is no invented browsing.
 * Research must mix several unrelated inspirations into reusable techniques,
 * never fetch one schematic and ask the blueprint model to mimic it.
 */
public final class InspirationResearch {
    private InspirationResearch() {}
    public record Result(InspirationEvidence.Brief brief, String status) {
        public boolean verified(){return brief.verified();}
        public static Result off(){return new Result(new InspirationEvidence.Brief("",List.of()),"disabled");}
    }

    public static Result research(String bearer,String model,String designPrompt,
                                  GenerationProgress progress){
        if(!InspirationSettings.enabled()) {
            stage(progress,"Online inspiration OFF","Using your local style library and architectural rules.");
            return Result.off();
        }
        stage(progress,"Researching example builds online",
            "Searching for real Minecraft architecture examples before drafting geometry.");
        try {
            JsonObject request=new JsonObject();
            request.addProperty("model",model);
            request.addProperty("store",false);
            request.addProperty("tool_choice","required");
            request.addProperty("max_output_tokens",1200);
            JsonArray tools=new JsonArray();
            JsonObject search=new JsonObject();search.addProperty("type","web_search");
            tools.add(search);request.add("tools",tools);
            JsonArray includes=new JsonArray();includes.add("web_search_call.action.sources");
            request.add("include",includes);

            String prompt=designPrompt==null?"":designPrompt.trim();
            if(prompt.length()>500)prompt=prompt.substring(0,500);
            request.addProperty("input",
                "Perform a REAL live web search for at least THREE DIFFERENT Minecraft builders' "+
                "build projects or architectural technique tutorials across at least TWO unrelated "+
                "website domains, all relevant to this player's project: "+prompt+"\n"+
                "GOAL: distill general architectural CRAFT principles from multiple examples, "+
                "NOT select a favorite design or copy its layout, proportions, palette, "+
                "signature feature, footprint or silhouette. Give priority to the player's "+
                "own creative concept and function. Find DIFFERENT interpretations so their "+
                "styles can inform one original result.\n"+
                "Output exactly SIX concise transferable lines (no other prose), "+
                "each using its exact category label, max 130 characters per line:\n"+
                "MASSING: a reusable approach to composition and scale\n"+
                "DEPTH: a reusable technique for facade or canopy depth\n"+
                "MATERIALS: a general principle for contrast and palette variation\n"+
                "DETAILS: an approach to fine block-work or organic detail\n"+
                "LANDSCAPE: a general approach to terrain and surroundings\n"+
                "ACCESS: a general principle for doors, paths and useful circulation\n"+
                "Be specific about TECHNIQUES, but never name individual builds or copy "+
                "their geometry, exact palette or measurements in these six lines. "+
                "Do not put source names, citations, URLs or website instructions "+
                "within any technique line; the API's separate citation metadata "+
                "records where you researched. Do NOT invent your research, do NOT "+
                "pretend to have visually inspected images, and do NOT output a "+
                "Minecraft blueprint or JSON. This is research only.");
            HttpRequest http=HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/responses"))
                .timeout(Duration.ofSeconds(85))
                .header("Content-Type","application/json")
                .header("Authorization","Bearer "+bearer)
                .POST(HttpRequest.BodyPublishers.ofString(request.toString(),StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response=ChatGPTNetwork.send(http,HttpResponse.BodyHandlers.ofString(),
                "researching Minecraft architecture examples");
            if(response.statusCode()!=200){
                stage(progress,"Inspiration search unavailable",
                    "Web search HTTP "+response.statusCode()+"; continuing without external references.");
                return new Result(new InspirationEvidence.Brief("",List.of()),
                    "not supported (HTTP "+response.statusCode()+")");
            }
            if(response.body().length()>600_000)throw new IllegalStateException("Oversized search result");
            InspirationEvidence.Brief brief=InspirationEvidence.parse(
                JsonParser.parseString(response.body()).getAsJsonObject());
            if(!brief.verified()){
                stage(progress,"No verifiable web inspiration",
                    "No sufficiently diverse sources and transferable techniques; using local style references.");
                return new Result(brief,"insufficient independent examples or technique synthesis");
            }
            stage(progress,"Mixed-source inspiration ready",brief.sources().size()+
                " cited sources informed transferable techniques; your prompt defines the original design.");
            return new Result(brief,"verified");
        }catch(Exception ex){
            // Never show service response bodies, tokens or raw prompts in UI/logs.
            stage(progress,"Online inspiration unavailable",
                "Research could not complete; proceeding with the normal building workflow.");
            return new Result(new InspirationEvidence.Brief("",List.of()),
                "unavailable ("+ex.getClass().getSimpleName()+")");
        }
    }
    private static void stage(GenerationProgress p,String stage,String details){
        if(p!=null)p.stage(stage,details);
    }
}
