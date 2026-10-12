package com.hanco.buildai.blueprint;

import com.google.gson.*;
import java.util.*;

/** Validates stage contracts and merges real model-authored operations into
 * ONE ordinary BuildAI specification. Never invents crop blocks or marks an
 * incomplete stage as success. Keeps the exact original global XYZ system.
 */
public final class StagedFarmAssembler {
    private StagedFarmAssembler(){}
    public static final class Assembly {
        private final StagedFarmPlan plan;
        private final JsonObject combined;
        private final JsonArray operations=new JsonArray();
        private final JsonArray limits=new JsonArray(),commands=new JsonArray(),signs=new JsonArray();
        private final JsonArray circuits=new JsonArray(),sections=new JsonArray();
        private final JsonArray interfaces=new JsonArray();
        private JsonObject firstGate;
        private int received=0;
        public Assembly(StagedFarmPlan plan){
            this.plan=Objects.requireNonNull(plan);
            combined=new JsonObject();
            combined.addProperty("name","Staged Multi-Floor Automated Farm");
            combined.addProperty("width",plan.width());
            combined.addProperty("height",plan.height());
            combined.addProperty("length",plan.length());
        }
        public void add(StagedFarmPlan.Stage stage,String json) {
            if(received>=plan.stages().size()||
                plan.stages().get(received)!=stage)
                throw new IllegalArgumentException("Staged blueprint parts out of order");
            JsonObject data=JsonParser.parseString(json).getAsJsonObject();
            if(data.has("error") && (!data.has("operations")||
                data.getAsJsonArray("operations").isEmpty()))
                throw new IllegalArgumentException("Stage "+stage.name()+
                    " returned no geometry: "+data.get("error").getAsString());
            for(String dim:List.of("width","height","length")){
                int expected=switch(dim){
                    case "width"->plan.width();case "height"->plan.height();
                    default->plan.length();
                };
                if(!data.has(dim)||data.get(dim).getAsInt()!=expected)
                    throw new IllegalArgumentException("Stage "+stage.name()+
                        " changed the global "+dim+"; expected "+expected+
                        ". The incomplete build is not pasted.");
            }
            if(!data.has("operations")||!data.get("operations").isJsonArray()||
               data.getAsJsonArray("operations").isEmpty())
                throw new IllegalArgumentException("Stage "+stage.name()+
                    " contained no geometry. Cannot pretend this floor is complete.");
            JsonArray incoming=data.getAsJsonArray("operations");
            if(operations.size()+incoming.size()>15900)
                throw new IllegalArgumentException("Staged output exceeds 15,900 "+
                    "operations; split this project before building, not shrink it.");
            for(JsonElement value:incoming) {
                JsonObject op=value.getAsJsonObject();
                for(String key:List.of("x1","x2","y1","y2","z1","z2"))
                    if(!op.has(key))throw new IllegalArgumentException(
                        stage.name()+" has geometry missing "+key);
                if(!stage.fullBounds()){
                    int y1=op.get("y1").getAsInt(),y2=op.get("y2").getAsInt();
                    if(y1<stage.firstY()||y1>stage.lastY()||
                       y2<stage.firstY()||y2>stage.lastY())
                        throw new IllegalArgumentException(stage.name()+
                            " wrote outside assigned Y"+stage.firstY()+
                            ".."+stage.lastY()+" band; rejected to protect "+
                            "other completed floors.");
                }
                operations.add(op.deepCopy());
            }
            int itemOutputs=0,triggerInputs=0;
            if(data.has("interfaces")&&data.get("interfaces").isJsonArray()){
                for(JsonElement value:data.getAsJsonArray("interfaces")){
                    if(interfaces.size()>=48)break;
                    if(!value.isJsonObject())continue;
                    JsonObject port=value.getAsJsonObject();
                    if(!port.has("x")||!port.has("y")||!port.has("z")||
                       !port.has("type"))continue;
                    int px=port.get("x").getAsInt(),py=port.get("y").getAsInt(),
                        pz=port.get("z").getAsInt();
                    if(px<0||px>=plan.width()||py<0||py>=plan.height()||
                       pz<0||pz>=plan.length()||
                       (!stage.fullBounds()&&(py<stage.firstY()||
                        py>stage.lastY())))
                        throw new IllegalArgumentException(stage.name()+
                            " declared an interface port outside its permitted coordinates");
                    String type=port.get("type").getAsString();
                    if(type.equals("item_output"))itemOutputs++;
                    if(type.equals("trigger_input"))triggerInputs++;
                    JsonObject normalized=port.deepCopy();
                    normalized.addProperty("stage",stage.name());
                    interfaces.add(normalized);
                }
            }
            if(stage.name().startsWith("Floor ") &&
               (itemOutputs==0||triggerInputs==0))
                throw new IllegalArgumentException(stage.name()+
                    " lacks declared global XYZ item_output and trigger_input "+
                    "interfaces; cannot connect master control and collectors "+
                    "by guessing. Refine this stage before final assembly.");
            if(data.has("signs")&&data.get("signs").isJsonArray())
                for(JsonElement entry:data.getAsJsonArray("signs")){
                    if(signs.size()>=SignTextPlan.MAX_SIGNS)
                        throw new IllegalArgumentException("Staged sign label budget exceeded");
                    signs.add(entry.deepCopy());
                }
            if(data.has("commands")&&data.get("commands").isJsonArray())
                for(JsonElement entry:data.getAsJsonArray("commands"))
                    commands.add(entry.deepCopy());
            if(data.has("limitations")&&data.get("limitations").isJsonArray())
                for(JsonElement entry:data.getAsJsonArray("limitations"))
                    if(limits.size()<32)limits.add(entry.deepCopy());
            if(data.has("redstone_plan")&&data.get("redstone_plan").isJsonObject()){
                JsonObject rp=data.getAsJsonObject("redstone_plan");
                if(rp.has("circuits")&&rp.get("circuits").isJsonArray())
                    for(JsonElement entry:rp.getAsJsonArray("circuits"))
                        if(circuits.size()<128)circuits.add(entry.deepCopy());
            }
            if(firstGate==null&&data.has("fluid_gate_plan")&&
                data.get("fluid_gate_plan").isJsonObject())
                firstGate=data.getAsJsonObject("fluid_gate_plan").deepCopy();
            JsonObject section=new JsonObject();
            section.addProperty("name",stage.name());
            section.addProperty("operationCount",incoming.size());
            section.addProperty("y1",stage.firstY());
            section.addProperty("y2",stage.lastY());
            sections.add(section);
            if(received==0&&data.has("name")&&data.get("name").isJsonPrimitive())
                combined.addProperty("name",data.get("name").getAsString());
            received++;
        }
        /** Short, real interface metadata reused in subsequent requests;
         * never resend entire earlier assistant geometry or huge JSON.
         */
        public String connectionBrief(){
            StringBuilder text=new StringBuilder();
            for(JsonElement entry:interfaces){
                JsonObject port=entry.getAsJsonObject();
                String value=port.get("stage").getAsString()+": "+
                    port.get("type").getAsString()+" at ("+
                    port.get("x").getAsInt()+","+
                    port.get("y").getAsInt()+","+
                    port.get("z").getAsInt()+") "+
                    (port.has("facing")?"facing "+port.get("facing").getAsString():"")+
                    "; ";
                if(text.length()+value.length()>900)break;
                text.append(value);
            }
            return text.toString();
        }
        public int completed(){return received;}
        public int total(){return plan.stages().size();}
        public String finish(){
            if(received!=plan.stages().size())
                throw new IllegalStateException("Only "+received+"/"+plan.stages().size()+
                    " farm stages completed. Partial schematic NOT created.");
            combined.add("operations",operations);
            if(!commands.isEmpty())combined.add("commands",commands);
            if(!signs.isEmpty())combined.add("signs",signs);
            if(!limits.isEmpty())combined.add("limitations",limits);
            if(!circuits.isEmpty()){
                JsonObject rp=new JsonObject();rp.add("circuits",circuits);
                combined.add("redstone_plan",rp);
            }
            if(firstGate!=null)combined.add("fluid_gate_plan",firstGate);
            combined.add("staged_modules",sections);
            if(!interfaces.isEmpty())combined.add("staged_interfaces",interfaces);
            String result=combined.toString();
            if(result.length()>1_450_000)
                throw new IllegalArgumentException("Staged specification exceeds "+
                    "saved design metadata budget. No partial construction applied.");
            return result;
        }
    }
}
