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
                :"BuildAI | POSITION PREVIEW";
            String controls=BuildAIClient.previewLocked
                ?"Inspect the locked facade normally | R retries"
                :PlacementMode.groundSelected()
                    ?"LEFT/RIGHT: sideways | UP/DOWN: height | CTRL+L/R: rotate"
                    :"Building follows you | G: freeze at visible spot (no block target)";
            String action=BuildAIClient.previewLocked
                ?"F8 > Confirm & Build | R: retry"
                :PlacementMode.waitingForLock()
                    ?"Checking XYZ with server..."
                    :PlacementMode.groundSelected()
                        ?"CTRL+UP/DOWN or wheel: depth | ENTER: lock XYZ"
                        :"G: freeze XYZ | adjust with arrows | ENTER: lock";
            int left=8,top=8,width=Math.max(90,Math.min(mc.getWindow().getScaledWidth()-16,450));
            ctx.fill(left-5,top-5,left+width,top+59,0xDD111D23);
            ctx.drawTextWithShadow(mc.textRenderer,headline,left,top,0x55EDAF);
            ctx.drawTextWithShadow(mc.textRenderer,mc.textRenderer.trimToWidth(controls,width-8),left,top+12,0xE7F3F4);
            ctx.drawTextWithShadow(mc.textRenderer,mc.textRenderer.trimToWidth(action,width-8),left,top+24,0xC5D2DC);
            ctx.drawTextWithShadow(mc.textRenderer,
                mc.textRenderer.trimToWidth("Rotation: "+(PlacementMode.rotation()*90)+
                    " degrees | Preview anchored: "+PlacementMode.groundSelected(),width-8),
                left,top+36,0xFFDDA8);
            if(pos!=null)ctx.drawTextWithShadow(mc.textRenderer,
                mc.textRenderer.trimToWidth("Origin: X "+pos.getX()+" / Y "+pos.getY()+" / Z "+pos.getZ(),width-8),
                left,top+48,0xF1D18F);
        });
    }
}
