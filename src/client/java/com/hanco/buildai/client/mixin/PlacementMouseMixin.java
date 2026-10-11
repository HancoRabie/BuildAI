package com.hanco.buildai.client.mixin;

import com.hanco.buildai.client.PlacementMode;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** World-Z mouse-wheel editing only when detached BuildAI placement is active. */
@Mixin(Mouse.class)
public abstract class PlacementMouseMixin {
    @Inject(method="onMouseScroll",at=@At("HEAD"),cancellable=true)
    private void buildai$scroll(long window,double horizontal,double vertical,CallbackInfo ci){
        if(PlacementMode.following()&&vertical!=0){
            PlacementMode.scroll(vertical);
            ci.cancel(); // don't switch the player's hotbar while editing placement
        }
    }
}
