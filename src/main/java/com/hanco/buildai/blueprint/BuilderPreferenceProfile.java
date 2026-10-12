package com.hanco.buildai.blueprint;

import java.util.*;

/**
 * Deterministic, compact memory of the user's review history. Not model
 * fine-tuning; no guessed preferences or irreversible style rules. All 120
 * retained feedback iterations contribute evidence for recurring topics.
 */
public final class BuilderPreferenceProfile {
    private BuilderPreferenceProfile(){}
    public record Review(int rating,String project,String note) {}
    private static final Map<String,List<String>> TOPICS=new LinkedHashMap<>();
    static {
        TOPICS.put("Shape and scale",List.of("scale","size","height","width","proportion","mass","silhouette","shape","symmetry","organic"));
        TOPICS.put("Roofs and facades",List.of("roof","gable","tower","facade","arch","wall","balcony","trim","window","pillar"));
        TOPICS.put("Materials and color",List.of("palette","color","colour","texture","block","contrast","wood","stone","sandstone","gradient"));
        TOPICS.put("Detail and realism",List.of("detail","decor","realistic","quality","depth","layer","shading","furniture","interior","natural"));
        TOPICS.put("Landscape and foliage",List.of("tree","leaves","canopy","garden","foliage","grass","terrain","landscape","path","water"));
        TOPICS.put("Access and usability",List.of("door","entrance","stairs","room","walkable","access","floor","corridor","lighting","light"));
        TOPICS.put("Redstone and games",List.of("redstone","command","game","ctf","capture","flag","team","score","timer","working","functional"));
    }
    public static String summarize(List<Review> reviews) {
        if(reviews==null||reviews.isEmpty())return "";
        StringBuilder result=new StringBuilder();
        result.append("\nBUILDER STYLE PROFILE (bounded evidence across ")
              .append(reviews.size()).append(" saved feedback iterations; NOT AI training):\n");
        int grouped=0;
        for(var group:TOPICS.entrySet()){
            List<Integer> indices=new ArrayList<>();
            for(int i=0;i<reviews.size();i++){
                String comment=reviews.get(i).note().toLowerCase(Locale.ROOT);
                for(String keyword:group.getValue())if(comment.contains(keyword)){
                    indices.add(i);break;
                }
            }
            if(indices.isEmpty())continue;
            if(grouped++>=7)break;
            result.append(group.getKey()).append(" (").append(indices.size()).append(" mentions): ");
            int examples=0;
            for(int i=indices.size()-1;i>=0&&examples<2;i--){
                Review review=reviews.get(indices.get(i));
                if(examples++>0)result.append(" / ");
                String clean=review.note().replaceAll("[\\p{Cntrl}&&[^\n]]"," ").trim();
                result.append("rating ").append(review.rating()).append("/5: ")
                    .append(clean,0,Math.min(170,clean.length()));
            }
            result.append('\n');
        }
        result.append("Use recurring preferences and newer corrections when relevant, but "+
            "treat every rating as contextual: the comment may praise one part of a "+
            "low-rated build. Preserve player creative direction and do not overgeneralize.\n");
        return result.length()>3600?result.substring(0,3600):result.toString();
    }
}
