package com.hanco.buildai.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;

/** Small unobtrusive reminder while using BuildAI's detached placement camera. */
public final class PlacementHud {
    private PlacementHud(){}
    public static void register(){
        HudRenderCallback.EVENT.register((ctx,tickDelta)->{
            MinecraftClient mc=MinecraftClient.getInstance();
            if(mc.player==null||mc.options.hudHidden||!PlacementMode.active())return;
            BlockPos pos=BuildAIClient.previewOrigin;
            String headline=PlacementMode.following()
                ?"BuildAI | MOVE BLUEPRINT"
                :"BuildAI | REVIEW LOCKED POSITION";
            String controls=PlacementMode.following()
                ?"WASD move | Space up | Shift down | Wheel = world Z | G = ground"
                :"WASD/Space/Shift inspect | R = retry placement";
            String action=PlacementMode.following()
                ?"Enter = lock XYZ | R = retry | F8 = menu"
                :"Return to player and use /buildai confirm to construct";
            int left=8,top=8,width=Math.min(mc.getWindow().getScaledWidth()-16,405);
            ctx.fill(left-5,top-5,left+width,top+47,0xBF111D23);
            ctx.drawTextWithShadow(mc.textRenderer,headline,left,top,0x55EDAF);
            ctx.drawTextWithShadow(mc.textRenderer,mc.textRenderer.trimToWidth(controls,width-8),left,top+12,0xE7F3F4);
            ctx.drawTextWithShadow(mc.textRenderer,mc.textRenderer.trimToWidth(action,width-8),left,top+24,0xC5D2DC);
            if(pos!=null)ctx.drawTextWithShadow(mc.textRenderer,
                "Blueprint origin: X "+pos.getX()+" / Y "+pos.getY()+" / Z "+pos.getZ(),
                left,top+36,0xF1D18F);
        });
    }
}
