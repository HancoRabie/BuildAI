package com.hanco.buildai.client.mixin;

import com.hanco.buildai.client.PlacementMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla reassigns the camera to the player during client updates.
 * Never allow that to steal an active BuildAI detached preview view.
 * PlacementMode.stop() disables this guard before restoring the real player.
 */
@Mixin(MinecraftClient.class)
public abstract class PlacementCameraLockMixin {
    @Inject(method="setCameraEntity",at=@At("HEAD"),cancellable=true)
    private void buildai$keepPlacementCamera(Entity requested,CallbackInfo ci){
        if(PlacementMode.mustKeepCamera(requested))ci.cancel();
    }
}
