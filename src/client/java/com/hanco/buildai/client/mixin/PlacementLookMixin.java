package com.hanco.buildai.client.mixin;

import com.hanco.buildai.client.PlacementMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft's Mouse.updateMouse() rotates mc.player, even when cameraEntity
 * is another entity. Route those exact vanilla mouse deltas to the detached
 * BuildAI camera instead of rotating the stationary real player.
 */
@Mixin(Entity.class)
public abstract class PlacementLookMixin {
    @Inject(method="changeLookDirection(DD)V",at=@At("HEAD"),cancellable=true)
    private void buildai$routeMouseToPlacement(double deltaX,double deltaY,CallbackInfo ci){
        MinecraftClient client=MinecraftClient.getInstance();
        if(client.player==(Object)this && PlacementMode.active()){
            PlacementMode.turnCamera(deltaX,deltaY);
            ci.cancel();
        }
    }
}
