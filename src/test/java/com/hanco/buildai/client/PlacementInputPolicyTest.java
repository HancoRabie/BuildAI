package com.hanco.buildai.client;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static com.hanco.buildai.client.PlacementInputPolicy.Action.*;
import static org.junit.jupiter.api.Assertions.*;

public final class PlacementInputPolicyTest {
    private static PlacementInputPolicy.Action pick(boolean world,boolean gui,
        boolean active,boolean selecting,boolean anchored,boolean waiting,int key){
        return PlacementInputPolicy.action(world,!gui,active,selecting,anchored,waiting,key);
    }

    @Test void allPreviewKeysAreReleasedInOrdinaryGameplay(){
        int[] keys={GLFW.GLFW_KEY_G,GLFW.GLFW_KEY_ENTER,
            GLFW.GLFW_KEY_KP_ENTER,GLFW.GLFW_KEY_R,
            GLFW.GLFW_KEY_LEFT,GLFW.GLFW_KEY_RIGHT,GLFW.GLFW_KEY_UP,
            GLFW.GLFW_KEY_DOWN,GLFW.GLFW_KEY_F8,GLFW.GLFW_KEY_W,
            GLFW.GLFW_KEY_A,GLFW.GLFW_KEY_S,GLFW.GLFW_KEY_D};
        for(int key:keys){
            assertEquals(NONE,pick(true,false,false,false,false,false,key),
                "No BuildAI preview means Minecraft/Cobblemon owns key "+key);
            assertEquals(NONE,pick(true,false,true,false,true,false,key),
                "Locked preview means Minecraft/Cobblemon owns key "+key);
            assertEquals(NONE,pick(true,true,true,true,true,false,key),
                "Never intercept any mod GUI, chat or container for key "+key);
            assertEquals(NONE,pick(false,false,true,true,true,false,key),
                "No world/player loaded for key "+key);
            assertEquals(NONE,pick(true,false,true,true,true,true,key),
                "Waiting for placement server reply must release key "+key);
        }
    }

    @Test void previewOwnsOnlyControlsThatCurrentlyHaveAnAction(){
        assertEquals(FREEZE,pick(true,false,true,true,false,false,GLFW.GLFW_KEY_G));
        assertEquals(LOCK,pick(true,false,true,true,false,false,GLFW.GLFW_KEY_ENTER));
        assertEquals(LOCK,pick(true,false,true,true,true,false,GLFW.GLFW_KEY_KP_ENTER));
        assertEquals(RETRY,pick(true,false,true,true,false,false,GLFW.GLFW_KEY_R));
        assertEquals(NONE,pick(true,false,true,true,false,false,GLFW.GLFW_KEY_LEFT),
            "Arrows are NOT captured until G freezes preview");
        assertEquals(NONE,pick(true,false,true,true,false,false,GLFW.GLFW_KEY_UP),
            "Arrows remain available to other mods while preview follows player");
        assertEquals(LEFT,pick(true,false,true,true,true,false,GLFW.GLFW_KEY_LEFT));
        assertEquals(RIGHT,pick(true,false,true,true,true,false,GLFW.GLFW_KEY_RIGHT));
        assertEquals(UP,pick(true,false,true,true,true,false,GLFW.GLFW_KEY_UP));
        assertEquals(DOWN,pick(true,false,true,true,true,false,GLFW.GLFW_KEY_DOWN));
        assertEquals(NONE,pick(true,false,true,true,true,false,GLFW.GLFW_KEY_F8),
            "The F8 global launcher is separate; never cancel it in this interceptor");
        assertEquals(NONE,pick(true,false,true,true,true,false,GLFW.GLFW_KEY_SPACE));
    }

    @Test void scrollOnlyOwnedDuringLiveEditableGhostWithoutGui(){
        assertTrue(PlacementInputPolicy.claimsScroll(true,true,true,true,false));
        assertFalse(PlacementInputPolicy.claimsScroll(true,true,false,false,false));
        assertFalse(PlacementInputPolicy.claimsScroll(true,false,true,true,false));
        assertFalse(PlacementInputPolicy.claimsScroll(false,true,true,true,false));
        assertFalse(PlacementInputPolicy.claimsScroll(true,true,true,false,false));
        assertFalse(PlacementInputPolicy.claimsScroll(true,true,true,true,true));
    }

    @Test void capturedPressRepeatAndReleaseCannotLeakToCobblemon(){
        var capture=new PlacementKeyCapture();
        var press=capture.evaluate(GLFW.GLFW_KEY_R,GLFW.GLFW_PRESS,RETRY);
        assertTrue(press.intercept());
        assertEquals(RETRY,press.execute());
        var repeat=capture.evaluate(GLFW.GLFW_KEY_R,GLFW.GLFW_REPEAT,RETRY);
        assertTrue(repeat.intercept());
        assertEquals(NONE,repeat.execute(),"Retry must NOT spam commands on key repeat");
        // Preview can close between PRESS and RELEASE.
        var release=capture.evaluate(GLFW.GLFW_KEY_R,GLFW.GLFW_RELEASE,NONE);
        assertTrue(release.intercept(),"Don't leak unmatched release into another mod");
        assertEquals(NONE,release.execute());
        assertFalse(capture.evaluate(GLFW.GLFW_KEY_R,GLFW.GLFW_PRESS,NONE).intercept(),
            "A new R in normal Cobblemon gameplay must pass through");
        assertFalse(capture.evaluate(GLFW.GLFW_KEY_R,GLFW.GLFW_RELEASE,NONE).intercept());
    }

    @Test void arrowRepeatWorksWhilePreviewRemainsFrozen(){
        var tracker=new PlacementKeyCapture();
        assertFalse(tracker.evaluate(GLFW.GLFW_KEY_LEFT,GLFW.GLFW_PRESS,NONE).intercept(),
            "Do not swallow key while G has not frozen the ghost");
        assertFalse(tracker.evaluate(GLFW.GLFW_KEY_LEFT,GLFW.GLFW_REPEAT,LEFT).intercept(),
            "Do not steal a held key originally pressed outside edit mode");
        assertTrue(tracker.evaluate(GLFW.GLFW_KEY_LEFT,GLFW.GLFW_RELEASE,LEFT).intercept()==false);
        assertEquals(LEFT,tracker.evaluate(GLFW.GLFW_KEY_LEFT,GLFW.GLFW_PRESS,LEFT).execute());
        for(int n=0;n<3;n++)
            assertEquals(LEFT,tracker.evaluate(GLFW.GLFW_KEY_LEFT,GLFW.GLFW_REPEAT,LEFT).execute());
        assertTrue(tracker.evaluate(GLFW.GLFW_KEY_LEFT,GLFW.GLFW_RELEASE,NONE).intercept());
    }

    @Test void noStickyInputsAfterLostFocusAndNewPress(){
        var tracker=new PlacementKeyCapture();
        assertTrue(tracker.evaluate(GLFW.GLFW_KEY_G,GLFW.GLFW_PRESS,FREEZE).intercept());
        // Release was lost while tabbed away. Later, ordinary gameplay
        // press must reset stale key state and go to Minecraft as normal.
        assertFalse(tracker.evaluate(GLFW.GLFW_KEY_G,GLFW.GLFW_PRESS,NONE).intercept());
        assertFalse(tracker.evaluate(GLFW.GLFW_KEY_G,GLFW.GLFW_RELEASE,NONE).intercept());
    }
}
