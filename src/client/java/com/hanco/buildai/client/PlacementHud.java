package com.hanco.buildai.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;

/** Ground-based preview controls: vanilla camera, movement and Creative flight. */
public final class PlacementHud {
    private PlacementHud(){}
    public static void register(){
        HudRenderCallback.EVENT.register((ctx,tickDelta)->{
            MinecraftClient mc=MinecraftClient.getInstance();
            if(mc.player==null||mc.options.hudHidden||!PlacementMode.active())return;
            BlockPos pos=BuildAIClient.previewOrigin;
            String headline=BuildAIClient.previewLocked
                ?"BuildAI | LOCATION LOCKED"
                :"BuildAI | CHOOSE GROUND LOCATION";
            String controls=BuildAIClient.previewLocked
                ?"Walk/fly normally to inspect | R retries placement"
                :"Normal movement/Creative flight | Aim at ground and press G";
            String action=BuildAIClient.previewLocked
                ?"F8 > Confirm & Build, or type /buildai confirm"
                :"Enter locks XYZ after G | R retry | F8 menu";
            int left=8,top=8,width=Math.max(90,Math.min(mc.getWindow().getScaledWidth()-16,410));
            ctx.fill(left-5,top-5,left+width,top+47,0xDD111D23);
            ctx.drawTextWithShadow(mc.textRenderer,headline,left,top,0x55EDAF);
            ctx.drawTextWithShadow(mc.textRenderer,mc.textRenderer.trimToWidth(controls,width-8),left,top+12,0xE7F3F4);
            ctx.drawTextWithShadow(mc.textRenderer,mc.textRenderer.trimToWidth(action,width-8),left,top+24,0xC5D2DC);
            if(pos!=null)ctx.drawTextWithShadow(mc.textRenderer,
                mc.textRenderer.trimToWidth("Origin: X "+pos.getX()+" / Y "+pos.getY()+" / Z "+pos.getZ(),width-8),
                left,top+36,0xF1D18F);
        });
    }
}
