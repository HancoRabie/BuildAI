package com.hanco.buildai.blueprint;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * BuildAI Redstone Blueprint Language (RBL), Java 1.21.1.
 *
 * Emojis and arrows are a DOCUMENTATION LAYER. Canonical XYZ blockstates are
 * the authoritative circuit recipe; no Unicode glyph is ever silently
 * interpreted as Minecraft geometry. Never claim a static template was
 * executed in Minecraft.
 *
 * Loaded once per process. Relevant snippets are selected per request, not
 * re-searched per block or included in every ordinary house prompt.
 */
public final class RedstoneBlueprintLibrary {
    private RedstoneBlueprintLibrary(){}
    private static final String PATH="/assets/buildai/redstone_blueprint_library.json";
    private static final String HEADER="""
        BUILDAI RBL REUSABLE CIRCUIT LIBRARY — Java 1.21.1
        Syntax: 🟥 redstone block; 🔥 dust (NOT fire); ⬛ solid support;
        R1>/R2>/R3>/R4> repeater delay 1..4 REDSTONE ticks, arrow
        points ACTUAL output. C0> compare, C1> subtract. O> observer RED OUTPUT east means Java observer facing=west
        (the OBSERVED INPUT face is west). P> piston extension. L lever, B button, 💡 lamp.
        Physical block-state 'facing' is component-specific: for Java
        repeaters/comparators R2> is facing=west (input west, output east).
        Diagrams are layered: +X east, +Z south, +Y up; each glyph occupies
        exactly one voxel cell. Do not infer placement by emoji character
        width. Canonical XYZ and exact blockstate always take precedence.
        Reuse a module only when its input/output, scale and constraints
        match the user's prompt; do NOT introduce unnecessary devices.
        'reference_unverified' means NOT simulated or tested in-world.
        Never label a module verified just because a JSON parser accepted it.
        """;

    private static final class Cached {
        static final JsonObject ROOT=readAndValidate();
    }

