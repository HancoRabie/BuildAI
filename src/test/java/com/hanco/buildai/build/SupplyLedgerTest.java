package com.hanco.buildai.build;

import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public final class SupplyLedgerTest {
    @BeforeAll static void setupVanillaRegistries(){net.minecraft.Bootstrap.initialize();}
    private static int count(Inventory inv,net.minecraft.item.Item item){
        int total=0;
        for(int slot=0;slot<inv.size();slot++)
            if(inv.getStack(slot).isOf(item))total+=inv.getStack(slot).getCount();
        return total;
    }

    @Test void returnsEachWithdrawalToTheExactSourceChest(){
        SimpleInventory west=new SimpleInventory(5),east=new SimpleInventory(5);
        BlockPos p1=new BlockPos(1,72,1),p2=new BlockPos(14,72,1);
        west.setStack(0,new ItemStack(Items.STONE,2));
        east.setStack(2,new ItemStack(Items.DIRT,1));
        SupplyLedger ledger=new SupplyLedger();
        ledger.record(p1,0,new ItemStack(Items.STONE),false);
        ledger.record(p2,2,new ItemStack(Items.DIRT),false);
        // Simulate the physical consumption that occurred in construction.
        west.removeStack(0,1);
        east.removeStack(2,1);
        var containers=Map.of(p1,west,p2,east);
        SupplyLedger.Refund result=ledger.refund(containers::get);
        assertTrue(result.success());
        assertEquals(2,result.returned());
        assertEquals(2,count(west,Items.STONE));
        assertEquals(0,count(west,Items.DIRT));
        assertEquals(1,count(east,Items.DIRT));
        assertEquals(0,count(east,Items.STONE));
        assertTrue(ledger.isEmpty());
        assertEquals(0,ledger.refund(containers::get).returned(),
            "Repeated refund requests must NEVER duplicate materials");
    }

    @Test void missingOrFullChestCannotCausePartialOrLostRefund(){
        SimpleInventory west=new SimpleInventory(1),east=new SimpleInventory(1);
        BlockPos p1=new BlockPos(1,72,1),p2=new BlockPos(14,72,1);
        SupplyLedger ledger=new SupplyLedger();
        ledger.record(p1,0,new ItemStack(Items.STONE),false);
        ledger.record(p2,0,new ItemStack(Items.DIRT),false);
        assertFalse(ledger.refund(p->p.equals(p1)?west:null).success());
        assertTrue(west.isEmpty(),"No refunds should have been committed");
        assertEquals(2,ledger.pending());
        east.setStack(0,new ItemStack(Items.GOLD_BLOCK,64));
        var containers=Map.of(p1,west,p2,east);
        assertFalse(ledger.refund(containers::get).success(),
            "No room in second chest must not refund first chest partially");
        assertTrue(west.isEmpty());
        assertEquals(2,ledger.pending());
        east.clear();
        assertTrue(ledger.refund(containers::get).success());
        assertEquals(1,count(west,Items.STONE));
        assertEquals(1,count(east,Items.DIRT));
    }

    @Test void fluidBucketRefundExchangesBackEmptyBucketWithoutDuplication(){
        SimpleInventory inv=new SimpleInventory(3);
        BlockPos source=new BlockPos(3,68,4);
        inv.setStack(0,new ItemStack(Items.BUCKET));
        SupplyLedger ledger=new SupplyLedger();
        ledger.record(source,0,new ItemStack(Items.WATER_BUCKET),true);
        var containers=Map.of(source,inv);
        assertTrue(ledger.refund(containers::get).success());
        assertEquals(1,count(inv,Items.WATER_BUCKET));
        assertEquals(0,count(inv,Items.BUCKET));
        assertEquals(0,ledger.refund(containers::get).returned(),
            "Second invocation must be a harmless no-op");
    }

    @Test void removingEmptyBucketFromChestBlocksFluidRefund(){
        SimpleInventory inv=new SimpleInventory(3);
        BlockPos source=new BlockPos(3,68,4);
        SupplyLedger ledger=new SupplyLedger();
        ledger.record(source,0,new ItemStack(Items.LAVA_BUCKET),true);
        var containers=Map.of(source,inv);
        assertFalse(ledger.refund(containers::get).success());
        assertEquals(0,count(inv,Items.LAVA_BUCKET));
        assertEquals(1,ledger.pending());
        inv.setStack(0,new ItemStack(Items.BUCKET));
        assertTrue(ledger.refund(containers::get).success());
        assertEquals(1,count(inv,Items.LAVA_BUCKET));
    }
}
