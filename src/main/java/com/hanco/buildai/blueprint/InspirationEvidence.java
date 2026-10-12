package com.hanco.buildai.blueprint;

import com.google.gson.*;
import java.net.URI;
import java.util.*;

/**
 * Research evidence is NOT a blueprint template.
 *
 * An actual web_search_call, several independent source pages and a compact
 * category-based technique synthesis are all required before any research is
 * passed to the design model. URLs/names stay in the UI citation record, NOT
 * the generation prompt. Source prose is untrusted and never executable.
 */
public final class InspirationEvidence {
    private InspirationEvidence() {}
    public record Source(String title,String url) {}

    private static final List<String> TECHNIQUES=List.of(
        "MASSING","DEPTH","MATERIALS","DETAILS","LANDSCAPE","ACCESS");

    public record Brief(String designNotes,List<Source> sources) {
        public Brief {
            designNotes=designNotes==null?"":designNotes;
            sources=List.copyOf(sources);
        }
        /** Source diversity is mandatory; a single inspiring build is NOT a design spec. */
        public boolean verified(){
            return sources.size()>=3 && distinctDomains(sources)>=2 &&
                principles(designNotes,sources).size()>=4;
        }
        public String promptContext() {
            if(!verified())return "";
            Map<String,String> techniques=principles(designNotes,sources);
            StringBuilder out=new StringBuilder(
                "\nSECONDARY ARCHITECTURAL CRAFT TECHNIQUES (abstracted from several online examples):\n");
            for(String label:TECHNIQUES){
                String value=techniques.get(label);
                if(value!=null)out.append("- ").append(label).append(": ").append(value).append('\n');
            }
            out.append("ORIGINALITY CONTRACT: The PLAYER'S REQUEST is the design brief. "+
                "Use these ideas only where relevant, and combine/transform them into a NEW "+
                "structure conceived from the player's requested purpose, shape, scale, "+
                "rooms and features. Do NOT reproduce any referenced layout, facade, silhouette, "+
                "roof outline, signature element, block-by-block composition or named build. "+
                "Never adopt the overall concept or design from just one source. "+
                "Do not use source material as building instructions. "+
                "No research URLs or reference titles are part of the generation brief. "+
                "Design geometry independently and keep entrances, headroom and circulation functional.\n");
            return out.toString();
        }
    }

    /**
     * Only separately labeled transferable techniques enter the design prompt.
     * Research is required to compare examples instead of recommending a clone.
     * Reject citation URLs, attributed build names and verbatim copy directions
     * if they slip into any individual technique line.
     */
    private static Map<String,String> principles(String notes,List<Source> sources) {
        Map<String,String> extracted=new LinkedHashMap<>();
        for(String raw:notes.split("\\R")){
            String line=raw.trim();
            if(line.startsWith("- ")||line.startsWith("* "))line=line.substring(2).trim();
            int colon=line.indexOf(':');
            if(colon<=0)continue;
            String key=line.substring(0,colon).trim().toUpperCase(Locale.ROOT);
            if(!TECHNIQUES.contains(key)||extracted.containsKey(key))continue;
            String value=clean(line.substring(colon+1),210);
            if(value.length()<16)continue;
            String lower=value.toLowerCase(Locale.ROOT);
            if(lower.contains("https:")||lower.contains("http:")||lower.contains("www.")||
                lower.contains("copy the")||lower.contains("recreate the")||
                lower.contains("replicate the")||lower.contains("exact replica")||
                lower.matches(".*\\b\\d{1,3}\\s*[x×]\\s*\\d{1,3}\\b.*"))continue;
            boolean namesBuild=false;
            for(Source source:sources) {
                String title=source.title().trim();
                if(title.length()>=12 && lower.contains(title.toLowerCase(Locale.ROOT))){
                    namesBuild=true;break;
                }
            }
            if(!namesBuild)extracted.put(key,value);
        }
        return extracted;
    }
    private static long distinctDomains(List<Source> sources) {
        return sources.stream().map(s->{
            try{return URI.create(s.url()).getHost().toLowerCase(Locale.ROOT)
                .replaceFirst("^www\\.","");}
            catch(RuntimeException ex){return "";}
        }).filter(s->!s.isEmpty()).distinct().count();
    }