    private static JsonObject readAndValidate(){
        try(InputStream in=RedstoneBlueprintLibrary.class.getResourceAsStream(PATH)){
            if(in==null)throw new IllegalStateException("Missing RBL resource "+PATH);
            String content=new String(in.readAllBytes(),StandardCharsets.UTF_8);
            JsonObject root=JsonParser.parseString(content).getAsJsonObject();
            if(!"minecraft-java-1.21.1".equals(root.get("target").getAsString()))
                throw new IllegalStateException("RBL must target Java 1.21.1");
            if(!"buildai-rbl-v0.1".equals(root.get("schema").getAsString()))
                throw new IllegalStateException("Unknown RBL schema");
            Map<String,String> symbols=new HashMap<>();
            for(JsonElement symbol:root.getAsJsonArray("symbols")){
                JsonObject entry=symbol.getAsJsonObject();
                String name=entry.get("symbol").getAsString();
                String value=entry.get("exampleBlock").getAsString();
                if(symbols.putIfAbsent(name,value)!=null)
                    throw new IllegalStateException("Duplicate RBL symbol "+name);
                if(!value.startsWith("minecraft:"))
                    throw new IllegalStateException("RBL symbol has no vanilla block: "+name);
            }
            Set<String> ids=new HashSet<>();
            for(JsonElement element:root.getAsJsonArray("modules")){
                JsonObject module=element.getAsJsonObject();
                String id=module.get("id").getAsString();
                if(!ids.add(id))throw new IllegalStateException("Duplicate RBL module "+id);
                if(!"reference_unverified".equals(module.get("status").getAsString())&&
                   !"in_game_verified".equals(module.get("status").getAsString()))
                    throw new IllegalStateException("Unknown RBL verification status "+id);
                JsonObject size=module.getAsJsonObject("size");
                int width=size.get("width").getAsInt(),
                    height=size.get("height").getAsInt(),
                    length=size.get("length").getAsInt();
                if(width<1||height<1||length<1||1L*width*height*length>Blueprint.MAX_VOLUME)
                    throw new IllegalStateException("Invalid RBL size "+id);
                Set<Integer> occupied=new HashSet<>();
                Map<String,String> at=new HashMap<>();
                for(JsonElement cellElement:module.getAsJsonArray("cells")){
                    JsonObject cell=cellElement.getAsJsonObject();
                    int x=cell.get("x").getAsInt(),
                        y=cell.get("y").getAsInt(),z=cell.get("z").getAsInt();
                    if(x<0||y<0||z<0||x>=width||y>=height||z>=length)
                        throw new IllegalStateException("RBL cell outside bounds: "+id);
                    if(!occupied.add(x+width*(z+length*y)))
                        throw new IllegalStateException("Duplicate RBL voxel: "+id);
                    String symbol=cell.get("symbol").getAsString();
                    String state=cell.get("block").getAsString();
                    if(!symbols.containsKey(symbol))
                        throw new IllegalStateException("Undefined RBL symbol "+symbol);
                    String exemplar=Blueprint.id(symbols.get(symbol));
                    if(!Blueprint.id(state).equals(exemplar))
                        throw new IllegalStateException("Symbol mismatch "+symbol+" / "+state);
                    // The main value of RBL is NOT having to re-infer the
                    // direction/delay/mode of a known component. Treat
                    // directional glyphs as exact, state-locked recipes.
                    boolean directional=symbol.matches("R[1-4][<>^v]")||
                        symbol.matches("C[01][<>^v]")||
                        symbol.matches("(?:O|P|SP)[<>^v]")||
                        symbol.startsWith("H");
                    if(directional&&!state.equals(symbols.get(symbol)))
                        throw new IllegalStateException("Directional glyph "+
                            symbol+" has wrong Java 1.21.1 block state: "+state);
                    if(!state.startsWith("minecraft:"))
                        throw new IllegalStateException("Not a Minecraft block: "+state);
                    at.put(x+","+y+","+z,symbol);
                }
                for(JsonElement layerElement:module.getAsJsonArray("diagrams")){
                    JsonObject layer=layerElement.getAsJsonObject();
                    int y=layer.get("y").getAsInt(),z=0;
                    if(y<0||y>=height)throw new IllegalStateException("RBL layer Y out of bounds");
                    for(JsonElement rowElement:layer.getAsJsonArray("rows")){
                        if(z>=length)throw new IllegalStateException("Extra diagram row");
                        String[] tokens=rowElement.getAsString().trim().split("\\s+");
                        if(tokens.length!=width)
                            throw new IllegalStateException("RBL diagram width mismatch "+id);
                        for(int x=0;x<width;x++){
                            String token=tokens[x];
                            String actual=at.getOrDefault(x+","+y+","+z,".");
                            if(!token.equals(actual))
                                throw new IllegalStateException("RBL diagram mismatch "+id+
                                    " XYZ "+x+","+y+","+z+": "+token+" != "+actual);
                        }
                        z++;
                    }
                }
                for(JsonElement portElement:module.getAsJsonArray("ports")){
                    JsonArray xyz=portElement.getAsJsonObject().getAsJsonArray("at");
                    int x=xyz.get(0).getAsInt(),y=xyz.get(1).getAsInt(),
                        z=xyz.get(2).getAsInt();
                    if(!at.containsKey(x+","+y+","+z))
                        throw new IllegalStateException("RBL port points to empty voxel "+id);
                }
                if("in_game_verified".equals(module.get("status").getAsString())&&
                    module.getAsJsonObject("verification").getAsJsonArray("evidence").isEmpty())
                    throw new IllegalStateException("No independent gameplay evidence: "+id);
            }
            return root;
        }catch(IOException e){
            throw new IllegalStateException("Cannot load built-in Java 1.21.1 RBL library",e);
        }
    }

    /** Exact example blockstate for a glyph, not the glyph's visual direction. */
    public static String exampleState(String symbol){
        for(JsonElement element:Cached.ROOT.getAsJsonArray("symbols")){
            JsonObject entry=element.getAsJsonObject();
            if(symbol.equals(entry.get("symbol").getAsString()))
                return entry.get("exampleBlock").getAsString();
        }
        throw new IllegalArgumentException("Unknown RBL glyph: "+symbol);
    }

