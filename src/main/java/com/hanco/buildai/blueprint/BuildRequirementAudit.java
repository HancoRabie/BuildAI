package com.hanco.buildai.blueprint;

import com.google.gson.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Generic request-to-blueprint acceptance review. This is a STATIC geometry
 * inspection, not a Minecraft tick simulation or an AI self-approval.
 *
 * Existing architecture, redstone, fluid and command audits remain
 * independent specialist diagnostics. We never claim those simulations ran.
 */
public final class BuildRequirementAudit {
    private BuildRequirementAudit(){}

    public record Report(List<String> blockers,List<String> suggestions,
                         Map<String,Integer> palette,int solidCount){
        public boolean needsCorrection(){return !blockers.isEmpty();}
        public List<String> warnings(){
            List<String> out=new ArrayList<>();
            for(String s:blockers)out.add("REQUIREMENT BLOCKER: "+s);
            for(String s:suggestions)out.add("DESIGN IMPROVEMENT: "+s);
            return List.copyOf(out);
        }
    }

    private static final Map<String,String> NAMED_BLOCKS=Map.ofEntries(
        Map.entry("observer","minecraft:observer"),
        Map.entry("comparator","minecraft:comparator"),
        Map.entry("repeater","minecraft:repeater"),
        Map.entry("hopper","minecraft:hopper"),
        Map.entry("dispenser","minecraft:dispenser"),
        Map.entry("dropper","minecraft:dropper"),
        Map.entry("crafter","minecraft:crafter"),
        Map.entry("piston","minecraft:piston"),
        Map.entry("lever","minecraft:lever"),
        Map.entry("button","_button"),
        Map.entry("chest","minecraft:chest")
    );
    private static final Pattern FUNCTIONAL=Pattern.compile(
        "(?i)\\b(redstone|functional|working|automated|automatic|mechanism|"+
        "sorting|item sorter|piston door|working elevator|command block)\\b");
    private static boolean includes(String request,String key){
        return Pattern.compile("(?i)\\b"+Pattern.quote(key)+"s?\\b")
            .matcher(request).find();
    }
    private static boolean inside(Blueprint b,int x,int y,int z){
        return x>=0&&y>=0&&z>=0&&x<b.width&&y<b.height&&z<b.length;
    }
    private static boolean button(String id){return id.endsWith("_button");}
    private static boolean storage(String id){
        return id.equals("minecraft:chest")||id.equals("minecraft:trapped_chest")||
            id.equals("minecraft:barrel");
    }
    private static boolean hasBlock(Map<String,Integer> counts,String target){
        for(String id:counts.keySet()){
            if(target.equals("_button")&&button(id))return true;
            if(target.equals("minecraft:piston")&&
                (id.equals(target)||id.equals("minecraft:sticky_piston")))return true;
            // Chests are NOT interchangeable: only trapped chests emit
            // opening signals, whereas a regular chest is ordinary storage.
            if(id.equals(target))return true;
            if(id.equals(target))return true;
        }
        return false;
    }
    private static boolean feedsStorage(Blueprint b,int x,int y,int z){
        Set<Integer> visited=new HashSet<>();
        for(int i=0;i<4096;i++){
            if(!inside(b,x,y,z))return false;
            String state=b.get(x,y,z),id=Blueprint.id(state);
            if(storage(id))return true;
            if(!id.equals("minecraft:hopper")||!visited.add(b.index(x,y,z)))
                return false;
            if(state.contains("facing=north"))z--;
            else if(state.contains("facing=south"))z++;
            else if(state.contains("facing=east"))x++;
            else if(state.contains("facing=west"))x--;
            else y--; // vanilla default output is down
        }
        return false;
    }
    private static String claimedEvidence(JsonObject entry,Blueprint b){
        if(!entry.has("evidence")||!entry.get("evidence").isJsonArray())
            return "No voxel evidence supplied";
        JsonArray entries=entry.getAsJsonArray("evidence");
        if(entries.isEmpty())return "No voxel evidence supplied";
        int checked=0;
        for(JsonElement e:entries){
            if(checked++>=20)break;
            if(!e.isJsonObject())continue;
            JsonObject cell=e.getAsJsonObject();
            if(!cell.has("x")||!cell.has("y")||!cell.has("z"))continue;
            try{
                int x=cell.get("x").getAsInt(),y=cell.get("y").getAsInt(),
                    z=cell.get("z").getAsInt();
                if(!inside(b,x,y,z))continue;
                String actual=Blueprint.id(b.get(x,y,z));
                if(actual.equals("minecraft:air"))continue;
                if(cell.has("block")&&cell.get("block").isJsonPrimitive()&&
                    !actual.equals(Blueprint.id(cell.get("block").getAsString())))
                    continue;
                return ""; // evidence references a real, matching voxel
            }catch(RuntimeException ignored){}
        }
        return "Claimed XYZ evidence does not match the generated blocks";
    }

