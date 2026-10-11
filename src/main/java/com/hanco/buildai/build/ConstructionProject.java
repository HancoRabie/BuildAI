package com.hanco.buildai.build;

import com.hanco.buildai.blueprint.*;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;

/**
 * Tick-limited server-authoritative construction. No drops from excavation.
 * This first version deliberately refuses excavating block entities and bedrock.
 */
public final class ConstructionProject {
    public enum Phase { SELECT_XZ, SELECT_SURFACE, REVIEW, EXCAVATE, BUILD, WAITING_SUPPLIES, CLEANUP, NORMALIZE, CINEMATIC_PULL, COMPLETE, CANCELLED, PAUSED_ERROR }
    public final Blueprint blueprint;
    public final UUID owner;
    public final boolean creative;
    public final int surfaceLayer;
    public final ServerWorld world;
    public Phase phase=Phase.SELECT_XZ;
    public BlockPos origin=BlockPos.ORIGIN;
    private final List<BlockPos> order;
    private int excavationIndex=0,constructionIndex=0,normalizeIndex=0,normalizedPanes=0;
    private final Map<BlockPos,BlockState> originals=new LinkedHashMap<>();
    private final Set<BlockPos> modified=new HashSet<>();
    private final Set<BlockPos> supports=new LinkedHashSet<>();
    private final List<BlockPos> excavationSamples=new ArrayList<>(192);
    private int sampledRemovalCount=0,blackHoleTicks=0;
    private static final int BLACK_HOLE_TICKS=80;
    private java.util.Iterator<BlockPos> cleanupIterator;
    private final Map<String,Integer> consumed=new TreeMap<>();
    private final Map<String,String> substitutions=new LinkedHashMap<>();
    private final List<BlockPos> supplies=new ArrayList<>();
    private final Set<BlockPos> linkedChests=new LinkedHashSet<>();
    private long lastChestScan=0;
    private boolean hasSnapshot=false;
    public int collisions=0;
    public int constructionSpeed=16;
    public boolean cinematic=true;
    public void setCinematic(boolean enabled) {
        cinematic=enabled;
        constructionSpeed=enabled?16:256;
    }

