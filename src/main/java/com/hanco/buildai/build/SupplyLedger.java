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


    private static final class ItemTypes{
        final List<ItemStack> templates=new ArrayList<>();
        ItemTypes(){templates.add(ItemStack.EMPTY);}
        int id(ItemStack stack){
            if(stack.isEmpty())return 0;
            for(int i=1;i<templates.size();i++)
                if(ItemStack.areItemsAndComponentsEqual(templates.get(i),stack))return i;
            templates.add(stack.copyWithCount(1));
            return templates.size()-1;
        }
        ItemStack stack(int id,int amount){
            return id==0?ItemStack.EMPTY:templates.get(id).copyWithCount(amount);
        }
    }

    /**
     * PLAN every original source chest first, then commit all or none. Only
     * exact item+component matches stack together. The pure slot planner is
     * independently unit-tested without Minecraft registry initialization.
     */
    public Refund refund(Function<BlockPos,Inventory> source){
        Objects.requireNonNull(source);
        if(entries.isEmpty())return new Refund(true,0,"No consumed supplies");
        Map<BlockPos,Inventory> containers=new LinkedHashMap<>();
        Map<BlockPos,List<RefundSlotPlanner.Slot>> originals=new LinkedHashMap<>();
        Map<BlockPos,List<RefundSlotPlanner.Receipt>> receipts=new LinkedHashMap<>();
        ItemTypes types=new ItemTypes();
        int emptyBucket=types.id(new ItemStack(Items.BUCKET));
        for(Entry e:entries){
            if(!containers.containsKey(e.chest())){
                Inventory inv=source.apply(e.chest());
                if(inv==null)return new Refund(false,0,"Original BuildAI supply chest missing at "+
                    e.chest().toShortString());
                containers.put(e.chest(),inv);
                List<RefundSlotPlanner.Slot> slots=new ArrayList<>(inv.size());
                for(int i=0;i<inv.size();i++){
                    ItemStack stack=inv.getStack(i);
                    slots.add(stack.isEmpty()?RefundSlotPlanner.Slot.empty():
                        new RefundSlotPlanner.Slot(types.id(stack),stack.getCount(),
                        Math.min(stack.getMaxCount(),inv.getMaxCountPerStack())));
                }
                originals.put(e.chest(),slots);
                receipts.put(e.chest(),new ArrayList<>());
            }
            Inventory inv=containers.get(e.chest());
            receipts.get(e.chest()).add(new RefundSlotPlanner.Receipt(
                e.slot(),types.id(e.stack()),1,
                Math.min(e.stack().getMaxCount(),inv.getMaxCountPerStack()),
                e.bucketExchange()));
        }
        Map<BlockPos,List<RefundSlotPlanner.Slot>> planned=new LinkedHashMap<>();
        for(var group:containers.entrySet()){
            BlockPos chest=group.getKey();
            Inventory inv=group.getValue();
            RefundSlotPlanner.Result plan=RefundSlotPlanner.plan(
                originals.get(chest),receipts.get(chest),emptyBucket,
                (slot,itemId)->inv.isValid(slot,types.templates.get(itemId)));
            if(!plan.valid())return new Refund(false,0,
                "BuildAI chest "+chest.toShortString()+": "+plan.failure()+
                ". Refund remains pending until you restore its contents or capacity");
            planned.put(chest,plan.slots());
        }
        // No inventory is changed until EVERY chest passed its simulation.
        for(var group:planned.entrySet()){
            Inventory inv=containers.get(group.getKey());
            List<RefundSlotPlanner.Slot> slots=group.getValue();
            for(int i=0;i<slots.size();i++){
                RefundSlotPlanner.Slot slot=slots.get(i);
                inv.setStack(i,types.stack(slot.type(),slot.count()));
            }
            inv.markDirty();
        }
        int count=entries.size();
        entries.clear();
        return new Refund(true,count,"Returned "+count+
            " consumed items to their ORIGINAL BuildAI source chests");
    }
}
