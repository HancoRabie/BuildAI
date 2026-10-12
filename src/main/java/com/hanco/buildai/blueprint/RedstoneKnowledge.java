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

    /** Build-time packaged knowledge, not a claim of model training. */
    public static String instructions(String prompt,String previousSpecification) {
        if(!isRedstoneRequest(prompt,previousSpecification))
            return "\nBASE REDSTONE: keep directional states, supported wiring and "+
                "appropriate script permissions. No machine is verified by geometry alone.\n";
        String manual=readResource("/assets/buildai/redstone_master_handbook.txt");
        return "\nMANDATORY ADVANCED REDSTONE ENGINEERING REVIEW (Java 1.21.1):\n"+
            "The player requested redstone or a working circuit/contraption. "+
            "Consult ALL rules in the bundled redstone master handbook, including its "+
            "device behavior, edge cases, correct directional block states, timed "+
            "signal flow, support checks, and circuit acceptance tests. "+
            "Never omit this engineering context when revising an existing machine. "+
            "Use real block geometry AND top-level redstone_plan to explain each "+
            "functional circuit; name precise input, signal path, output, timing, "+
            "reset and in-game test. Do not copy a schematic from a reference. "+
            "Do not invent filled inventories or functional command blocks. "+
            "A circuit cannot be claimed verified without an actual world test.\n"+
            manual+"\nMachine-readable component glossary is also packaged with BuildAI.\n";
    }
}
