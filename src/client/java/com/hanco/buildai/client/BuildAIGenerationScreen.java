package com.hanco.buildai.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import java.util.List;

/**
 * Native Minecraft generation monitor, not a pretend percentage bar.
 * Every step comes from the actual network/stream/compiler and elapsed time
 * is measured by a monotonic clock. It can be hidden while work continues.
 */
public final class BuildAIGenerationScreen extends Screen {
    private final BuildAIScreen parent;
    private final GenerationProgress progress;

    public BuildAIGenerationScreen(BuildAIScreen parent,GenerationProgress progress) {
        super(Text.literal("BuildAI | Generating your structure"));
        this.parent=parent;
        this.progress=progress;
    }

    @Override protected void init(){
        int width=Math.min(440,Math.max(140,this.width-24));
        int x=(this.width-width)/2;
        int center=this.height/2;
        int gap=6,half=(width-gap)/2;
        int footer=Math.min(this.height-27,center+104);
        addDrawableChild(ButtonWidget.builder(Text.literal("Back to BuildAI"),
            b->close()).dimensions(x,footer,half,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Return to Game"),
            b->{if(client!=null)client.setScreen(null);})
            .dimensions(x+half+gap,footer,half,20).build());
    }

    @Override public void render(DrawContext ctx,int mouseX,int mouseY,float delta){
        super.render(ctx,mouseX,mouseY,delta);
        GenerationProgress.Snapshot s=progress.snapshot();
        int panel=Math.min(465,Math.max(150,width-16)),x=(width-panel)/2;
        int center=height/2;
        int top=Math.max(6,center-114);
        ctx.fill(x-5,top-6,x+panel+5,Math.min(height-5,center+137),0xDE14212B);
        ctx.drawCenteredTextWithShadow(textRenderer,"BuildAI | AI Architecture Studio",
            width/2,top+5,0x7EF5C9);
        ctx.drawCenteredTextWithShadow(textRenderer,
            textRenderer.trimToWidth(s.stage(),panel-12),width/2,top+23,
            s.failed()?0xF39886:0xF1F7FA);
        List<OrderedText> details=textRenderer.wrapLines(Text.literal(s.detail()),panel-24);
        for(int i=0;i<Math.min(details.size(),3);i++)
            ctx.drawCenteredTextWithShadow(textRenderer,details.get(i),width/2,top+40+i*11,0xBDD2DF);

        int barX=x+18,barY=top+83,barW=panel-36;
        ctx.fill(barX,barY,barX+barW,barY+7,0xFF354653);
        if(s.finished()){
            ctx.fill(barX,barY,barX+barW,barY+7,s.failed()?0xFFDC746B:0xFF5FE6AC);
        }else {
            // Moving highlight means "working", NOT estimated percent complete.
            int highlight=Math.max(10,barW/5);
            int offset=(int)((System.nanoTime()/25_000_000L)%(barW+highlight))-highlight;
            int from=Math.max(0,offset),to=Math.min(barW,offset+highlight);
            if(to>from)ctx.fill(barX+from,barY,barX+to,barY+7,0xFF69DDC4);
        }
        long seconds=s.elapsedSeconds();
        String elapsed="Elapsed: "+(seconds/60)+"m "+(seconds%60)+"s";
        String received="AI design received: "+s.receivedCharacters()+" characters";
        ctx.drawTextWithShadow(textRenderer,elapsed,x+18,top+98,0xD3E1E8);
        ctx.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(received,panel-36),
            x+18,top+111,0xD3E1E8);
        String note=s.finished()?(s.failed()?"Generation stopped. Your previous blueprint is unchanged.":
            "Blueprint completed; opening its in-game preview.")
            :s.secondsSinceActivity()>=15
                ?"No new data for "+s.secondsSinceActivity()+"s. Still waiting for OpenAI; no percentage is known."
                :"Progress updates reflect real model/network and validation events.";
        ctx.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(note,panel-24),
            x+12,top+127,0xE9C88A);
        // Stage history, drawn only when there is vertical space; never overlap buttons.
        int historyY=top+143,buttonTop=Math.min(height-27,center+104);
        if(historyY+12<buttonTop){
            for(String milestone:s.milestones()){
                if(historyY+11>=buttonTop)break;
                ctx.drawTextWithShadow(textRenderer,textRenderer.trimToWidth("• "+milestone,panel-30),
                    x+15,historyY,0x9CB9C4);
                historyY+=11;
            }
        }
    }

    @Override public boolean shouldPause(){return false;}
    @Override public void close(){
        if(client!=null)client.setScreen(parent);
    }
}
