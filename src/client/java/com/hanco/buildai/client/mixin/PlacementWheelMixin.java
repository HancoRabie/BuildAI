package com.hanco.buildai.client.mixin;

import com.hanco.buildai.client.PlacementMode;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No observer camera, no mouse-look interception.
 * Intercept ONLY scroll during an unlocked, in-world blueprint preview;
 * otherwise Minecraft keeps its ordinary hotbar and GUI scrolling.
 */
@Mixin(Mouse.class)
public abstract class PlacementWheelMixin {
    @Inject(method="onMouseScroll(JDD)V",at=@At("HEAD"),cancellable=true)
    private void buildai$moveWorldZ(long window,double horizontal,double vertical,CallbackInfo ci) {
        if(PlacementMode.scroll(vertical))ci.cancel();
    }
}
