package com.hanco.buildai.blueprint;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.*;

/**
 * Turn older saved schematics (which lack AI history metadata) into a
 * geometrically faithful, inert revision seed without inventing a prompt.
 *
 * Merge adjacent X runs into Z rectangles per Y layer. No external model
 * request is made here; the candidate must fit BuildAI's documented
 * operation and prompt budgets or the user gets a clear error instead of a
 * silently partial design.
 */
public final class BlueprintRevisionSeed {
    private BlueprintRevisionSeed(){}
    private record Segment(int start,int end,String state){}
    public static String from(Blueprint blueprint){
        if(blueprint.hasRevisionContext())return blueprint.designSpecification();
        JsonObject root=new JsonObject();
        root.addProperty("name",blueprint.name);
        root.addProperty("width",blueprint.width);
        root.addProperty("height",blueprint.height);
        root.addProperty("length",blueprint.length);
        JsonArray ops=new JsonArray();
        for(int y=0;y<blueprint.height;y++){
            Map<Segment,JsonObject> active=new LinkedHashMap<>();
            for(int z=0;z<blueprint.length;z++){
                Map<Segment,JsonObject> next=new LinkedHashMap<>();
                for(int x=0;x<blueprint.width;){
                    if(blueprint.isAir(x,y,z)){x++;continue;}
                    String state=blueprint.get(x,y,z);
                    int from=x++;
                    while(x<blueprint.width && state.equals(blueprint.get(x,y,z)))x++;
                    Segment segment=new Segment(from,x-1,state);
                    JsonObject operation=active.remove(segment);
                    if(operation==null){
                        operation=new JsonObject();
                        operation.addProperty("kind","fill");
                        operation.addProperty("block",state);
                        operation.addProperty("x1",from);operation.addProperty("x2",x-1);
                        operation.addProperty("y1",y);operation.addProperty("y2",y);
                        operation.addProperty("z1",z);operation.addProperty("z2",z);
                    }else operation.addProperty("z2",z);
                    next.put(segment,operation);
                }
                for(JsonObject completed:active.values())ops.add(completed);
                active=next;
                if(ops.size()+active.size()>16000)
                    throw new IllegalArgumentException("This legacy schematic is too detailed for "+
                        "a single AI revision request. Its original prompt was not saved. "+
                        "You can regenerate by entering a new prompt; the old file is unchanged.");
            }
            for(JsonObject completed:active.values())ops.add(completed);
            if(ops.size()>16000)
                throw new IllegalArgumentException("Legacy design has too many fine-grained "+
                    "sections for safe one-request revisions. Original saved file is unchanged.");
        }
        if(ops.isEmpty())throw new IllegalArgumentException("This blueprint has no geometry to revise");
        root.add("operations",ops);
        if(!blueprint.commands().isEmpty())
            root.add("commands",CommandBlockPlan.encode(blueprint.commands()));
        if(!blueprint.signs().isEmpty())
            root.add("signs",SignTextPlan.encode(blueprint.signs()));
        String json=root.toString();
        if(json.length()>1_500_000)
            throw new IllegalArgumentException("The legacy blueprint's detailed geometry exceeds "+
                "the AI revision context limit. No file or world blocks were changed.");
        return json;
    }
}
