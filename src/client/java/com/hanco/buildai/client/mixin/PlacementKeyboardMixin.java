package com.hanco.buildai.client.mixin;

import com.hanco.buildai.client.PlacementInputPolicy;
import com.hanco.buildai.client.PlacementMode;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

/**
 * Placement uses raw, CONTEXTUAL input instead of registering global
 * Minecraft KeyBindings that steal G, Enter, arrows or Cobblemon's R.
 *
 * Higher injection priority captures a preview key before vanilla's
 * KeyBinding dispatch. Unrelated games/screens get unmodified input.
 * A key's repeat/release is swallowed only if its PRESS was captured.
 */
@Mixin(value=Keyboard.class,priority=1500)
public abstract class PlacementKeyboardMixin {
    @Unique private final Set<Integer> buildai$claimedKeys=new HashSet<>();

    @Inject(method="onKey(JIIII)V",at=@At("HEAD"),cancellable=true)
    private void buildai$onContextKey(long window,int key,int scancode,
                                    int action,int modifiers,CallbackInfo ci){
        MinecraftClient mc=MinecraftClient.getInstance();
        if(mc==null||window!=mc.getWindow().getHandle())return;

        if(action==GLFW.GLFW_RELEASE){
            if(buildai$claimedKeys.remove(key))ci.cancel();
            return;
        }

        boolean inWorld=mc.player!=null&&mc.world!=null;
        var command=PlacementInputPolicy.action(inWorld,mc.currentScreen==null,
            PlacementMode.active(),
            com.hanco.buildai.client.BuildAIClient.selecting,
            PlacementMode.groundSelected(),PlacementMode.waitingForLock(),key);

        if(action==GLFW.GLFW_PRESS){
            // A new press invalidates stale state from a lost focus/release.
            buildai$claimedKeys.remove(key);
            if(command==PlacementInputPolicy.Action.NONE)return;
            buildai$claimedKeys.add(key);
            buildai$perform(command,modifiers);
            ci.cancel();
            return;
        }

        if(action==GLFW.GLFW_REPEAT&&buildai$claimedKeys.contains(key)){
            if(command==PlacementInputPolicy.Action.LEFT||
               command==PlacementInputPolicy.Action.RIGHT||
               command==PlacementInputPolicy.Action.UP||
               command==PlacementInputPolicy.Action.DOWN)
                buildai$perform(command,modifiers);
            ci.cancel();
        }
    }

    @Unique private static void buildai$perform(PlacementInputPolicy.Action command,int modifiers){
        boolean ctrl=(modifiers&GLFW.GLFW_MOD_CONTROL)!=0;
        switch(command){
            case FREEZE->PlacementMode.snapGround();
            case LOCK->PlacementMode.lock();
            case RETRY->PlacementMode.retry();
            case LEFT->PlacementMode.arrow(-1,ctrl);
            case RIGHT->PlacementMode.arrow(1,ctrl);
            case UP->PlacementMode.arrow(2,ctrl);
            case DOWN->PlacementMode.arrow(-2,ctrl);
            case NONE->{}
        }
    }
}