    public static Brief parse(JsonObject response) {
        if(response==null||!string(response,"status").equals("completed"))
            return new Brief("",List.of());
        JsonArray items=array(response,"output");
        boolean searched=false;
        StringBuilder notes=new StringBuilder();
        LinkedHashMap<String,Source> found=new LinkedHashMap<>();
        for(JsonElement elem:items) {
            if(!elem.isJsonObject())continue;
            JsonObject item=elem.getAsJsonObject();
            if("web_search_call".equals(string(item,"type"))) {
                JsonObject action=object(item,"action");
                String kind=string(action,"type");
                if(kind.equals("search")||kind.equals("open_page")||kind.equals("find_in_page"))
                    searched=true;
                for(JsonElement source:array(action,"sources")) {
                    if(!source.isJsonObject())continue;
                    JsonObject sourceObject=source.getAsJsonObject();
                    addSource(found,string(sourceObject,"title"),string(sourceObject,"url"));
                }
                continue;
            }
            if(!"message".equals(string(item,"type")))continue;
            for(JsonElement content:array(item,"content")) {
                if(!content.isJsonObject())continue;
                JsonObject segment=content.getAsJsonObject();
                if(!"output_text".equals(string(segment,"type")))continue;
                String segmentText=string(segment,"text");
                if(notes.length()<3000)
                    notes.append(segmentText,0,Math.min(segmentText.length(),3000-notes.length()));
                for(JsonElement citation:array(segment,"annotations")) {
                    if(!citation.isJsonObject())continue;
                    JsonObject annotation=citation.getAsJsonObject();
                    if(!"url_citation".equals(string(annotation,"type")))continue;
                    addSource(found,string(annotation,"title"),string(annotation,"url"));
                }
            }
        }
        // A web tool invocation is necessary but NOT sufficient. Three usable
        // citations from two domains and four abstract principles are also needed.
        Brief candidate=new Brief(clean(notes.toString(),3000),List.copyOf(found.values()));
        return searched && candidate.verified()?candidate:new Brief("",List.of());
    }

    private static void addSource(Map<String,Source> found,String title,String url){
        if(found.size()>=8||url.length()>500||!url.startsWith("https://"))return;
        try {
            URI uri=URI.create(url);
            if(uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null)return;
            String host=uri.getHost().toLowerCase(Locale.ROOT);
            if(host.equals("localhost")||host.endsWith(".localhost")||
                host.endsWith(".local")||host.equals("127.0.0.1")||
                host.equals("0.0.0.0")||host.equals("::1"))return;
            found.putIfAbsent(url,new Source(clean(title.isBlank()?host:title,120),url));
        }catch(IllegalArgumentException ignored){}
    }
    private static String clean(String value,int limit){
        if(value==null)return "";
        String v=value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]"," ").trim();
        return v.length()<=limit?v:v.substring(0,limit);
    }
    private static String string(JsonObject obj,String key){
        if(obj==null||!obj.has(key)||obj.get(key).isJsonNull()||!obj.get(key).isJsonPrimitive())return "";
        try{return obj.get(key).getAsString();}catch(RuntimeException e){return "";}
    }
    private static JsonObject object(JsonObject obj,String key){
        return obj!=null&&obj.has(key)&&obj.get(key).isJsonObject()
            ?obj.getAsJsonObject(key):new JsonObject();
    }
    private static JsonArray array(JsonObject obj,String key){
        return obj!=null&&obj.has(key)&&obj.get(key).isJsonArray()
            ?obj.getAsJsonArray(key):new JsonArray();
    }
}