    public static Report inspect(Blueprint b,String request,String spec){
        String prompt=request==null?"":request;
        Map<String,Integer> counts=new HashMap<>();
        List<int[]> hoppers=new ArrayList<>();
        int solids=0;
        for(int y=0;y<b.height;y++)for(int z=0;z<b.length;z++)
            for(int x=0;x<b.width;x++){
                String id=Blueprint.id(b.get(x,y,z));
                if(id.equals("minecraft:air")||id.equals("minecraft:cave_air")||
                    id.equals("minecraft:void_air"))continue;
                solids++;counts.merge(id,1,Integer::sum);
                if(id.equals("minecraft:hopper")&&hoppers.size()<10000)
                    hoppers.add(new int[]{x,y,z});
            }
        List<String> blockers=new ArrayList<>(),suggestions=new ArrayList<>();
        if(solids==0)blockers.add("Blueprint contains no placeable blocks.");
        boolean trappedRequested=Pattern.compile(
            "(?i)\\b(?:trapped|redstone)[ -]chests?\\b|"+
            "\\bchests?\\s+(?:that|which|to)\\s+(?:emit|send|output|produce)\\s+redstone\\b")
            .matcher(prompt).find();
        boolean normalRequested=Pattern.compile(
            "(?i)\\b(?:normal|regular|ordinary|plain|simple|standard|"+
            "non[- ]trapped)\\s+chests?\\b").matcher(prompt).find();
        boolean trappedProhibited=Pattern.compile(
            "(?i)\\b(?:no|without|avoid|exclude)\\s+(?:trapped|redstone)\\s+chests?\\b")
            .matcher(prompt).find();
        if(trappedRequested && !trappedProhibited &&
           counts.getOrDefault("minecraft:trapped_chest",0)==0)
            blockers.add("Requested redstone/trapped chest is absent: "+
                "minecraft:chest does NOT output an opening signal.");
        if(trappedProhibited && counts.getOrDefault("minecraft:trapped_chest",0)>0)
            blockers.add("Trapped chest is present despite player's instruction "+
                "to exclude it; ordinary chest is a separate Minecraft block.");
        // A literal requested component is a concrete, machine-verifiable
        // requirement. Avoid treating mentions inside negative instructions
        // like 'no dispensers' as positive requirements.
        for(var entry:NAMED_BLOCKS.entrySet()){
            String name=entry.getKey();
            if(!includes(prompt,name))continue;
            // The word "chest" inside "trapped chest" is not a demand
            // for a separate normal chest unless the request says so.
            if(name.equals("chest")&&(trappedRequested||trappedProhibited)&&!normalRequested)
                continue;
            if(Pattern.compile("(?i)\\b(?:no|without|avoid|exclude|remove|not using)\\s+"+
                Pattern.quote(name)+"s?\\b").matcher(prompt).find())continue;
            if(!hasBlock(counts,entry.getValue()))
                blockers.add("Requested "+name+" is absent from the actual blocks.");
        }
        if(FUNCTIONAL.matcher(prompt).find()){
            for(String warning:RedstoneAudit.inspect(b,request,spec)){
                if(warning.contains("no detected redstone/command components")||
                   warning.contains("No recognizable redstone output device")||
                   warning.contains("No obvious button, lever, plate, detector, observer"))
                    blockers.add(warning);
            }
        }
        if(!hoppers.isEmpty()&&(includes(prompt,"sorting")||
            includes(prompt,"collection")||includes(prompt,"storage")||
            includes(prompt,"hopper")||includes(prompt,"item"))){
            int valid=0;
            for(int[] h:hoppers)if(feedsStorage(b,h[0],h[1],h[2]))valid++;
            if(valid==0)
                blockers.add("No directed hopper output route reaches a chest or barrel.");
            else if(valid<hoppers.size())
                suggestions.add((hoppers.size()-valid)+" hopper(s) do not "+
                    "have a directed route to storage; inspect their purpose.");
        }
        // Blank sign blocks do not fulfill a player's labeling requirement.
        String lower=prompt.toLowerCase(Locale.ROOT);
        boolean signageRequested=lower.matches(
            "(?s).*(\\\\blabel(?:s|ed|ing)?\\\\b|\\\\bsigns?\\\\b|"+
            "numbered stations|number each).*");
        if(signageRequested&&b.signs().isEmpty())
            blockers.add("Requested readable station/room signs have no "+
                "literal sign text metadata.");
        int dispensers=counts.getOrDefault("minecraft:dispenser",0);
        if(dispensers>0)
            suggestions.add(dispensers+" dispenser(s) require supplies to function. "+
                "BuildAI currently does not preload inventory; verify the "+
                "mechanical purpose and remove redundant components.");
        if(spec!=null&&!spec.isBlank())try {
            JsonObject root=JsonParser.parseString(spec).getAsJsonObject();
            if(root.has("optimization_suggestions")&&
               root.get("optimization_suggestions").isJsonArray()){
                int total=0;
                for(JsonElement idea:root.getAsJsonArray("optimization_suggestions")){
                    if(total++>=6)break;
                    if(idea.isJsonPrimitive()){
                        String value=idea.getAsString().replaceAll("[\\r\\n]+"," ").trim();
                        if(!value.isBlank())suggestions.add("MODEL PROPOSAL (unverified): "+
                            (value.length()>160?value.substring(0,160):value));
                    }
                }
            }
            if(root.has("requirements")&&root.get("requirements").isJsonArray()){
                int seen=0;
                for(JsonElement element:root.getAsJsonArray("requirements")){
                    if(seen++>=20)break;
                    if(!element.isJsonObject())continue;
                    JsonObject entry=element.getAsJsonObject();
                    String label=entry.has("description")&&entry.get("description").isJsonPrimitive()
                        ?entry.get("description").getAsString():"Unnamed requirement";
                    label=label.replaceAll("[\\r\\n]+"," ");
                    if(label.length()>100)label=label.substring(0,100);
                    String state=entry.has("status")&&entry.get("status").isJsonPrimitive()
                        ?entry.get("status").getAsString():"unverified";
                    // Self-declared statuses are untrusted commentary, not
                    // independently verified blockers. Otherwise an AI could
                    // "repair" a missing room by changing missing->implemented.
                    if("missing".equalsIgnoreCase(state))
                        suggestions.add("The generated design itself marks '"+
                            label+"' missing. Require physical proof before "+
                            "accepting a correction.");
                    else if("implemented".equalsIgnoreCase(state)){
                        String evidence=claimedEvidence(entry,b);
                        if(!evidence.isBlank())
                            suggestions.add("Claimed requirement '"+label+"' is UNVERIFIED: "+evidence);
                    }
                }
            }
        }catch(RuntimeException ignored){
            suggestions.add("Requirement evidence manifest is unreadable; "+
                "inspect the saved specification.");
        }
        // All buildings use the same audit gate. Optional specialist
        // modules contribute real, domain-specific findings without owning
        // generation or inventing new player requirements.
        SpecializedAuditRegistry.Findings extra=
            SpecializedAuditRegistry.inspect(b,request,spec);
        blockers.addAll(extra.blockers());
        suggestions.addAll(extra.suggestions());
        return new Report(List.copyOf(new LinkedHashSet<>(blockers)),
            List.copyOf(new LinkedHashSet<>(suggestions)),
            Map.copyOf(counts),solids);
    }

    /**
     * A candidate cannot win by erasing part of the build or hiding an unmet
     * requirement. Requires measurable reduction in independent blockers.
     */
    public static boolean improves(Report before,Report after,
                                   Blueprint original,Blueprint candidate){
        if(original.width!=candidate.width||original.height!=candidate.height||
            original.length!=candidate.length)return false;
        if(after.blockers.size()>=before.blockers.size())return false;
        if(candidate.solidCount()*10L<original.solidCount()*7L)return false;
        // A generic structural repair must not silently delete planned
        // growing crops or replace the map's approved executable machinery.
        // These are preserved regardless of which building type was requested.
        for(String id:List.of("minecraft:carrots","minecraft:potatoes",
            "minecraft:wheat","minecraft:beetroots","minecraft:sugar_cane",
            "minecraft:command_block","minecraft:repeating_command_block",
            "minecraft:chain_command_block")){
            if(after.palette().getOrDefault(id,0)<
                before.palette().getOrDefault(id,0))return false;
        }
        return true;
    }
}
