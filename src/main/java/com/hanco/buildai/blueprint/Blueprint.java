package com.hanco.buildai.blueprint;

import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Independent blueprint containing block states, not a schematic-specific data structure. */
public final class Blueprint {
    // Runtime memory-safety limit per blueprint. Larger architecture will be segmented
    // in future releases; never silently shrink a player's requested scale.
    public static final int MAX_VOLUME = 4_000_000;
    public final String name;
    public final int width, height, length;
    private final String[] states;
    public final List<String> warnings = new ArrayList<>();
    /** Optional original user prompt; persisted only for BuildAI-generated files.
     * Missing for legacy or external schematics; never invent it from a filename. */
    private String originalPrompt="";
    /** Previous exact AI geometry JSON, inert metadata, NOT executable commands. */
    private String designSpecification="";
    public String originalPrompt(){return originalPrompt;}
    public String designSpecification(){return designSpecification;}
    public boolean hasOriginalPrompt(){return !originalPrompt.isBlank();}
    public boolean hasRevisionContext(){return !designSpecification.isBlank();}
    public void setGenerationContext(String original,String spec){
        if(original==null)original="";
        if(spec==null)spec="";
        if(original.length()>4000 || spec.length()>1_500_000)
            throw new IllegalArgumentException("Blueprint generation metadata exceeds supported limits");
        originalPrompt=original;
        designSpecification=spec;
    }
    /** Explicitly reviewed executable logic, not serialized blockstate text. */
    private List<CommandBlockPlan.Entry> commands=List.of();
    public List<CommandBlockPlan.Entry> commands(){return commands;}
    public void setCommands(List<CommandBlockPlan.Entry> entries){
        commands=CommandBlockPlan.mappedInBuildOrder(this,entries);
    }

    public Blueprint(String name, int width, int height, int length) {
        long volume = (long)width * height * length;
        if (width < 1 || height < 1 || length < 1 || volume > MAX_VOLUME)
            throw new IllegalArgumentException("Invalid dimensions or more than "+MAX_VOLUME+" cells");
        this.name=name;this.width=width;this.height=height;this.length=length;
        states=new String[(int)volume];
        Arrays.fill(states,"minecraft:air");
    }
    public int index(int x,int y,int z) { return x+width*(z+length*y); }
    public String get(int x,int y,int z) { return states[index(x,y,z)]; }
    public void set(int x,int y,int z,String state) {
        if(x<0||y<0||z<0||x>=width||y>=height||z>=length)
            throw new IllegalArgumentException("Outside blueprint: "+x+","+y+","+z);
        states[index(x,y,z)] = Objects.requireNonNull(state);
    }
    public static String id(String state) { return state.split("\\[",2)[0]; }
    public boolean isAir(int x,int y,int z) {
        String id=id(get(x,y,z));
        return id.equals("minecraft:air")||id.equals("minecraft:cave_air")||id.equals("minecraft:void_air");
    }
    public int solidCount() {
        int n=0;
        for(int y=0;y<height;y++)for(int z=0;z<length;z++)for(int x=0;x<width;x++)
            if(!isAir(x,y,z))n++;
        return n;
    }
    public List<BlockPos> ordered() {
        List<BlockPos> result=new ArrayList<>();
        for(int y=0;y<height;y++)for(int z=0;z<length;z++)for(int x=0;x<width;x++)
            if(!isAir(x,y,z))result.add(new BlockPos(x,y,z));
        result.sort(Comparator.comparingInt(BlockPos::getY)
            .thenComparingInt(p->priority(get(p.getX(),p.getY(),p.getZ())))
            .thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX));
        return result;
    }
    private static int priority(String state) {
        String id=id(state);
        if(id.contains("redstone")||id.contains("repeater")||id.contains("comparator")||
           id.contains("torch")||id.contains("rail")||id.contains("button")||id.contains("lever")||
           id.contains("pane")||id.contains("wall_sign"))return 2;
        if(id.contains("_door")||id.contains("_bed"))return 1;
        return 0;
    }
    public boolean secondHalf(String state) {
        return ((state.contains("_door[")||state.contains("_plant["))&&state.contains("half=upper"))
            || (state.contains("_bed[")&&state.contains("part=head"));
    }
    public Map<String,Integer> materials() {
        Map<String,Integer> counts=new TreeMap<>();
        for(int y=0;y<height;y++)for(int z=0;z<length;z++)for(int x=0;x<width;x++) {
            if(isAir(x,y,z))continue;
            String s=get(x,y,z);
            if(!secondHalf(s))counts.merge(id(s),1,Integer::sum);
        }
        return counts;
    }
    public int[] layerCounts() {
        int[] out=new int[height];
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)for(int z=0;z<length;z++)
            if(!isAir(x,y,z))out[y]++;
        return out;
    }
    public void validateSlices() {
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)for(int z=0;z<length;z++)Objects.requireNonNull(get(x,y,z));
        for(int x=0;x<width;x++)for(int y=0;y<height;y++)for(int z=0;z<length;z++)Objects.requireNonNull(get(x,y,z));
        for(int z=0;z<length;z++)for(int x=0;x<width;x++)for(int y=0;y<height;y++)Objects.requireNonNull(get(x,y,z));
    }
}