    public ConstructionProject(ServerPlayerEntity player,Blueprint blueprint,int surfaceLayer) {
        this.blueprint=blueprint;this.owner=player.getUuid();this.world=player.getServerWorld();
        this.creative=player.isCreative();
        this.surfaceLayer=surfaceLayer;
        this.order=blueprint.ordered();
    }
    public void retryPlacement() {
        if (phase!=Phase.SELECT_XZ && phase!=Phase.SELECT_SURFACE && phase!=Phase.REVIEW)
            throw new IllegalStateException("Cannot reposition after excavation has begun");
        origin=BlockPos.ORIGIN;collisions=0;phase=Phase.SELECT_XZ;
    }
    /** Preview is free to move client-side; only this method locks a server-approved XYZ origin. */
    public void selectOrigin(BlockPos proposal) {
        if(phase!=Phase.SELECT_XZ && phase!=Phase.SELECT_SURFACE && phase!=Phase.REVIEW)
            throw new IllegalStateException("Placement cannot change once construction begins");
        int count=0;
        // Validate all cells first. A failed selection must never overwrite the prior approved preview.
        for(int y=0;y<blueprint.height;y++)for(int z=0;z<blueprint.length;z++)for(int x=0;x<blueprint.width;x++){
            BlockPos pos=proposal.add(x,y,z);
            if(!world.isInBuildLimit(pos)||!world.isChunkLoaded(pos))
                throw new IllegalArgumentException("Entire blueprint must be inside loaded world limits");
            BlockState previous=world.getBlockState(pos);
            if(!previous.isAir())count++;
            if(previous.hasBlockEntity()||previous.isOf(Blocks.BEDROCK))
                throw new IllegalArgumentException("Blocked by protected inventory/bedrock at "+pos.toShortString());
        }
        origin=proposal.toImmutable();
        collisions=count;
        phase=Phase.REVIEW;
    }
    public void confirm() {
        if(phase!=Phase.REVIEW)throw new IllegalStateException("Lock the blueprint XYZ first");
        // No multi-million-block snapshot on the server tick! Save affected
        // ORIGINAL states lazily, exactly before each excavation/placement.
        // A previously AIR cell is represented by absence from originals.
        hasSnapshot=true;
        excavationSamples.clear();sampledRemovalCount=0;blackHoleTicks=0;
        phase=Phase.EXCAVATE;
        if(cinematic && collisions>0)excavationCue(
            com.hanco.buildai.net.CinematicExcavationPayload.PREPARE,0);
    }
    private void excavationCue(int stage,int duration){
        net.minecraft.server.network.ServerPlayerEntity player=
            world.getServer().getPlayerManager().getPlayer(owner);
        if(player!=null&&net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(
            player,com.hanco.buildai.net.CinematicExcavationPayload.ID)){
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new com.hanco.buildai.net.CinematicExcavationPayload(
                    stage,origin,blueprint.width,blueprint.height,blueprint.length,
                    stage==com.hanco.buildai.net.CinematicExcavationPayload.PULL?
                        List.copyOf(excavationSamples):List.of(),duration));
        }
    }
    private void recordOriginal(BlockPos position) {
        BlockPos key=position.toImmutable();
        BlockState previous=world.getBlockState(position);
        // Every attempted mutation is checked, including cells excavated earlier
        // and revisited during placement. Only accepted edits enter the undo set.
        if(previous.hasBlockEntity()||previous.isOf(Blocks.BEDROCK))
            throw new IllegalStateException("Protected block appeared during construction at "+
                position.toShortString()+". Project halted before altering it.");
        if(!modified.add(key))return;
        if(!previous.isAir())originals.put(key,previous);
    }
    /** Re-run normalization against the already-built project footprint, without excavation. */
    public boolean requestPaneRepair() {
        if (phase != Phase.COMPLETE) return false;
        normalizeIndex=0;
        normalizedPanes=0;
        phase=Phase.NORMALIZE;
        return true;
    }
    public int normalizedPanes() { return normalizedPanes; }
    public void tick() {
        if(phase==Phase.CINEMATIC_PULL){
            // Absolutely no construction before the cleared-site cinematic ends.
            if(++blackHoleTicks>=BLACK_HOLE_TICKS)phase=Phase.BUILD;
            return;
        }
        if(phase==Phase.NORMALIZE) {
            int scanned=0;
            while(normalizeIndex<order.size() && scanned++<256) {
                BlockPos pos=origin.add(order.get(normalizeIndex));
                if(!world.isChunkLoaded(pos))return;
                if(PaneNormalizer.normalize(world,pos))normalizedPanes++;
                normalizeIndex++;
            }
            if(normalizeIndex>=order.size())phase=Phase.COMPLETE;
            return;
        }
        if(phase==Phase.CLEANUP){
            if(cleanupIterator==null)cleanupIterator=supports.iterator();
            int n=0;
            while(cleanupIterator.hasNext()&&n++<128){
                BlockPos p=cleanupIterator.next();
                BlockState s=world.getBlockState(p);
                if(s.isOf(BuildBlocks.CONSTRUCTION_BLOCK)||s.isOf(BuildBlocks.CONSTRUCTION_SLAB))
                    world.setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
                cleanupIterator.remove();
            }
            if(!cleanupIterator.hasNext()){
                normalizeIndex=0;
                normalizedPanes=0;
                phase=Phase.NORMALIZE;
            }
            return;
        }
        if(phase!=Phase.EXCAVATE&&phase!=Phase.BUILD&&phase!=Phase.WAITING_SUPPLIES)return;
        if(phase==Phase.EXCAVATE) {
            // Cinematic is one VISUAL removal event, not a 4-million-cell server
            // tick. The client shields the site during this bounded preparatory pass.
            // World edits use limited chunks to avoid hangs, drops and TPS collapse.
            int scanned=0,removed=0;
            int total=blueprint.width*blueprint.height*blueprint.length;
            final int maxScanned=cinematic?12000:4096;
            final int maxRemoved=cinematic?2048:2048;
            int sampleStride=Math.max(1,collisions/192);
            while(excavationIndex<total && scanned<maxScanned && removed<maxRemoved) {
                int i=excavationIndex++;
                int x=i%blueprint.width;
                int z=(i/blueprint.width)%blueprint.length;
                int y=i/(blueprint.width*blueprint.length);
                BlockPos p=origin.add(x,y,z);
                if(!world.isChunkLoaded(p)) {excavationIndex--;break;}
                scanned++;
                if(!world.getBlockState(p).isAir()) {
                    recordOriginal(p);
                    world.setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
                    removed++;
                    if(cinematic && excavationSamples.size()<192 &&
                        sampledRemovalCount++%sampleStride==0)
                        excavationSamples.add(p.toImmutable());
                }
            }
            if(excavationIndex==total){
                if(cinematic&&collisions>0){
                    // The client reveals the newly empty site ALL AT ONCE,
                    // then draws sample fragments spiralling into the center.
                    excavationCue(com.hanco.buildai.net.CinematicExcavationPayload.PULL,
                        BLACK_HOLE_TICKS);
                    phase=Phase.CINEMATIC_PULL;
                }else phase=Phase.BUILD;
            }
            return;
        }
        if(phase==Phase.WAITING_SUPPLIES)phase=Phase.BUILD;
        int n=0;
        while(constructionIndex<order.size() && n++<constructionSpeed) {
            BlockPos local=order.get(constructionIndex);
            BlockPos pos=origin.add(local);
            if(!world.isChunkLoaded(pos))return;
            String stateText=effective(local);
            BlockState target=BlockStates.parse(stateText);
            if(!creative && !blueprint.secondHalf(stateText)) {
                Item item=target.getBlock().asItem();
                if(item==Items.AIR) {phase=Phase.WAITING_SUPPLIES;return;}
                if(!consume(item)) {phase=Phase.WAITING_SUPPLIES;return;}
                consumed.merge(Registries.ITEM.getId(item).toString(),1,Integer::sum);
            }
            // Cosmetic construction scaffolding under overhanging slabs/stairs; not in manifest.
            if(local.getY()>0 &&
                blueprint.isAir(local.getX(),local.getY()-1,local.getZ()) &&
                (target.getBlock() instanceof SlabBlock || target.getBlock() instanceof StairsBlock)) {
                BlockPos under=pos.down();
                if(world.getBlockState(under).isAir()){
                    BlockState staging=target.getBlock() instanceof SlabBlock
                        ? BuildBlocks.CONSTRUCTION_SLAB.getDefaultState()
                        : BuildBlocks.CONSTRUCTION_BLOCK.getDefaultState();
                    recordOriginal(under);
                    world.setBlockState(under,staging,Block.NOTIFY_ALL);
                    supports.add(under.toImmutable());
                }
            }
            recordOriginal(pos);
            world.setBlockState(pos,target,Block.NOTIFY_ALL|Block.FORCE_STATE);
            if(cinematic)world.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD,
                pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,1,0.2,0.3,0.2,0.01);
            constructionIndex++;
        }
        if(constructionIndex==order.size())phase=Phase.CLEANUP;
    }
    public Map<String,Integer> consumed(){return Collections.unmodifiableMap(consumed);}
    public BlockPos statusChest(){
        if(supplies.isEmpty())refreshChests();
        return supplies.isEmpty()?new BlockPos(0,-9999,0):supplies.get(0);
    }
    public com.hanco.buildai.net.ProjectStatusPayload status(){
        Map<String,Integer> current=stored(),required=requirements();
        StringBuilder lines=new StringBuilder();
        int suppliesTotal=0,needTotal=0;
        for(var e:required.entrySet()){
            int used=consumed.getOrDefault(e.getKey(),0);
            int available=current.getOrDefault(e.getKey(),0);
            needTotal+=e.getValue();
            suppliesTotal+=Math.min(e.getValue(),used+available);
            if(lines.length()<1750)lines.append(e.getKey().replace("minecraft:",""))
                .append(": ").append(used+available).append("/").append(e.getValue()).append("\n");
        }
        return new com.hanco.buildai.net.ProjectStatusPayload(blueprint.name,phase.toString(),
            placed(),total(),suppliesTotal,needTotal,lines.toString(),statusChest());
    }
    public void substitute(String from,String to) {
        if(phase!=Phase.SELECT_XZ && phase!=Phase.SELECT_SURFACE && phase!=Phase.REVIEW)
            throw new IllegalStateException("Substitutions locked after construction begins");
        if(!blueprint.materials().containsKey(from))throw new IllegalArgumentException("Blueprint does not use "+from);
        SubstitutionPolicy.validateMapping(from,to,world);
        Map<String,String> candidate=new LinkedHashMap<>(substitutions);
        candidate.put(from,to);
        for(int y=0;y<blueprint.height;y++)for(int z=0;z<blueprint.length;z++)for(int x=0;x<blueprint.width;x++)
            BlockStates.parse(SubstitutionPolicy.apply(blueprint,x,y,z,candidate));
        substitutions.put(from,to);
    }
    private String effective(net.minecraft.util.math.BlockPos local) {
        return SubstitutionPolicy.apply(blueprint,local.getX(),local.getY(),local.getZ(),substitutions);
    }
    public int placed(){return constructionIndex;}
    public int total(){return order.size();}
    public int excavated(){return excavationIndex;}
    public int cells(){return blueprint.width*blueprint.height*blueprint.length;}
    public Map<String,Integer> requirements(){
        Map<String,Integer> result=new TreeMap<>();
        for(BlockPos local:order){
            String value=effective(local);
            if(!blueprint.secondHalf(value))result.merge(Blueprint.id(value),1,Integer::sum);
        }
        return result;
    }

    private boolean consume(Item item) {
        if(world.getTime()-lastChestScan>=40||supplies.isEmpty())refreshChests();
        for(BlockPos chest:supplies) {
            if(!(world.getBlockEntity(chest) instanceof Inventory inv))continue;
            for(int i=0;i<inv.size();i++) {
                ItemStack stack=inv.getStack(i);
                if(stack.isOf(item)&&!stack.isEmpty()) {
                    inv.removeStack(i,1);inv.markDirty();return true;
                }
            }
        }
        return false;
    }
    private void refreshChests() {
        supplies.clear();
        lastChestScan=world.getTime();
        for(BlockPos linked:linkedChests) {
            if(world.getBlockEntity(linked) instanceof Inventory && marked(linked))
                supplies.add(linked);
        }
        // Small auto-discovery around the chosen ground-level corner, never the buried blueprint origin.
        BlockPos ground=origin.up(surfaceLayer);
        for(int dx=-12;dx<=12;dx++)for(int dz=-12;dz<=12;dz++)for(int dy=-4;dy<=4;dy++) {
            BlockPos candidate=ground.add(dx,dy,dz);
            if(!(world.getBlockEntity(candidate) instanceof Inventory))continue;
            if(marked(candidate)&&!supplies.contains(candidate))supplies.add(candidate.toImmutable());
        }
    }
    private boolean marked(BlockPos chest) {
        for(Direction direction:Direction.values()){
            BlockPos candidate=chest.offset(direction);
            if(world.getBlockEntity(candidate) instanceof SignBlockEntity sign) {
                for(int line=0;line<4;line++) {
                    if("BuildAI".equalsIgnoreCase(sign.getFrontText().getMessage(line,false).getString().trim())||
                       "BuildAI".equalsIgnoreCase(sign.getBackText().getMessage(line,false).getString().trim()))
                        return true;
                }
            }
        }
        return false;
    }
    public boolean protectedChest(BlockPos pos) {
        return linkedChests.contains(pos)||supplies.contains(pos);
    }
    public void linkChest(BlockPos pos) {
        if(!(world.getBlockEntity(pos) instanceof Inventory))throw new IllegalArgumentException("Select a chest or other inventory block");
        if(!marked(pos))throw new IllegalArgumentException("Put a sign saying BuildAI beside the chest first");
        if(pos.getSquaredDistance(origin.add(blueprint.width/2,surfaceLayer,blueprint.length/2))>160*160)
            throw new IllegalArgumentException("Supply chest too far from the project");
        linkedChests.add(pos.toImmutable());refreshChests();
    }
    public Map<String,Integer> stored(){
        refreshChests();
        Map<String,Integer> map=new TreeMap<>();
        for(BlockPos pos:supplies)if(world.getBlockEntity(pos) instanceof Inventory inv)
            for(int slot=0;slot<inv.size();slot++){
                ItemStack stack=inv.getStack(slot);
                if(stack.isEmpty())continue;
                map.merge(Registries.ITEM.getId(stack.getItem()).toString(),stack.getCount(),Integer::sum);
            }
        return map;
    }
    public void cancel(boolean undo) {
        excavationCue(com.hanco.buildai.net.CinematicExcavationPayload.STOP,0);
        // Temporary staging never survives a cancellation.
        for(BlockPos p:new ArrayList<>(supports)){
            BlockState s=world.getBlockState(p);
            if(s.isOf(BuildBlocks.CONSTRUCTION_BLOCK)||s.isOf(BuildBlocks.CONSTRUCTION_SLAB))
                world.setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
        }
        supports.clear();
        if(undo && hasSnapshot) {
            // Conflict safe: refuse rollback if a block has become a block entity since our placement.
            for(BlockPos p:modified)if(world.getBlockState(p).hasBlockEntity())
                throw new IllegalStateException("Undo blocked by new container or block entity at "+p.toShortString());
            for(BlockPos position:modified){
                BlockState original=originals.getOrDefault(position,Blocks.AIR.getDefaultState());
                world.setBlockState(position,original,Block.NOTIFY_ALL|Block.FORCE_STATE);
            }
        }
        phase=Phase.CANCELLED;
    }
}
