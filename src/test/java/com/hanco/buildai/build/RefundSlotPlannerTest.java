package com.hanco.buildai.build;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public final class RefundSlotPlannerTest {
    private static RefundSlotPlanner.Slot slot(int id,int count){
        return new RefundSlotPlanner.Slot(id,count,64);
    }
    private static RefundSlotPlanner.Receipt item(int preferred,int type){
        return new RefundSlotPlanner.Receipt(preferred,type,1,64,false);
    }

    @Test void refundsExactMaterialToPreferredSourceSlot(){
        var before=List.of(slot(1,2),RefundSlotPlanner.Slot.empty());
        var result=RefundSlotPlanner.plan(before,List.of(item(0,1)),99,(i,t)->true);
        assertTrue(result.valid());
        assertEquals(3,result.slots().get(0).count());
        assertEquals(0,result.slots().get(1).type());
        assertEquals(2,before.get(0).count(),"Planner never mutates real inventory");
    }

    @Test void failsOnFullInventoryWithoutAnyPartialMutation(){
        var before=List.of(slot(1,64),slot(2,64));
        var result=RefundSlotPlanner.plan(before,List.of(item(0,1)),99,(i,t)->true);
        assertFalse(result.valid());
        assertEquals(before,result.slots());
        assertTrue(result.failure().contains("space"));
    }

    @Test void originalSlotOccupiedReturnsItemToAnotherAvailableSlot(){
        var before=List.of(slot(3,1),RefundSlotPlanner.Slot.empty());
        var result=RefundSlotPlanner.plan(before,List.of(item(0,1)),99,(i,t)->true);
        assertTrue(result.valid());
        assertEquals(3,result.slots().get(0).type());
        assertEquals(1,result.slots().get(1).type());
    }

    @Test void receiptBatchMustAllFitOrNoneCanBeCommitted(){
        var before=List.of(RefundSlotPlanner.Slot.empty());
        var result=RefundSlotPlanner.plan(before,List.of(item(0,1),item(0,2)),99,(i,t)->true);
        assertFalse(result.valid(),"Distinct items need distinct slots");
        assertEquals(before,result.slots(),"No partial refund can commit");
    }

    @Test void fluidBucketMustTradeAnEmptyBucketForAFullOne(){
        var before=List.of(slot(50,1),RefundSlotPlanner.Slot.empty());
        var receipt=new RefundSlotPlanner.Receipt(0,51,1,1,true);
        var result=RefundSlotPlanner.plan(before,List.of(receipt),50,(i,t)->true);
        assertTrue(result.valid());
        assertEquals(51,result.slots().get(0).type());
        assertEquals(1,result.slots().get(0).count());
        assertEquals(0,result.slots().get(1).type());
        var withoutEmpty=RefundSlotPlanner.plan(
            List.of(RefundSlotPlanner.Slot.empty()),List.of(receipt),50,(i,t)->true);
        assertFalse(withoutEmpty.valid());
        assertTrue(withoutEmpty.failure().contains("bucket"));
    }

    @Test void refusesForbiddenSlotsAndHonorsItemMaxStack(){
        var before=List.of(RefundSlotPlanner.Slot.empty(),
            RefundSlotPlanner.Slot.empty());
        var receipt=new RefundSlotPlanner.Receipt(0,11,1,1,false);
        var result=RefundSlotPlanner.plan(before,List.of(receipt),99,
            (slot,type)->slot==1);
        assertTrue(result.valid());
        assertEquals(0,result.slots().get(0).type());
        assertEquals(11,result.slots().get(1).type());
        assertEquals(1,result.slots().get(1).max());
    }

    @Test void ninetyNinePercentFoundationDoesNotAffectRefundSlots(){
        // Foundation exclusions and temporary construction scaffolds never
        // produce receipts; only one genuine chest debit appears here.
        var result=RefundSlotPlanner.plan(
            List.of(RefundSlotPlanner.Slot.empty()),
            List.of(item(0,7)),99,(i,t)->true);
        assertTrue(result.valid());
        assertEquals(1,result.slots().get(0).count());
    }
}
