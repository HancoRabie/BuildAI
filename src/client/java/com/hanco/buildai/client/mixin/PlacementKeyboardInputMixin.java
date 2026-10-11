package com.hanco.buildai.client.mixin;

import com.hanco.buildai.client.PlacementMode;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freeze real player movement inputs; the camera is visual and may not move the body. */
@Mixin(KeyboardInput.class)
public abstract class PlacementKeyboardInputMixin {
    @Inject(method="tick",at=@At("TAIL"))
    private void buildai$keepPlayerStationary(boolean slowDown,float slowDownFactor,CallbackInfo ci){
        if(!PlacementMode.active())return;
        Input input=(Input)(Object)this;
        input.pressingForward=false;input.pressingBack=false;
        input.pressingLeft=false;input.pressingRight=false;
        input.jumping=false;input.sneaking=false;
        input.movementForward=0;input.movementSideways=0;
    }
}
