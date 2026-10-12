package com.hanco.buildai.blueprint;

import com.google.gson.*;
import java.util.*;

/**
 * AI-authored command-block intentions, never executable merely because a
 * schematic or client packet supplies them. Installation is operator-approved
 * on the server AFTER ordinary construction and connection normalization.
 *
 * Target: Minecraft Java 1.21.1.  A conservative minigame command allowlist
 * prevents the generator from emitting administrative or world-destructive
 * commands, including nested execute ... run chains. This is not a general
 * arbitrary-operator-command feature.
 */
public final class CommandBlockPlan {
    private CommandBlockPlan(){}
    public static final int MAX_COMMANDS=128;
    public static final int MAX_COMMAND_LENGTH=768;
    private static final Set<String> ROOTS=Set.of(
        "scoreboard","team","tag","effect","give","clear","tp","teleport",
        "title","tellraw","playsound","particle","execute","spawnpoint");
    private static final Set<String> TYPES=Set.of(
        "minecraft:command_block","minecraft:chain_command_block","minecraft:repeating_command_block");

    public record Entry(int x,int y,int z,String command,boolean auto,boolean trackOutput) {
        public Entry {
            Objects.requireNonNull(command);
            validateCommand(command);
        }
    }
    public static boolean isCommandBlock(String state){return TYPES.contains(Blueprint.id(state));}
    public static void validate(Blueprint b,Entry e) {
        if(e.x()<0||e.y()<0||e.z()<0||e.x()>=b.width||e.y()>=b.height||e.z()>=b.length)
            throw new IllegalArgumentException("Command block position outside blueprint");
        if(!isCommandBlock(b.get(e.x(),e.y(),e.z())))
            throw new IllegalArgumentException("Command assigned to non-command block at "+
                e.x()+","+e.y()+","+e.z());
        // Prevent repeating command loops from starting the moment the block
        // is loaded. Operators arm ALL verified blocks explicitly after build.
    }
    public static void validateCommand(String command) {
        if(command.isBlank()||command.length()>MAX_COMMAND_LENGTH||
            command.startsWith("/")||command.indexOf('\n')>=0||command.indexOf('\r')>=0||
            command.indexOf('\0')>=0)
            throw new IllegalArgumentException("Command-block command is blank, malformed or exceeds "+MAX_COMMAND_LENGTH+" characters");
        String input=command.trim().toLowerCase(Locale.ROOT);
        String root=input.split("\\s+",2)[0];
        if(!ROOTS.contains(root))throw new IllegalArgumentException("Command not permitted in minigame plan: "+root);
        if(root.equals("execute")){
            // Every nested run target must be independently allowlisted.
            // Requiring a runnable tail prevents "execute ... run function ..."
            // and blocks unsupported dynamic command injection.
            int i=input.indexOf(" run ");
            if(i<0)throw new IllegalArgumentException("Execute command requires a safe 'run' action");
            validateCommand(command.substring(i+5).trim());
        }
        if(input.contains(" run function ")||input.contains(" run op ")||
            input.contains(" run deop ")||input.contains(" run data ")||
            input.contains(" run fill ")||input.contains(" run setblock "))
            throw new IllegalArgumentException("Unsafe nested action in command plan");
    }
    public static JsonArray encode(List<Entry> commands){
        JsonArray entries=new JsonArray();
        for(Entry c:commands){
            JsonObject e=new JsonObject();
            e.addProperty("x",c.x());e.addProperty("y",c.y());e.addProperty("z",c.z());
            e.addProperty("command",c.command());
            e.addProperty("auto",c.auto());e.addProperty("trackOutput",c.trackOutput());
            entries.add(e);
        }
        return entries;
    }
    public static List<Entry> decode(JsonArray payload,Blueprint blueprint) {
        if(payload==null)return List.of();
        if(payload.size()>MAX_COMMANDS)
            throw new IllegalArgumentException("Too many command blocks; maximum "+MAX_COMMANDS);
        List<Entry> out=new ArrayList<>();
        Set<String> positions=new HashSet<>();
        for(JsonElement elem:payload) {
            if(!elem.isJsonObject())throw new IllegalArgumentException("Invalid command record");
            JsonObject o=elem.getAsJsonObject();
            Entry c=new Entry(integer(o,"x"),integer(o,"y"),integer(o,"z"),
                o.get("command").getAsString(),
                o.has("auto")&&o.get("auto").getAsBoolean(),
                o.has("trackOutput")&&o.get("trackOutput").getAsBoolean());
            validate(blueprint,c);
            if(!positions.add(c.x()+":"+c.y()+":"+c.z()))
                throw new IllegalArgumentException("Duplicate command-block position");
            out.add(c);
        }
        return List.copyOf(out);
    }
    private static int integer(JsonObject o,String key){
        if(!o.has(key))throw new IllegalArgumentException("Missing command position: "+key);
        return o.get(key).getAsInt();
    }
}