    public static List<String> ids(){
        List<String> out=new ArrayList<>();
        for(JsonElement element:Cached.ROOT.getAsJsonArray("modules"))
            out.add(element.getAsJsonObject().get("id").getAsString());
        return List.copyOf(out);
    }

    private static JsonObject find(String id){
        for(JsonElement element:Cached.ROOT.getAsJsonArray("modules")){
            JsonObject module=element.getAsJsonObject();
            if(module.get("id").getAsString().equals(id))return module;
        }
        throw new IllegalArgumentException("Unknown Java 1.21.1 RBL template: "+id);
    }

    /** Deterministic EXACT example voxel expansion; not an AI, world placement or test. */
    public static Blueprint templateBlueprint(String id){
        JsonObject module=find(id),size=module.getAsJsonObject("size");
        Blueprint blueprint=new Blueprint(module.get("title").getAsString(),
            size.get("width").getAsInt(),size.get("height").getAsInt(),
            size.get("length").getAsInt());
        for(JsonElement element:module.getAsJsonArray("cells")){
            JsonObject cell=element.getAsJsonObject();
            blueprint.set(cell.get("x").getAsInt(),cell.get("y").getAsInt(),
                cell.get("z").getAsInt(),cell.get("block").getAsString());
        }
        blueprint.warnings.add("RBL reference module — NOT VERIFIED in Minecraft "+
            "Java 1.21.1 until independently tested; inspect all ports and state.");
        return blueprint;
    }

    private static String describe(JsonObject module){
        StringBuilder b=new StringBuilder();
        b.append("\nMODULE ").append(module.get("id").getAsString()).append(" | ")
            .append(module.get("title").getAsString())
            .append(" | status=").append(module.get("status").getAsString()).append('\n');
        for(JsonElement diagramElement:module.getAsJsonArray("diagrams")){
            JsonObject diagram=diagramElement.getAsJsonObject();
            b.append("Y=").append(diagram.get("y").getAsInt()).append(' ');
            for(JsonElement row:diagram.getAsJsonArray("rows"))
                b.append(row.getAsString()).append(" | ");
            b.append('\n');
        }
        for(JsonElement cellElement:module.getAsJsonArray("cells")){
            JsonObject cell=cellElement.getAsJsonObject();
            b.append('(').append(cell.get("x").getAsInt()).append(',')
                .append(cell.get("y").getAsInt()).append(',')
                .append(cell.get("z").getAsInt()).append(") ")
                .append(cell.get("symbol").getAsString()).append(" = ")
                .append(cell.get("block").getAsString()).append('\n');
        }
        for(JsonElement portElement:module.getAsJsonArray("ports")){
            JsonObject port=portElement.getAsJsonObject();
            b.append("PORT ").append(port.get("name").getAsString()).append(' ')
                .append(port.get("at").toString()).append('\n');
        }
        b.append("TESTS: ");
        for(JsonElement test:module.getAsJsonArray("testCases"))
            b.append(test.getAsString()).append(" | ");
        b.append("\nNOT PROVEN: no real server-tick execution recorded.\n");
        return b.toString();
    }

    public static String relevant(String prompt,String previousSpecification){
        String text=((prompt==null?"":prompt)+" "+
            (previousSpecification==null?"":previousSpecification)).toLowerCase(Locale.ROOT);
        StringBuilder out=new StringBuilder(HEADER);
        int emitted=0;
        for(JsonElement element:Cached.ROOT.getAsJsonArray("modules")){
            JsonObject module=element.getAsJsonObject();
            boolean match=false;
            for(JsonElement keyword:module.getAsJsonArray("keywords"))
                if(text.contains(keyword.getAsString().toLowerCase(Locale.ROOT))){
                    match=true;break;
                }
            if(match&&emitted++<2)out.append(describe(module));
        }
        if(emitted==0)out.append(
            "No exact library circuit matches this request; apply handbook "+
            "rules and engineer a new mechanism. A matched diagram is NOT "+
            "permission to invent an unrelated feature.\n");
        return out.toString();
    }
}
