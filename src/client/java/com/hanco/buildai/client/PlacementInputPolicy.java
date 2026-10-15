package com.hanco.buildai.client;

import org.lwjgl.glfw.GLFW;

/**
 * Pure policy for RAW keyboard capture. A registered KeyBinding would reserve
 * its key across ALL Minecraft gameplay, even if its action later no-ops.
 * BuildAI therefore has NO globally registered placement keys.
 *
 * Only an editable placement preview in the game world may claim its keys;
 * other GUIs, normal gameplay and a LOCKED preview receive NONE.
 */
public final class PlacementInputPolicy {
    public enum Action { NONE, FREEZE, LOCK, RETRY, LEFT, RIGHT, UP, DOWN }
    private PlacementInputPolicy(){}

    public static Action action(boolean inWorld,boolean screenClosed,
                                boolean placementActive,boolean selecting,
                                boolean anchored,boolean waitingLock,
                                int key){
        if(!inWorld||!screenClosed||!placementActive||!selecting||waitingLock)
            return Action.NONE;
        if(key==GLFW.GLFW_KEY_G)return Action.FREEZE;
        if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER)
            return Action.LOCK;
        // R is a placement shortcut ONLY while editing a ghost. Cobblemon
        // retains R in all other gameplay, including after placement lock.
        if(key==GLFW.GLFW_KEY_R)return Action.RETRY;
        if(!anchored)return Action.NONE;
        return switch(key){
            case GLFW.GLFW_KEY_LEFT ->Action.LEFT;
            case GLFW.GLFW_KEY_RIGHT->Action.RIGHT;
            case GLFW.GLFW_KEY_UP->Action.UP;
            case GLFW.GLFW_KEY_DOWN->Action.DOWN;
            default->Action.NONE;
        };
    }

    public static boolean claimsScroll(boolean inWorld,boolean screenClosed,
                boolean placementActive,boolean selecting,boolean waitingLock){
        return inWorld&&screenClosed&&placementActive&&selecting&&!waitingLock;
    }
}
