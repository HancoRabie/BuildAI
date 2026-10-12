package com.hanco.buildai.blueprint;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        RBL ARROWS ARE RELATIVE OUTPUTS, NOT HARDCODED EAST/WEST.
        > east (+X), < west (-X), ^ north (-Z), v south (+Z).
        R1..R4 = repeater delay in REDSTONE ticks; C0 = compare,
        C1 = subtract; O = observer; P = piston; SP = sticky piston.
        Any R/C/O/P/SP circuit symbol can point N/S/E/W as required.
        Select the output from the ACTUAL neighboring target block / output
        port, then compute canonical block-state facing for Java 1.21.1.
        R2^ sends NORTH (facing=south); R2v sends SOUTH (facing=north);
        R2> sends EAST (facing=west); R2< sends WEST (facing=east).
        Java repeater/comparator 'facing' is rear INPUT, opposite output.
        Java observer 'facing' is its observed INPUT, opposite red output.
        Piston 'facing' IS its extension/output direction.
        Do not align a repeater to the player's camera, a fixed template
        axis, or the opposite side of its actual intended destination.
        Never point repeater/comparator straight up/down: horizontal only.
        Emoji glossary: 🟥 constant redstone block; 🔥 dust NOT fire;
        ⬛ solid support; 💡 redstone lamp; L lever, B button.
        A catalog module may SHOW an east-facing example but MUST be
        rotated with its complete 3D cells, supporting blocks and named
        input/output ports to the requested direction. Don't transplant
        east-facing blockstates into north/south runs. Generated full
        blueprint operations must use actual local XYZ and blockstates.
        RBL reference_unverified is NOT proof of in-world behavior.
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


    /**
     * Resolve a glyph toward the ACTUAL target/output, not a hardcoded
     * example axis. Horizontal gates/observers point their block-state
     * facing opposite the output, while pistons point along extension.
     */
    public static String orientedState(String symbol,String outputDirection){
        String target=cardinal(outputDirection);
        String state=exampleState(symbol);
        String block=Blueprint.id(state);
        if(block.equals("minecraft:repeater")||block.equals("minecraft:comparator")||
           block.equals("minecraft:observer"))
            return replaceFacing(state,opposite(target));
        if(block.equals("minecraft:piston")||block.equals("minecraft:sticky_piston"))
            return replaceFacing(state,target);
        throw new IllegalArgumentException("Symbol does not carry a horizontal "+
            "redstone output: "+symbol);
    }

    /** A one-block-adjacent horizontal output connection has one direction. */
    public static String directionToward(int x,int y,int z,
                                         int targetX,int targetY,int targetZ){
        if(y!=targetY)
            throw new IllegalArgumentException("Repeater/comparator connections must be horizontal");
        int dx=targetX-x,dz=targetZ-z;
        if(Math.abs(dx)+Math.abs(dz)!=1)
            throw new IllegalArgumentException("Target must be adjacent N/S/E/W");
        return dx==1?"east":dx==-1?"west":dz==1?"south":"north";
    }

    public static String orientedStateToward(String symbol,
                           int x,int y,int z,int targetX,int targetY,int targetZ){
        return orientedState(symbol,directionToward(x,y,z,targetX,targetY,targetZ));
    }

    private static final Pattern FACING=Pattern.compile(
        "(?<=\\\\[|,)facing=(north|south|east|west)");
    private static String cardinal(String direction){
        if(direction==null)throw new IllegalArgumentException("Missing output direction");
        String value=direction.toLowerCase(Locale.ROOT);
        return switch(value){
            case "north","south","east","west"->value;
            default->throw new IllegalArgumentException(
                "RBL horizontal output must be north, south, east or west: "+direction);
        };
    }
    private static String opposite(String direction){
        return switch(direction){
            case "east"->"west";
            case "west"->"east";
            case "north"->"south";
            case "south"->"north";
            default->throw new IllegalArgumentException("Invalid cardinal direction");
        };
    }
    private static String rotateDirection(String facing,int quarterClockwise){
        String[] order={"north","east","south","west"};
        for(int i=0;i<4;i++)if(order[i].equals(facing))
            return order[(i+quarterClockwise)%4];
        throw new IllegalArgumentException("Not cardinal: "+facing);
    }
    private static String replaceFacing(String state,String facing){
        Matcher m=FACING.matcher(state);
        if(!m.find())throw new IllegalArgumentException(
            "Directional block lacks facing property: "+state);
        return m.replaceFirst("facing="+facing);
    }

    /**
     * Exactly rotate reference template XZ geometry, all face properties and
     * ports. Only catalogued, rotation-safe component families supported;
     * fail closed on more complex state shapes until native-aware transforms
     * are implemented (rails, stairs, asymmetric doors, connected dust).
     */
    private static String rotateState(String state,int steps){
        if(steps==0)return state;
        String id=Blueprint.id(state);
        if(id.equals("minecraft:stone")||id.equals("minecraft:redstone_lamp")||
           id.equals("minecraft:redstone_block"))return state;
        if(!Set.of("minecraft:lever","minecraft:stone_button","minecraft:repeater",
            "minecraft:comparator","minecraft:observer","minecraft:piston",
            "minecraft:sticky_piston").contains(id))
            throw new IllegalArgumentException(
                "Unsafe automatic rotation of unsupported RBL block "+id);
        Matcher m=FACING.matcher(state);
        if(!m.find())throw new IllegalArgumentException("No rotatable facing for "+id);
        return m.replaceFirst("facing="+rotateDirection(m.group(1),steps));
    }
    private static int turnsFromEast(String direction){
        return switch(cardinal(direction)){
            case "east"->0;
            case "south"->1;
            case "west"->2;
            case "north"->3;
            default->throw new IllegalArgumentException("Not cardinal");
        };
    }
    private static int[] newXZ(int x,int z,int width,int length,int steps){
        return switch(steps){
            case 0->new int[]{x,z};
            case 1->new int[]{length-1-z,x};
            case 2->new int[]{width-1-x,length-1-z};
            case 3->new int[]{z,width-1-x};
            default->throw new IllegalArgumentException("Invalid XZ rotation");
        };
    }

    public record Port(String name,int x,int y,int z){}
    /** Rotated named input/output ports: never leave connections in east-only coordinates. */
    public static List<Port> ports(String id,String outputDirection){
        JsonObject module=find(id),dim=module.getAsJsonObject("size");
        int turns=turnsFromEast(outputDirection);
        int width=dim.get("width").getAsInt(),length=dim.get("length").getAsInt();
        List<Port> list=new ArrayList<>();
        for(JsonElement element:module.getAsJsonArray("ports")){
            JsonObject entry=element.getAsJsonObject();
            JsonArray at=entry.getAsJsonArray("at");
            int[] xyz=newXZ(at.get(0).getAsInt(),at.get(2).getAsInt(),
                width,length,turns);
            list.add(new Port(entry.get("name").getAsString(),
                xyz[0],at.get(1).getAsInt(),xyz[1]));
        }
        return List.copyOf(list);
    }

    /**
     * East in JSON is only a compact EXAMPLE: rotate it to the direction
     * of this build's destination; never force output east or west.
     */
    public static Blueprint templateBlueprint(String id,String outputDirection){
        JsonObject module=find(id),dim=module.getAsJsonObject("size");
        int turns=turnsFromEast(outputDirection);
        int width=dim.get("width").getAsInt(),length=dim.get("length").getAsInt();
        Blueprint result=new Blueprint(module.get("title").getAsString(),
            turns%2==0?width:length,dim.get("height").getAsInt(),
            turns%2==0?length:width);
        for(JsonElement element:module.getAsJsonArray("cells")){
            JsonObject c=element.getAsJsonObject();
            int[] coords=newXZ(c.get("x").getAsInt(),c.get("z").getAsInt(),
                width,length,turns);
            result.set(coords[0],c.get("y").getAsInt(),coords[1],
                rotateState(c.get("block").getAsString(),turns));
        }
        result.warnings.add("RBL reference only: Java 1.21.1 gameplay "+
            "unverified; rotated "+cardinal(outputDirection)+"; test actual behavior.");
        return result;
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

    /** Default east SAMPLE only; use the direction overload for real projects. */
    public static Blueprint templateBlueprint(String id){
        Blueprint result=templateBlueprint(id,"east");
        result.warnings.add("Example orientation EAST only. For other ports "+
            "call templateBlueprint(id,targetDirection)!");
        return result;
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
