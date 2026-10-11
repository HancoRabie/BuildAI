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
        return generateDesign(key,prompt,null);
    }
    public static DesignResult generateDesign(String key,String prompt,GenerationProgress progress) throws Exception {
        return requestDesign(key,prompt,null,progress);
    }

    /**
     * Revisions send the full previous primitive design rather than a new, ungrounded prompt.
     * A failed revision leaves the existing blueprint unchanged.
     */
    public static DesignResult reviseDesign(String key,String request,String existingSpecification) throws Exception {
        return reviseDesign(key,request,existingSpecification,null);
    }
    public static DesignResult reviseDesign(String key,String request,String existingSpecification,
                                            GenerationProgress progress) throws Exception {
        if(existingSpecification==null||existingSpecification.isBlank())
            throw new IllegalArgumentException("Only an AI-generated design can be revised in this version.");
        if(existingSpecification.length()>110_000)
            throw new IllegalArgumentException("This design is too large for conversational revisions.");
        return requestDesign(key,request,existingSpecification,progress);
    }

    private static String loadReference(){
        try(java.io.InputStream in=AIArchitect.class.getResourceAsStream("/assets/buildai/redstone_reference.json")){
            if(in==null)return "";
            return new String(in.readAllBytes(),StandardCharsets.UTF_8);
        }catch(Exception ignored){return "";}
    }

    private static void report(GenerationProgress progress,String stage,String detail){
        if(progress!=null)progress.stage(stage,detail);
    }

    /** ChatGPT plan usage requires an authorized OAuth token and the streaming Responses API. */
    public static DesignResult generateWithChatGPT(String prompt,String previous) throws Exception {
        return generateWithChatGPT(prompt,previous,null);
    }
    public static DesignResult generateWithChatGPT(String prompt,String previous,
                                                    GenerationProgress progress) throws Exception {
        if(prompt==null||prompt.isBlank()||prompt.length()>4000)
            throw new IllegalArgumentException("Describe your building in 1-4000 characters");
        if(previous!=null&&previous.length()>110_000)
            throw new IllegalArgumentException("Prior design is too large for revision");
        report(progress,"Checking account access","Looking up the models available to your connected ChatGPT account.");
        String model=ChatGPTSignIn.model();
        report(progress,"Preparing secure request","Using your authorized account and model "+model+".");
        String token=ChatGPTSignIn.token();
        JsonObject body=new JsonObject();
        body.addProperty("model",model);
        body.addProperty("store",false);
        body.addProperty("stream",true);
        JsonArray input=new JsonArray();
        JsonObject system=new JsonObject();
        system.addProperty("role","developer");
        String rules=INSTRUCTIONS+"\n"+REDSTONE+"\n"+loadReference();
        if(previous!=null)rules+="\nRevise the existing blueprint. Return COMPLETE replacement JSON, not a diff. "+
            "Keep existing geometry unless the player explicitly asks for changes.";
        system.addProperty("content",rules);
        input.add(system);
        if(previous!=null){
            JsonObject old=new JsonObject();old.addProperty("role","assistant");
            old.addProperty("content",previous);input.add(old);
        }
        JsonObject user=new JsonObject();user.addProperty("role","user");user.addProperty("content",prompt);
        input.add(user);body.add("input",input);
        HttpRequest request=HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/responses"))
            .timeout(Duration.ofSeconds(180))
            .header("Content-Type","application/json")
            .header("Accept","text/event-stream")
            .header("Authorization","Bearer "+token)
            .POST(HttpRequest.BodyPublishers.ofString(body.toString(),StandardCharsets.UTF_8)).build();
        report(progress,"Connecting to OpenAI","Sending the design instructions to the selected model.");
        HttpResponse<java.io.InputStream> response=ChatGPTNetwork.send(
            request,HttpResponse.BodyHandlers.ofInputStream(),"requesting a Minecraft blueprint");
        if(response.statusCode()!=200){
            try(var stream=response.body()){
                throw new java.io.IOException("ChatGPT generation HTTP "+response.statusCode()+
                    " — check plan permission, model limits or reconnect in F8");
            }
        }
        report(progress,"Waiting for model output","Request accepted; waiting for actual design data.");
        StringBuilder generated=new StringBuilder();
        boolean completed=false;
        boolean startedReceiving=false;
        try(var reader=new java.io.BufferedReader(new java.io.InputStreamReader(response.body(),
                StandardCharsets.UTF_8))){
            String line;
            while((line=reader.readLine())!=null) {
                if(!line.startsWith("data: "))continue;
                String json=line.substring(6);
                if(json.equals("[DONE]"))break;
                JsonObject event;
                try{event=JsonParser.parseString(json).getAsJsonObject();}
                catch(RuntimeException invalidEvent){continue;}
                String type=event.has("type")?event.get("type").getAsString():"";
                if(type.equals("response.created") || type.equals("response.in_progress")) {
                    report(progress,"AI generating design","OpenAI reports the model is processing the blueprint request.");
                }else if(type.equals("response.output_text.delta")){
                    if(event.has("delta")){
                        String delta=event.get("delta").getAsString();
                        if(!startedReceiving){
                            report(progress,"Receiving building instructions",
                                "Design data is arriving. Blueprint compilation starts after the response finishes.");
                            startedReceiving=true;
                        }
                        generated.append(delta);
                        if(progress!=null)progress.receivedCharacters(delta.length());
                        if(generated.length()>120_000)throw new java.io.IOException("AI blueprint exceeded 120 KB");
                    }
                }else if(type.equals("response.failed")||type.equals("response.incomplete")||type.equals("error")){
                    throw new java.io.IOException("ChatGPT stopped before completing the design ("+type+
                        "). Check plan usage or try a smaller building.");
                }else if(type.equals("response.completed")){
                    completed=true;
                    report(progress,"Response completed","Received the final response; preparing to compile the structure.");
                }
            }
        }
        if(!completed)throw new java.io.IOException("ChatGPT stream ended without response.completed; no blueprint was applied");
        String spec=generated.toString().trim();
        report(progress,"Compiling 3D blueprint","Converting AI design operations into exact block positions.");
        Blueprint compiled=ProceduralGenerator.compile(spec);
        report(progress,"Validating Minecraft blocks","Checking block IDs and states before displaying the blueprint.");
        BlockStates.validate(compiled);
        return new DesignResult(compiled,spec);
    }

    private static DesignResult requestDesign(String key,String prompt,String previous,
                                              GenerationProgress progress) throws Exception {
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
        report(progress,"Connecting to OpenAI","Sending architectural instructions through the developer API.");
        HttpResponse<String> response=ChatGPTNetwork.send(request,HttpResponse.BodyHandlers.ofString(),
            "requesting a Minecraft blueprint with the developer API");
        report(progress,"Response received","The AI response arrived; reading the design instructions.");
        if(response.statusCode()!=200)throw new IllegalStateException("OpenAI HTTP "+response.statusCode()+
            ": verify API key, model availability, and billing");
        JsonObject parsed=JsonParser.parseString(response.body()).getAsJsonObject();
        String json=parsed.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message")
            .get("content").getAsString();
        if(progress!=null)progress.receivedCharacters(json.length());
        report(progress,"Compiling 3D blueprint","Converting AI design operations into exact block positions.");
        Blueprint compiled=ProceduralGenerator.compile(json);
        report(progress,"Validating Minecraft blocks","Checking block IDs and states before displaying the blueprint.");
        BlockStates.validate(compiled);
        return new DesignResult(compiled,json);
    }
}
