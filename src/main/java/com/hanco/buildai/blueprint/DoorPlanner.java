package com.hanco.buildai.blueprint;

import java.util.*;

/**
 * Non-destructive door-pair completion for AI-authored voxel plans.
 * Never cuts a doorway through authored walls, furniture, or floors.
 * Imported schematics are not altered by this class.
 */
public final class DoorPlanner {
    private DoorPlanner(){}
    static boolean door(String raw) {
        String id=Blueprint.id(raw);
        return id.endsWith("_door") && !id.endsWith("_trapdoor");
    }
    static String property(String raw,String key) {
        int start=raw.indexOf('['),end=raw.lastIndexOf(']');
        if(start<0||end<start)return null;
        for(String part:raw.substring(start+1,end).split(",")){
            String[] pair=part.split("=",2);
            if(pair.length==2&&pair[0].equals(key))return pair[1];
        }
        return null;
    }
    static String replaceProperty(String raw,String key,String value){
        int start=raw.indexOf('['),end=raw.lastIndexOf(']');
        if(start<0)return raw+"["+key+"="+value+"]";
        if(end<start)throw new IllegalArgumentException("Bad door state "+raw);
        StringBuilder props=new StringBuilder();
        boolean replaced=false;
        for(String p:raw.substring(start+1,end).split(",")){
            if(p.isBlank())continue;
            if(props.length()>0)props.append(',');
            if(p.startsWith(key+"=")){
                props.append(key).append('=').append(value);
                replaced=true;
            }else props.append(p);
        }
        if(!replaced){
            if(props.length()>0)props.append(',');
            props.append(key).append('=').append(value);
        }
        return raw.substring(0,start+1)+props+"]";
    }
    public static List<String> completeSafePairs(Blueprint b){
        List<String> issues=new ArrayList<>();
        // Only generated designs should call this. The entire authored geometry
        // exists before this pass: solid upper neighbors are NEVER overwritten.
        for(int y=0;y<b.height;y++)for(int z=0;z<b.length;z++)for(int x=0;x<b.width;x++){
            String lower=b.get(x,y,z);
            if(!door(lower))continue;
            String half=property(lower,"half");
            if("upper".equals(half)){
                if(y==0 || !door(b.get(x,y-1,z)) ||
                    !Blueprint.id(b.get(x,y-1,z)).equals(Blueprint.id(lower)))
                    issues.add("Unpaired upper door at "+x+","+y+","+z);
                continue;
            }
            if(y+1>=b.height){
                issues.add("Door at "+x+","+y+","+z+" has no upper block inside blueprint");
                continue;
            }
            String upper=b.get(x,y+1,z);
            if(b.isAir(x,y+1,z)){
                b.set(x,y,z,replaceProperty(lower,"half","lower"));
                b.set(x,y+1,z,replaceProperty(lower,"half","upper"));
            }else if(door(upper) && Blueprint.id(upper).equals(Blueprint.id(lower)) &&
                     "upper".equals(property(upper,"half"))){
                // Preserve explicitly generated half orientations and hinge.
            }else issues.add("Door at "+x+","+y+","+z+
                " collides with another block where its upper half belongs");
        }
        b.warnings.addAll(issues);
        return List.copyOf(issues);
    }
}
