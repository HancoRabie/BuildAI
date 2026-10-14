package com.hanco.buildai.build;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import java.util.*;
import java.util.function.Function;

/**
 * Exact-source material ledger. Only ACTUAL Survival chest withdrawals enter
 * this log: never omitted foundations or BuildAI construction scaffolding.
 * Refund is all-or-nothing across all source inventories, and never drops
 * items when chests are missing or full. Not persisted across server restarts.
 */
public final class SupplyLedger {
    private record Entry(BlockPos chest,int slot,ItemStack stack,boolean bucketExchange){}
    public record Refund(boolean success,int returned,String message){}
    private final List<Entry> entries=new ArrayList<>();

    public void record(BlockPos chest,int slot,ItemStack consumed,boolean bucketExchange){
        Objects.requireNonNull(chest);
        if(slot<0||consumed==null||consumed.isEmpty()||consumed.getCount()!=1)
            throw new IllegalArgumentException("Invalid supply receipt");
        if(bucketExchange&&!consumed.isOf(Items.WATER_BUCKET)&&
            !consumed.isOf(Items.LAVA_BUCKET))
            throw new IllegalArgumentException("Expected a water/lava bucket");
        entries.add(new Entry(chest.toImmutable(),slot,consumed.copy(),bucketExchange));
    }
    public boolean isEmpty(){return entries.isEmpty();}
    public int pending(){return entries.size();}

    private static boolean insert(List<ItemStack> slots,Inventory inv,int preferred,ItemStack template){
        int left=template.getCount();
        if(preferred>=0&&preferred<slots.size()){
            ItemStack existing=slots.get(preferred);
            if(!existing.isEmpty()&&ItemStack.areItemsAndComponentsEqual(existing,template)
                &&inv.isValid(preferred,template)){
                int take=Math.min(left,existing.getMaxCount()-existing.getCount());
                if(take>0){existing.increment(take);left-=take;}
            }else if(existing.isEmpty()&&inv.isValid(preferred,template)){
                int take=Math.min(left,template.getMaxCount());
                slots.set(preferred,template.copyWithCount(take));left-=take;
            }
        }
        for(int i=0;i<slots.size()&&left>0;i++){
            ItemStack existing=slots.get(i);
            if(existing.isEmpty()||!ItemStack.areItemsAndComponentsEqual(existing,template)||
                !inv.isValid(i,template))continue;
            int take=Math.min(left,existing.getMaxCount()-existing.getCount());
            if(take>0){existing.increment(take);left-=take;}
        }
        for(int i=0;i<slots.size()&&left>0;i++){
            if(!slots.get(i).isEmpty()||!inv.isValid(i,template))continue;
            int take=Math.min(left,template.getMaxCount());
            slots.set(i,template.copyWithCount(take));left-=take;
        }
        return left==0;
    }
    private static boolean reclaimBucket(List<ItemStack> slots,int preferred){
        if(preferred>=0&&preferred<slots.size()&&slots.get(preferred).isOf(Items.BUCKET)){
            slots.get(preferred).decrement(1);return true;
        }
        for(ItemStack stack:slots)if(!stack.isEmpty()&&stack.isOf(Items.BUCKET)){
            stack.decrement(1);return true;
        }
        return false;
    }

    /**
     * Simulate all refunds before changing any inventory. Water/lava buckets
     * require their EMPTY counterparts returned to the original supply chest:
     * otherwise undo would duplicate a bucket the player took away.
     */
    public Refund refund(Function<BlockPos,Inventory> source){
        Objects.requireNonNull(source);
        if(entries.isEmpty())return new Refund(true,0,"No consumed supplies");
        Map<BlockPos,Inventory> containers=new LinkedHashMap<>();
        Map<BlockPos,List<ItemStack>> proposed=new LinkedHashMap<>();
        for(Entry e:entries){
            if(proposed.containsKey(e.chest()))continue;
            Inventory inv=source.apply(e.chest());
            if(inv==null)return new Refund(false,0,
                "Restore the original BuildAI supply chest at "+e.chest().toShortString());
            List<ItemStack> copy=new ArrayList<>(inv.size());
            for(int i=0;i<inv.size();i++)copy.add(inv.getStack(i).copy());
            proposed.put(e.chest(),copy);containers.put(e.chest(),inv);
        }
        for(Entry e:entries){
            List<ItemStack> slots=proposed.get(e.chest());
            if(e.bucketExchange()&&!reclaimBucket(slots,e.slot()))
                return new Refund(false,0,"Return an empty bucket to original BuildAI chest "+
                    e.chest().toShortString()+" to reverse a fluid supply exchange");
            if(!insert(slots,containers.get(e.chest()),e.slot(),e.stack()))
                return new Refund(false,0,"Not enough free space in original BuildAI chest "+
                    e.chest().toShortString()+". Clear space to complete the refund");
        }
        for(var group:proposed.entrySet()){
            Inventory inv=containers.get(group.getKey());
            for(int i=0;i<group.getValue().size();i++)
                inv.setStack(i,group.getValue().get(i));
            inv.markDirty();
        }
        int count=entries.size();entries.clear();
        return new Refund(true,count,"Refunded "+count+
            " consumed item(s) to their original BuildAI supply chests");
    }
}
