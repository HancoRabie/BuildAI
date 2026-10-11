package com.hanco.buildai.client;

import com.google.gson.*;
import com.hanco.buildai.blueprint.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Client-only prompt generation. Credentials are never transmitted to the Minecraft server. */
public final class AIArchitect {
    private AIArchitect() {}
    private static final String REDSTONE = """
        Minecraft 1.21.1 redstone facts: dust carries power 0-15 and attenuates; repeaters are directional
        with selectable 1-4 redstone tick delays and side locking; comparators read container fullness and
        compare/subtract; observers emit pulses from their output face after updates on their sensor face;
        pistons move up to 12 movable blocks and sticky pistons can retract one; hoppers output directionally
        and lock when powered; dispensers and droppers act on powered edges; torches invert power and can
        burn out; buttons pulse and levers latch; rails, doors, trapdoors, slabs and stairs need correct blockstates.
        Complex machines are NOT functionally verified just because their geometry is syntactically valid.
        """;
    private static final String INSTRUCTIONS = """
        You are BuildAI, an architect for Minecraft Java 1.21.1. Return ONLY valid JSON, no Markdown.
        Shape: {"name":"Title","width":25,"height":14,"length":20,"operations":[
          {"kind":"fill","block":"minecraft:stone_bricks","x1":0,"y1":0,"z1":0,"x2":24,"y2":0,"z2":19}
        ]}.
        Allowed kinds: fill, box, walls (vertical perimeter), shell (six faces), line.
        X is width, Y height, Z depth. All coordinates zero-based and within dimensions.
        Operations execute in order, and later operations override earlier ones.
        Minecraft air can carve voids, windows and doorways.
        Make fully recognizable, attractive buildings including actual usable entrances and rooms.
        Use only valid Minecraft 1.21.1 block IDs and property states.
        Target <= 48x32x48 and <= 220 operations. Do not invent imaginary redstone machines.
        If machinery needs later testing, include that in the project name.
        """;
    public record DesignResult(Blueprint blueprint, String specification) {}

    public static Blueprint generate(String key,String prompt) throws Exception {
        return generateDesign(key,prompt).blueprint();
    }

    public static DesignResult generateDesign(String key,String prompt) throws Exception {
        return requestDesign(key,prompt,null);
    }

    /**
     * Revisions send the full previous primitive design rather than a new, ungrounded prompt.
     * A failed revision leaves the existing blueprint unchanged.
     */
    public static DesignResult reviseDesign(String key,String request,String existingSpecification) throws Exception {
        if(existingSpecification==null||existingSpecification.isBlank())
            throw new IllegalArgumentException("Only an AI-generated design can be revised in this version.");
        if(existingSpecification.length()>110_000)
            throw new IllegalArgumentException("This design is too large for conversational revisions.");
        return requestDesign(key,request,existingSpecification);
    }

    private static String loadReference(){
        try(java.io.InputStream in=AIArchitect.class.getResourceAsStream("/assets/buildai/redstone_reference.json")){
            if(in==null)return "";
            return new String(in.readAllBytes(),StandardCharsets.UTF_8);
        }catch(Exception ignored){return "";}
    }

    private static DesignResult requestDesign(String key,String prompt,String previous) throws Exception {
        if(key==null||key.isBlank())throw new IllegalArgumentException("Enter an OpenAI API key (v0.1).");
        if(prompt==null||prompt.isBlank()||prompt.length()>4000)throw new IllegalArgumentException("Prompt length 1-4000 required");
        JsonObject body=new JsonObject();body.addProperty("model","gpt-4.1-mini");
        JsonObject format=new JsonObject();format.addProperty("type","json_object");
        body.add("response_format",format);
        JsonArray messages=new JsonArray();
        String systemInstructions=INSTRUCTIONS+"\n"+REDSTONE+"\n"+loadReference();
        if(previous!=null)
            systemInstructions+="\nYou are REVISING an existing project, not designing from scratch. "+
                "Preserve dimensions, details and architecture unless the user requests changes. "+
                "Return a COMPLETE replacement JSON design using the same schema; do not return a diff. "+
                "Keep all previous valid geometry operations except when necessary for requested changes.";
        JsonObject system=new JsonObject();system.addProperty("role","system");system.addProperty("content",systemInstructions);
        JsonObject user=new JsonObject();user.addProperty("role","user");user.addProperty("content",prompt);
        messages.add(system);
        if(previous!=null) {
            JsonObject former=new JsonObject();
            former.addProperty("role","assistant");
            former.addProperty("content",previous);
            messages.add(former);
        }
        messages.add(user);body.add("messages",messages);
        HttpRequest request=HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/chat/completions"))
            .timeout(Duration.ofSeconds(120)).header("Content-Type","application/json")
            .header("Authorization","Bearer "+key.trim())
            .POST(HttpRequest.BodyPublishers.ofString(body.toString(),StandardCharsets.UTF_8)).build();
        HttpResponse<String> response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()!=200)throw new IllegalStateException("OpenAI HTTP "+response.statusCode()+
            ": verify API key, model availability, and billing");
        JsonObject parsed=JsonParser.parseString(response.body()).getAsJsonObject();
        String json=parsed.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message")
            .get("content").getAsString();
        return new DesignResult(ProceduralGenerator.compile(json),json);
    }
}
