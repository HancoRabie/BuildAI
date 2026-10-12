package com.hanco.buildai.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Tiny nonblocking overlay shown after Return to Game; never pauses or starts a new request. */
public final class GenerationHud {
    private GenerationHud(){}
    public static void register(){
        HudRenderCallback.EVENT.register((ctx,tickDelta)->{
            MinecraftClient mc=MinecraftClient.getInstance();
            if(mc.player==null||mc.options.hudHidden||mc.currentScreen!=null)return;
            GenerationProgress progress=BuildAIClient.minimizedProgress();
            if(progress==null)return;
            GenerationProgress.Snapshot s=progress.snapshot();
            if(s.finished() && s.secondsSinceActivity()>30)return;
            int width=Math.min(258,Math.max(134,mc.getWindow().getScaledWidth()-16));
            int x=8,y=PlacementMode.active()?83:9;
            ctx.fill(x-3,y-4,x+width,y+54,0xDC132530);
            ctx.drawTextWithShadow(mc.textRenderer,"BuildAI AI | "+(s.failed()?"FAILED":s.finished()?"READY":"GENERATING"),
                x,y,s.failed()?0xFFDC9790:0xFF78EDCF);
            ctx.drawTextWithShadow(mc.textRenderer,
                mc.textRenderer.trimToWidth(s.stage(),width-9),x,y+11,0xFFF0F3F5);
            long seconds=s.elapsedSeconds();
            ctx.drawTextWithShadow(mc.textRenderer,
                mc.textRenderer.trimToWidth(
                    "Elapsed "+seconds/60+"m "+seconds%60+"s | "+s.receivedCharacters()+" chars",
                    width-9),x,y+22,0xFFDEEAF1);
            ctx.drawTextWithShadow(mc.textRenderer,
                mc.textRenderer.trimToWidth(
                    s.finished()?"Open F8 to review / read errors":
                    "Output "+(long)s.charactersPerSecond()+" chars/s | F8 for details",
                    width-9),x,y+33,0xFFB9D7E0);
            int stripe=(int)((System.nanoTime()/20_000_000L)%(width+34))-34;
            ctx.fill(x,y+46,x+width-6,y+49,0xFF2D4855);
            if(s.finished())
                ctx.fill(x,y+46,x+width-6,y+49,s.failed()?0xFFDD7469:0xFF56D7A5);
            else ctx.fill(x+Math.max(0,stripe),y+46,
                x+Math.min(width-6,stripe+34),y+49,0xFF55DDBF);
        });
    }
}
