package com.hanco.buildai.client;

import org.lwjgl.glfw.GLFW;
import java.util.HashSet;
import java.util.Set;

/**
 * Finite-state ownership of preview key press/repeat/release. A key receives
 * NO interception unless BuildAI was editing a preview at the moment of
 * the original PRESS. Captured repeats/releases cannot leak into other mods
 * if that press changes preview state or opens a GUI.
 */
public final class PlacementKeyCapture {
    public record Decision(boolean intercept,PlacementInputPolicy.Action execute){}
    private final Set<Integer> pressed=new HashSet<>();

    public Decision evaluate(int key,int glfwAction,PlacementInputPolicy.Action inMode){
        if(glfwAction==GLFW.GLFW_RELEASE){
            boolean wasCaptured=pressed.remove(key);
            return new Decision(wasCaptured,PlacementInputPolicy.Action.NONE);
        }
        if(glfwAction==GLFW.GLFW_PRESS){
            // Handle stale state after alt-tab/window focus loss.
            pressed.remove(key);
            if(inMode==PlacementInputPolicy.Action.NONE)
                return new Decision(false,PlacementInputPolicy.Action.NONE);
            pressed.add(key);
            return new Decision(true,inMode);
        }
        if(glfwAction==GLFW.GLFW_REPEAT && pressed.contains(key)){
            boolean move=inMode==PlacementInputPolicy.Action.LEFT ||
                inMode==PlacementInputPolicy.Action.RIGHT ||
                inMode==PlacementInputPolicy.Action.UP ||
                inMode==PlacementInputPolicy.Action.DOWN;
            return new Decision(true,move?inMode:PlacementInputPolicy.Action.NONE);
        }
        return new Decision(false,PlacementInputPolicy.Action.NONE);
    }

    public void clear(){pressed.clear();}
}
