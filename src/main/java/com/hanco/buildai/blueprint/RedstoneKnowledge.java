package com.hanco.buildai.blueprint;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Built-in Java 1.21.1 technical reference. Redstone-specific user requests
 * ALWAYS receive the complete bundled handbook, on BOTH supported model
 * routes and on revisions. The classpath resource is part of the mod JAR;
 * there are no external websites, logins or editable client files to locate.
 */
public final class RedstoneKnowledge {
    private RedstoneKnowledge(){}

    private static final Pattern EXPLICIT=Pattern.compile(
        "\\bred[\\s-]?stone(?:\\b|_)|\\b(?:comparator|repeater|observer|piston|sticky piston|"+
        "hopper|dropper|dispenser|crafter|copper bulb|sculk sensor|lever|"+
        "button circuit|pressure plate|tripwire|logic gate|rs latch|t flip.flop|"+
        "analog circuit|digital circuit|wireless redstone|powered rail|detector rail|"+
        "command blocks?|redstone clocks?)s?\\b",
        Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private static final Pattern MECHANISM=Pattern.compile(
        "\\b(?:automati(?:c|on)|automate|automation|mechanis(?:m|ms)|contraption|"+
        "hidden (?:piston )?door|secret (?:door|entrance)|trapdoor machine|"+
        "working (?:vending machine|factory|farm|drawbridge|elevator)|"+
        "functional (?:arena|game|capture the flag|ctf|machine)|"+
        "capture the flag|ctf|logic circuit|pulse generator|"+
        "item sort(?:er|ing)|auto.?craft(?:er|ing)|item filter|"+
        "roller[ -]?coaster|minecarts?|railway|railroad|rail tracks?|"+
        "powered rails?|train tracks?|water flush(?:ing)?|irrigation|"+
        "water channels?|lava channels?|automated farms?|"+
        "timed gate|self.opening door|piston elevator)\\b",
        Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);

    public static boolean isRedstoneRequest(String prompt,String previousSpecification) {
        return mentions(prompt) || mentions(previousSpecification);
    }
    private static boolean mentions(String text) {
        if(text==null||text.isBlank())return false;
        return EXPLICIT.matcher(text).find()||MECHANISM.matcher(text).find();
    }
    private static String readResource(String path) {
        try(InputStream stream=RedstoneKnowledge.class.getResourceAsStream(path)){
            if(stream==null)throw new IllegalStateException(
                "BuildAI redstone master reference is missing from the installed mod: "+path);
            String result=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
            if(result.length()<400)throw new IllegalStateException(
                "BuildAI redstone reference resource is incomplete: "+path);
            return result;
        }catch(IOException ex){
            throw new IllegalStateException("Unable to load bundled redstone engineering reference "+path,ex);
        }
    }

    // Read from bundled JAR once per Minecraft process; every new model
    // request can reuse this immutable technical text. The model receives the
    // relevant text ONCE per request, not a new lookup for each block or dust.
    private static final class Handbook {
        static final String TEXT=readResource("/assets/buildai/redstone_master_handbook.txt");
    }

    static String section(String from,String to) {
        String handbook=Handbook.TEXT;
        int start=handbook.indexOf(from);
        if(start<0)throw new IllegalStateException("Missing handbook section "+from);
        int end=to==null?handbook.length():handbook.indexOf(to,start+from.length());
        if(end<0)throw new IllegalStateException("Missing handbook boundary "+to);
        return handbook.substring(start,end);
    }

    private static boolean containsAny(String text,String...terms){
        if(text==null)return false;
        String lower=text.toLowerCase(java.util.Locale.ROOT);
        for(String word:terms)if(lower.contains(word))return true;
        return false;
    }

    public static String instructions(String prompt,String previousSpecification) {
        if(!isRedstoneRequest(prompt,previousSpecification))
            return "\nBASE REDSTONE: keep directional states, supported wiring and "+
                "appropriate script permissions. No machine is verified by geometry alone.\n";

        String combined=(prompt==null?"":prompt)+"\n"+
            (previousSpecification==null?"":previousSpecification);
        boolean fluid=containsAny(combined,"water","lava","irrigat","flush",
            "flood","crop farm","harvest","farming");
        boolean rail=containsAny(combined,"coaster","minecart","rail","train");
        boolean logistics=containsAny(combined,"hopper","chest","sorter",
            "dispenser","dropper","crafter","collection","item filter");
        boolean mechanical=containsAny(combined,"piston","door","farm",
            "flush","trap","elevator");
        boolean general=!(fluid||rail||logistics||mechanical);

        // Stable 1.21.1 core mechanics and directional examples ALWAYS apply.
        StringBuilder memo=new StringBuilder(
            "BUILDAI RELEVANT REDSTONE ENGINEERING MEMORY (Java 1.21.1):\n"+
            "This is already-researched bundled guidance, not a command to "+
            "browse the web or reread documentation while placing blocks. "+
            "Apply the rules to the whole circuit and output final voxel "+
            "operations. Geometry is not a substitute for in-world testing.\n");
        memo.append(section("BUILDAI REDSTONE MASTER HANDBOOK",
            "RAIL ENGINEERING:"));
        if(rail)memo.append(section("RAIL ENGINEERING:",
            "SURVIVAL/CREATIVE FLUID ECONOMY"));
        if(fluid){
            memo.append(section("SURVIVAL/CREATIVE FLUID ECONOMY",
                "WATER FLOW + PISTON FLOODGATES"));
            memo.append(section("WATER FLOW + PISTON FLOODGATES",
                "SECTION 1: FUNDAMENTALS"));
        }
        // Fundamentals & directional inputs are required by all circuits.
        memo.append(section("SECTION 1: FUNDAMENTALS",
            "SECTION 3: LOGIC"));
        if(mechanical||logistics||fluid||general)
            memo.append(section("SECTION 3: LOGIC",
                "SECTION 5: OUTPUTS"));
        if(general)
            memo.append(section("SECTION 5: OUTPUTS",
                "SECTION 7: ENGINEERING TEMPLATES"));
        memo.append(section("SECTION 7: ENGINEERING TEMPLATES",
            "ADDITIONAL ENGINEERING RESEARCH"));
        return memo.toString();
    }

}
