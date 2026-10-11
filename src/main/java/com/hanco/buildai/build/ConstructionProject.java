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
    public enum Phase { SELECT_XZ, SELECT_SURFACE, REVIEW, EXCAVATE, BUILD, WAITING_SUPPLIES, COMPLETE, CANCELLED }
    public final Blueprint blueprint;
    public final UUID owner;
    public final boolean creative;
    public final int surfaceLayer;
    public final ServerWorld world;
    public Phase phase=Phase.SELECT_XZ;
    public BlockPos origin=BlockPos.ORIGIN;
    private final List<BlockPos> order;
    private int excavationIndex=0,constructionIndex=0;
    private final Map<BlockPos,BlockState> originals=new LinkedHashMap<>();
    private final Set<BlockPos> modified=new HashSet<>();
    private final Map<String,Integer> consumed=new TreeMap<>();
    private final List<BlockPos> supplies=new ArrayList<>();
    private long lastChestScan=0;
    private boolean hasSnapshot=false;
    public int collisions=0;
    public int constructionSpeed=16;

    public ConstructionProject(ServerPlayerEntity player,Blueprint blueprint,int surfaceLayer) {
        this.blueprint=blueprint;this.owner=player.getUuid();this.world=player.getServerWorld();
        this.creative=player.isCreative();
        this.surfaceLayer=surfaceLayer;
        this.order=blueprint.ordered();
    }
    public void selectXZ(BlockPos pos) {
        if(phase!=Phase.SELECT_XZ && phase!=Phase.SELECT_SURFACE)return;
        origin=new BlockPos(pos.getX(),0,pos.getZ());
        phase=Phase.SELECT_SURFACE;
    }
    public void selectSurface(BlockPos surface) {
        if(phase!=Phase.SELECT_SURFACE)return;
        origin=new BlockPos(origin.getX(),surface.getY()+1-surfaceLayer,origin.getZ());
        collisions=0;
        for(int y=0;y<blueprint.height;y++)for(int z=0;z<blueprint.length;z++)for(int x=0;x<blueprint.width;x++){
            BlockPos pos=origin.add(x,y,z);
            if(!world.isInBuildLimit(pos))throw new IllegalArgumentException("Part of the structure is outside world height");
            BlockState previous=world.getBlockState(pos);
            if(!previous.isAir())collisions++;
            if(previous.hasBlockEntity()||previous.isOf(Blocks.BEDROCK))
                throw new IllegalArgumentException("Structure overlaps protected block entity or bedrock at "+pos.toShortString());
        }
        phase=Phase.REVIEW;
    }
    public void confirm() {
        if(phase!=Phase.REVIEW)throw new IllegalStateException("Select ground first");
        // Snapshot all occupied bounding cells. Excavation only begins after explicit player confirmation.
        for(int y=0;y<blueprint.height;y++)for(int z=0;z<blueprint.length;z++)for(int x=0;x<blueprint.width;x++) {
            BlockPos p=origin.add(x,y,z);
            originals.put(p.toImmutable(),world.getBlockState(p));
        }
        hasSnapshot=true;
        phase=Phase.EXCAVATE;
    }
    public void tick() {
        if(phase!=Phase.EXCAVATE&&phase!=Phase.BUILD&&phase!=Phase.WAITING_SUPPLIES)return;
        if(phase==Phase.EXCAVATE) {
            int processed=0;
            int total=blueprint.width*blueprint.height*blueprint.length;
            while(excavationIndex<total && processed++<Math.min(64,constructionSpeed*4)) {
                int i=excavationIndex++;
                int x=i%blueprint.width;
                int z=(i/blueprint.width)%blueprint.length;
                int y=i/(blueprint.width*blueprint.length);
                BlockPos p=origin.add(x,y,z);
                if(!world.isChunkLoaded(p)) {excavationIndex--;break;}
                if(!world.getBlockState(p).isAir()) {
                    modified.add(p.toImmutable());
                    world.setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
                }
            }
            if(excavationIndex==total)phase=Phase.BUILD;
            return;
        }
        if(phase==Phase.WAITING_SUPPLIES)phase=Phase.BUILD;
        int n=0;
        while(constructionIndex<order.size() && n++<constructionSpeed) {
            BlockPos local=order.get(constructionIndex);
            BlockPos pos=origin.add(local);
            if(!world.isChunkLoaded(pos))return;
            String stateText=blueprint.get(local.getX(),local.getY(),local.getZ());
            BlockState target=BlockStates.parse(stateText);
            if(!creative && !blueprint.secondHalf(stateText)) {
                Item item=target.getBlock().asItem();
                if(item==Items.AIR) {phase=Phase.WAITING_SUPPLIES;return;}
                if(!consume(item)) {phase=Phase.WAITING_SUPPLIES;return;}
                consumed.merge(Registries.ITEM.getId(item).toString(),1,Integer::sum);
            }
            modified.add(pos.toImmutable());
            world.setBlockState(pos,target,Block.NOTIFY_ALL|Block.FORCE_STATE);
            constructionIndex++;
        }
        if(constructionIndex==order.size())phase=Phase.COMPLETE;
    }
    public Map<String,Integer> consumed(){return Collections.unmodifiableMap(consumed);}
    public int placed(){return constructionIndex;}
    public int total(){return order.size();}
    public int excavated(){return excavationIndex;}
    public int cells(){return blueprint.width*blueprint.height*blueprint.length;}
    public Map<String,Integer> requirements(){return blueprint.materials();}

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
        // Bounded search around construction origin; signs can be attached or placed above a chest.
        for(int dx=-12;dx<=12;dx++)for(int dz=-12;dz<=12;dz++)for(int dy=-3;dy<=3;dy++) {
            BlockPos candidate=origin.add(dx,dy,dz);
            if(!(world.getBlockEntity(candidate) instanceof Inventory))continue;
            if(marked(candidate))supplies.add(candidate.toImmutable());
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
    public boolean protectedChest(BlockPos pos) {return supplies.contains(pos);}
    public void cancel(boolean undo) {
        if(undo && hasSnapshot) {
            // Conflict safe: refuse rollback if a block has become a block entity since our placement.
            for(BlockPos p:modified)if(world.getBlockState(p).hasBlockEntity())
                throw new IllegalStateException("Undo blocked by new container or block entity at "+p.toShortString());
            for(Map.Entry<BlockPos,BlockState> entry:originals.entrySet()) {
                if(modified.contains(entry.getKey()))
                    world.setBlockState(entry.getKey(),entry.getValue(),Block.NOTIFY_ALL|Block.FORCE_STATE);
            }
        }
        phase=Phase.CANCELLED;
    }
}
