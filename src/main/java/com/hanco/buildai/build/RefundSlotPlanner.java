package com.hanco.buildai.build;

import java.util.*;
import java.util.function.BiPredicate;

/** Deterministic, Minecraft-independent slot allocator for exact-source undo.
 * Type 0 means empty. Other type IDs are assigned by SupplyLedger using
 * ItemStack.areItemsAndComponentsEqual (including components), not block IDs.
 */
public final class RefundSlotPlanner {
    private RefundSlotPlanner(){}
    public record Slot(int type,int count,int max){
        public Slot{
            if(type<0||count<0||max<1||count>max||(type==0&&count!=0))
                throw new IllegalArgumentException("Invalid inventory slot");
        }
        public static Slot empty(){return new Slot(0,0,64);}
    }
    public record Receipt(int preferredSlot,int type,int count,int max,boolean bucketExchange){
        public Receipt{
            if(preferredSlot<0||type<=0||count<=0||max<=0)
                throw new IllegalArgumentException("Invalid refund receipt");
        }
    }
    public record Result(boolean valid,List<Slot> slots,String failure){}
    public static Result plan(List<Slot> starting,List<Receipt> receipts,
                              int emptyBucketType,BiPredicate<Integer,Integer> accepts){
        Objects.requireNonNull(starting);Objects.requireNonNull(receipts);
        Objects.requireNonNull(accepts);
        ArrayList<Slot> work=new ArrayList<>(starting);
        for(Receipt r:receipts){
            if(r.bucketExchange()){
                int found=-1;
                if(r.preferredSlot()<work.size()&&
                   work.get(r.preferredSlot()).type()==emptyBucketType&&
                   work.get(r.preferredSlot()).count()>0)found=r.preferredSlot();
                if(found<0)for(int i=0;i<work.size();i++)
                    if(work.get(i).type()==emptyBucketType&&work.get(i).count()>0){
                        found=i;break;
                    }
                if(found<0)return new Result(false,List.copyOf(starting),
                    "empty bucket missing from original source chest");
                Slot old=work.get(found),reduced=new Slot(old.type(),
                    old.count()-1,old.max());
                work.set(found,reduced.count()==0?Slot.empty():reduced);
            }
            int left=r.count();
            if(r.preferredSlot()<work.size()){
                int i=r.preferredSlot();
                Slot old=work.get(i);
                if(accepts.test(i,r.type())){
                    if(old.type()==0){
                        int n=Math.min(left,r.max());
                        work.set(i,new Slot(r.type(),n,r.max()));left-=n;
                    }else if(old.type()==r.type()&&old.count()<old.max()){
                        int n=Math.min(left,old.max()-old.count());
                        work.set(i,new Slot(old.type(),old.count()+n,old.max()));left-=n;
                    }
                }
            }
            for(int i=0;i<work.size()&&left>0;i++){
                Slot old=work.get(i);
                if(old.type()!=r.type()||old.count()>=old.max()||
                   !accepts.test(i,r.type()))continue;
                int n=Math.min(left,old.max()-old.count());
                work.set(i,new Slot(old.type(),old.count()+n,old.max()));left-=n;
            }
            for(int i=0;i<work.size()&&left>0;i++){
                if(work.get(i).type()!=0||!accepts.test(i,r.type()))continue;
                int n=Math.min(left,r.max());
                work.set(i,new Slot(r.type(),n,r.max()));left-=n;
            }
            if(left>0)return new Result(false,List.copyOf(starting),
                "original source chest has insufficient free space");
        }
        return new Result(true,List.copyOf(work),"");
    }
}
