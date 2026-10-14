package com.hanco.buildai.build;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public final class SupplyChestDiscoveryTest {
    @Test void ordinarySignReadingBuildAIIsEnoughWithoutManualLink(){
        assertTrue(SupplyChestDiscovery.marked("BuildAI"));
        assertTrue(SupplyChestDiscovery.marked(" buildai "));
        assertTrue(SupplyChestDiscovery.marked("BUILDAI"));
        assertFalse(SupplyChestDiscovery.marked("BuildAI Materials"));
        assertFalse(SupplyChestDiscovery.marked("[BuildAI]"));
        assertFalse(SupplyChestDiscovery.marked(null));
    }
    @Test void supplyChestCanBeAtAnySideOfHugeBuild(){
        int originX=100,originY=60,originZ=200,w=280,h=85,d=400;
        assertTrue(SupplyChestDiscovery.inProjectArea(
            originX+275,originY+20,originZ+390,originX,originY,originZ,w,h,d),
            "Signed chest close to opposite corner should be discovered near player");
        assertTrue(SupplyChestDiscovery.inProjectArea(
            originX-24,originY,originZ-24,originX,originY,originZ,w,h,d));
        assertFalse(SupplyChestDiscovery.inProjectArea(
            originX-25,originY,originZ,originX,originY,originZ,w,h,d));
        assertFalse(SupplyChestDiscovery.inProjectArea(
            originX+w+25,originY,originZ,originX,originY,originZ,w,h,d));
    }
}
