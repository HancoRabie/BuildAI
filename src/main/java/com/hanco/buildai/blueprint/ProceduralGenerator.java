package com.hanco.buildai.blueprint;
import com.google.gson.*;
import java.util.*;

/** Compiles model-authored geometry operations into deterministic, bounded voxel data. */
public final class ProceduralGenerator {
    private ProceduralGenerator() {}
    public record Compilation(Blueprint blueprint, String correctedSpecification) {}

    /** Pure geometry compiler, usable without a running Fabric registry (e.g. JUnit). */
    public static Blueprint compile(String json) {
        return compileInternal(json,null).blueprint();
    }

    /** AI-only path; resolve block errors using the live Minecraft registry. */
    public static Compilation compileDesign(String json) {
        return compileInternal(json,AiBlockRepair::repair);
    }

    /** Test seam for pipeline behavior; production always uses the real registry. */
    static Compilation compileWithRepair(String json,java.util.function.Function<String,AiBlockRepair.Result> repairer) {
        return compileInternal(json,Objects.requireNonNull(repairer));
    }

    private static Compilation compileInternal(String json,
            java.util.function.Function<String,AiBlockRepair.Result> repairer) {
        JsonObject root=JsonParser.parseString(json).getAsJsonObject();
        // "Cannot guarantee Minecraft testing" is not a valid reason to
        // discard actual usable geometry. A pure {error: ...} still rejects
        // true engine-size/operation failures (and is eligible for a single
        // model correction when it's merely a validation excuse).
        boolean hasGeometry=root.has("operations")&&root.get("operations").isJsonArray()
            && !root.getAsJsonArray("operations").isEmpty();
        if(root.has("error")&&!hasGeometry)
            throw new IllegalArgumentException("AI returned no usable geometry: "+
                root.get("error").getAsString());
        int w=number(root,"width",1,512),h=number(root,"height",1,512),d=number(root,"length",1,512);
        Blueprint b=new Blueprint(root.has("name")?root.get("name").getAsString():"AI Design",w,h,d);
        if(root.has("error")&&hasGeometry)
            b.warnings.add("MODEL DESIGN CAVEAT: "+
                trimWarning(root.get("error").getAsString())+
                ". The actual design remains available for Minecraft testing.");
        if(root.has("limitations")&&root.get("limitations").isJsonArray()){
            int shown=0;
            for(JsonElement item:root.getAsJsonArray("limitations")){
                if(shown++>=16)break;
                if(item.isJsonPrimitive())
                    b.warnings.add("SETUP / IN-GAME TEST: "+
                        trimWarning(item.getAsString()));
            }
        }
        JsonArray ops=root.getAsJsonArray("operations");
        if(ops==null||ops.size()<1||ops.size()>16000)throw new IllegalArgumentException("Expected 1-16000 design operations");
        // Repairs are cached by original state, not repeated for each voxel.
        // Keep at most 24 individual repair notices readable in Design QA.
        Map<String,AiBlockRepair.Result> repairedStates=new HashMap<>();
        int repairCount=0;
        Set<String> reportedStates=new HashSet<>();
        for(JsonElement e:ops) {
            JsonObject op=e.getAsJsonObject();
            String kind=op.get("kind").getAsString().toLowerCase(Locale.ROOT);
            String block=op.get("block").getAsString();
            if(block.length()>180||!block.matches("[a-z0-9_.-]+:[a-z0-9_./-]+(\\[[a-z0-9_,=.-]+\\])?"))
                throw new IllegalArgumentException("Invalid block state "+block);
            if(repairer!=null){
                AiBlockRepair.Result repaired=repairedStates.computeIfAbsent(block,repairer);
                if(repaired.warning()!=null && reportedStates.add(block)) {
                    // One warning per distinct invalid AI state, even across many operations.
                    if(repairCount<24)b.warnings.add(repaired.warning());
                    repairCount++;
                }
                block=repaired.block();
                op.addProperty("block",block);
            }
            int x1=number(op,"x1",0,w-1),y1=number(op,"y1",0,h-1),z1=number(op,"z1",0,d-1);
            int x2=number(op,"x2",0,w-1),y2=number(op,"y2",0,h-1),z2=number(op,"z2",0,d-1);
            int ax=Math.min(x1,x2),ay=Math.min(y1,y2),az=Math.min(z1,z2);
            int bx=Math.max(x1,x2),by=Math.max(y1,y2),bz=Math.max(z1,z2);
            switch(kind) {
                case "fill","box" -> {
                    for(int y=ay;y<=by;y++)for(int z=az;z<=bz;z++)for(int x=ax;x<=bx;x++)b.set(x,y,z,block);
                }
                case "shell" -> {
                    for(int y=ay;y<=by;y++)for(int z=az;z<=bz;z++)for(int x=ax;x<=bx;x++)
                        if(x==ax||x==bx||y==ay||y==by||z==az||z==bz)b.set(x,y,z,block);
                }
                case "walls" -> {
                    for(int y=ay;y<=by;y++)for(int z=az;z<=bz;z++)for(int x=ax;x<=bx;x++)
                        if(x==ax||x==bx||z==az||z==bz)b.set(x,y,z,block);
                }
                case "ellipsoid","organic_canopy" -> {
                    // Curved domes and natural tree crowns; deterministic and
                    // clipped to the authored box. Leaves never erase structural
                    // timbers, windows, lanterns, or existing trunk blocks.
                    boolean crown=kind.equals("organic_canopy");
                    int seed=op.has("seed")?number(op,"seed",0,1_000_000):37;
                    double cx=(ax+bx+1)/2.0,cy=(ay+by+1)/2.0,cz=(az+bz+1)/2.0;
                    double rx=(bx-ax+1)/2.0,ry=(by-ay+1)/2.0,rz=(bz-az+1)/2.0;
                    for(int y=ay;y<=by;y++)for(int z=az;z<=bz;z++)for(int x=ax;x<=bx;x++) {
                        double nx=(x+0.5-cx)/rx,ny=(y+0.5-cy)/ry,nz=(z+0.5-cz)/rz;
                        double dist=nx*nx+ny*ny+nz*nz;
                        // Tiny per-voxel edge variation avoids rectangular crowns,
                        // while interior blocks remain connected and reproducible.
                        double edge=1.0;
                        if(crown){
                            int hash=seed ^ (x*73428767) ^ (y*912931) ^ (z*19349663);
                            hash^=hash>>>13;hash*=1274126177;hash^=hash>>>16;
                            edge+=(((hash&1023)/1023.0)-0.5)*0.24;
                        }
                        if(dist>edge)continue;
                        String current=b.get(x,y,z);
                        if(!crown || b.isAir(x,y,z) || Blueprint.id(current).endsWith("_leaves"))
                            b.set(x,y,z,block);
                    }
                }
                case "line" -> {
                    int steps=Math.max(Math.abs(x2-x1),Math.max(Math.abs(y2-y1),Math.abs(z2-z1)));
                    for(int i=0;i<=steps;i++) {
                        double f=steps==0?0:(double)i/steps;
                        b.set((int)Math.round(x1+(x2-x1)*f),(int)Math.round(y1+(y2-y1)*f),(int)Math.round(z1+(z2-z1)*f),block);
                    }
                }
                default -> throw new IllegalArgumentException("Unknown primitive "+kind);
            }
        }
        if(repairCount>24)b.warnings.add((repairCount-24)+" further AI block-state repairs made; review the design visually.");
        if(repairer!=null){
            // Repair only an unambiguous local pattern: upstream dedicated
            // power source -> repeater -> output device. Both actual voxels
            // AND the generated JSON specification receive the same patch,
            // so saved revision history never reintroduces the wrong facing.
            List<RedstoneFacingGuard.Fix> flips=RedstoneFacingGuard.unambiguousReversals(b);
            int corrected=0;
            for(RedstoneFacingGuard.Fix fix:flips){
                if(ops.size()>=16000){
                    b.warnings.add("REDSTONE QA: Some clearly reversed repeaters could "+
                        "not be corrected because the 16000-operation AI output limit "+
                        "was reached. Review their input/output direction.");
                    break;
                }
                b.set(fix.x(),fix.y(),fix.z(),fix.after());
                JsonObject patch=new JsonObject();
                patch.addProperty("kind","fill");patch.addProperty("block",fix.after());
                patch.addProperty("x1",fix.x());patch.addProperty("x2",fix.x());
                patch.addProperty("y1",fix.y());patch.addProperty("y2",fix.y());
                patch.addProperty("z1",fix.z());patch.addProperty("z2",fix.z());
                ops.add(patch);
                corrected++;
            }
            if(corrected>0)b.warnings.add("REDSTONE REPAIR: corrected "+corrected+
                " clearly reversed repeater(s). Java 1.21.1 facing property points "+
                "toward the INPUT; the physical front/output is opposite. "+
                "Complex ambiguous circuits still require in-world testing.");
        }
        // AI can declare the actual command and block-entity settings separately
        // from the geometry. Their activation always requires operator approval.
        if(root.has("commands")){
            b.setCommands(CommandBlockPlan.decode(root.getAsJsonArray("commands"),b));
            if(!b.commands().isEmpty()){
                b.warnings.add(b.commands().size()+" command instructions are assigned to unique "+
                    "local XYZ positions and ordered by Y/Z/X block placement. Operator approval required.");
                int unassigned=CommandBlockPlan.unassignedBlocks(b);
                if(unassigned>0)b.warnings.add(unassigned+" command-block cell(s) have no assigned "+
                    "script and will remain unprogrammed. Inspect the map plan.");
            }
        }
        if(b.commands().isEmpty()&&CommandBlockPlan.unassignedBlocks(b)>0)
            b.warnings.add("UNCONFIGURED COMMAND BLOCKS: "+CommandBlockPlan.unassignedBlocks(b)+
                " block(s) lack actual instructions. /buildai commands approve all "+
                "cannot work until a revision supplies the missing command plan.");
        b.validateSlices();
        return new Compilation(b,root.toString());
    }
    private static String trimWarning(String text) {
        if(text==null)return "unspecified";
        String normalized=text.replaceAll("[\\r\\n\\t]+"," ").trim();
        return normalized.length()>260?normalized.substring(0,260)+"...":normalized;
    }
    private static int number(JsonObject o,String key,int min,int max) {
        int n=o.get(key).getAsInt();
        if(n<min||n>max)throw new IllegalArgumentException(key+" outside bounds "+min+".."+max);
        return n;
    }
}
