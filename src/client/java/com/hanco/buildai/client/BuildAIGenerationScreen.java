package com.hanco.buildai.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import java.util.List;

/**
 * Native BuildAI generation monitor: all progress UI is rendered on ONE crisp
 * foreground panel. Mirrors ChainFrames' GUI approach: disable vanilla screen
 * background blur, draw the panel and all text, then draw widgets on top.
 * Milestones are actual events, not fabricated percent-complete estimates.
 */
public final class BuildAIGenerationScreen extends Screen {
    private final BuildAIScreen parent;
    private final GenerationProgress progress;
    private ButtonWidget autoStopButton;
    private ButtonWidget probeButton,refineButton,returnToGameButton;

    private Text autoStopLabel(){
        return Text.literal(progress.autoStopEnabled()
            ?"AUTO-STOP: ON  |  Click to disable (8m / 3m)"
            :"AUTO-STOP: OFF  |  Wait until I cancel");
    }

    public BuildAIGenerationScreen(BuildAIScreen parent,GenerationProgress progress) {
        super(Text.literal("BuildAI | Generating your structure"));
        this.parent=parent;
        this.progress=progress;
    }

    private int panelWidth() {
        return Math.min(560,Math.max(140,width-20));
    }

    @Override protected void init() {
        int panel=panelWidth();
        int x=(width-panel)/2;
        int gap=6, half=(panel-28-gap)/2;
        // Three separate footer rows: navigation, diagnostics/cancel, and
        // the live per-request timeout toggle. No scrolling or overlapping
        // labels even when Minecraft fullscreen changes logical GUI scale.
        int footerY=Math.max(12,height-82);
        addDrawableChild(ButtonWidget.builder(Text.literal("Back to BuildAI"),
            b->close()).dimensions(x+14,footerY,half,20).build());
        returnToGameButton=ButtonWidget.builder(Text.literal("Return to Game (Keep Generating)"),
            b->{
                BuildAIClient.minimizeGeneration(progress);
                if(client!=null)client.setScreen(null);
            })
            .dimensions(x+14+half+gap,footerY,half,20).build();
        addDrawableChild(returnToGameButton);
        probeButton=ButtonWidget.builder(
            Text.literal("CHECK REQUEST (SAFE)"),
            button->BuildAIRequestHealth.check(progress))
            .dimensions(x+14,footerY+24,half,20).build();
        probeButton.active=!progress.snapshot().finished();
        addDrawableChild(probeButton);
        // Cancel is a workflow transition, not just a flag: open an editor
        // immediately, keeping the original text while the old HTTP worker
        // finishes closing. No new request can launch until it is gone.
        refineButton=ButtonWidget.builder(
            Text.literal(progress.snapshot().finished()
                ?"REFINE & START FRESH":"CANCEL & REFINE"),
            button->{
                if(!progress.snapshot().finished())
                    progress.requestCancellation();
                if(client!=null)
                    client.setScreen(new BuildAIRetryScreen(parent,progress));
            }).dimensions(x+14+half+gap,footerY+24,half,20).build();
        addDrawableChild(refineButton);

        autoStopButton=ButtonWidget.builder(autoStopLabel(),button->{
            // This updates the watchdog observing the ALREADY OPEN stream;
            // no prompt resend, no new model instance and no cancellation.
            progress.setAutoStopEnabled(!progress.autoStopEnabled());
            button.setMessage(autoStopLabel());
        }).dimensions(x+14,footerY+48,panel-28,20).build();
        autoStopButton.active=!progress.snapshot().finished();
        autoStopButton.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(
            Text.literal("ON: stop after 8m with no text or 3m stalled output. "+
                "OFF: no silence limit; cancel manually. Re-enable for a "+
                "fresh full grace period.")));
        addDrawableChild(autoStopButton);
    }

    @Override public void renderBackground(DrawContext context,int mouseX,int mouseY,float delta) {
        // Same approach as ChainFrames: no vanilla background blur drawn over
        // the panel. The world remains visible behind our opaque UI shell.
    }

    @Override public void render(DrawContext ctx,int mouseX,int mouseY,float delta) {
        GenerationProgress.Snapshot s=progress.snapshot();
        int panel=panelWidth(),x=(width-panel)/2,footerY=Math.max(12,height-82);
        if(autoStopButton!=null){
            autoStopButton.setMessage(autoStopLabel());
            autoStopButton.active=!s.finished();
        }
        if(probeButton!=null)probeButton.active=!s.finished();
        if(refineButton!=null)refineButton.setMessage(Text.literal(s.finished()
            ?"REFINE & START FRESH":"CANCEL & REFINE"));
        if(returnToGameButton!=null)returnToGameButton.setMessage(Text.literal(
            s.finished()?"Return to Game":"Return to Game (Keep Generating)"));
        int top=Math.max(5,Math.min(20,height/12));
        int bottom=height-6, inset=x+14, usable=panel-28;
        int unit=Math.max(11,Math.min(14,(footerY-top-12)/20));

        // One foreground card contains EVERY progress element and the buttons.
        ctx.fill(x,top,x+panel,bottom,0xF0182630);
        ctx.drawBorder(x,top,panel,bottom-top,0xFF4D6E76);
        ctx.fill(x,top,x+panel,top+4,0xFF5ED7BE);

        ctx.drawCenteredTextWithShadow(textRenderer,
            textRenderer.trimToWidth("BUILDAI  |  AI ARCHITECTURE STUDIO",usable),
            width/2,top+unit,0x77F4CC);

        // Workflow and module are separate from model streaming/auto-stop status.
        ctx.drawCenteredTextWithShadow(textRenderer,
            textRenderer.trimToWidth(s.workflow(),usable),
            width/2,top+unit*2,0xFF80E9CB);

        ctx.drawCenteredTextWithShadow(textRenderer,
            textRenderer.trimToWidth(s.stage(),usable),
            width/2,top+unit*3,s.failed()?0xFFA99B:0xFFF0F7F7);

        // Diagnostics are shown in the VISIBLE top area even at high GUI scale.
        String readableDetail=progress.checkStatus().isBlank()
            ?s.detail():progress.briefCheckStatus();
        List<OrderedText> detailLines=textRenderer.wrapLines(Text.literal(readableDetail),usable-8);
        for(int i=0;i<Math.min(3,detailLines.size());i++) {
            int y=top+unit*4+i*11;
            if(y+11>=footerY)break;
            ctx.drawCenteredTextWithShadow(textRenderer,detailLines.get(i),width/2,y,0xC5D8E1);
        }

        drawIfFits(ctx,progress.lastStreamSummary(),inset,
            top+unit*7,usable,footerY,0xFFB7D8E5);

        // Single slim activity track; ETA is INSIDE the bar rather than
        // occupying an empty opaque block under the statistics. This
        // animation is deliberately indeterminate, not fake percent-done.
        int barY=top+unit*8;
        int barX=inset,barW=usable,barH=12;
        if(barY+barH<footerY){
            ctx.fill(barX,barY,barX+barW,barY+barH,0xFF29424A);
            if(s.finished()){
                ctx.fill(barX,barY,barX+barW,barY+barH,
                    s.failed()?0xFFB95955:0xFF3F977C);
            }else{
                int highlight=Math.max(12,barW/5);
                int offset=(int)((System.nanoTime()/25_000_000L)%
                    (barW+highlight))-highlight;
                int from=Math.max(0,offset),to=Math.min(barW,offset+highlight);
                if(to>from)
                    ctx.fill(barX+from,barY,barX+to,barY+barH,0xFF4DC8AB);
            }
            ctx.drawBorder(barX,barY,barW,barH,0xFF527B82);
            String eta=compactEta(s);
            ctx.drawCenteredTextWithShadow(textRenderer,
                textRenderer.trimToWidth(eta,barW-12),barX+barW/2,
                barY+(barH-9)/2,0xFFFFFFFF);
        }

        long seconds=s.elapsedSeconds();
        drawIfFits(ctx,"Elapsed time: "+(seconds/60)+"m "+(seconds%60)+"s",
            inset,top+unit*9+5,usable,footerY,0xFFDBE7E8);
        drawIfFits(ctx,"AI design received: "+s.receivedCharacters()+" characters",
            inset,top+unit*10+5,usable,footerY,0xFFDBE7E8);
        drawIfFits(ctx,"Output rate (average): "+String.format(java.util.Locale.ROOT,"%.0f",s.charactersPerSecond())+
            " characters/second",inset,top+unit*11+5,usable,footerY,0xFFDBE7E8);
        String check=progress.checkStatus();
        String note=!check.isBlank()?check:
            progress.cancellationRequested()
            ? "Cancellation requested; closing the model stream. Your blueprint is safe."
            : s.finished()
            ? s.failed()?"Generation stopped. Your previous blueprint is unchanged.":
                "Blueprint complete. Opening its in-game review."
            : s.secondsSinceActivity()>=15
                ? "No new design data for "+s.secondsSinceActivity()+
                    "s. "+(progress.autoStopEnabled()
                        ?"Auto-stop ON: 8m initial / 3m after text.":
                         "Auto-stop OFF: request will keep waiting.")
                : "Live updates represent real model, network and validation events.";
        List<OrderedText> notes=textRenderer.wrapLines(Text.literal(note),usable);
        int noteY=top+unit*16;
        for(int i=0;i<Math.min(check.isBlank()?2:7,notes.size());i++) {
            int y=noteY+i*11;
            if(y+11>=footerY-10)break;
            ctx.drawTextWithShadow(textRenderer,notes.get(i),inset,y,0xE6C990);
        }

        // Milestones now live INSIDE the same dark panel. Show the most recent
        // events that fit above the footer; never draw a list beyond the card.
        int historyTop=top+unit*19;
        if(check.isBlank()&&historyTop+13<footerY) {
            ctx.fill(inset,historyTop-6,inset+usable,historyTop-5,0xFF405D68);
            drawIfFits(ctx,"RECENT ACTIVITY",inset,historyTop,usable,footerY,0xFF83E5C9);
            int y=historyTop+14;
            for(String milestone:s.milestones()) {
                if(y+11>=footerY-10)break;
                drawIfFits(ctx,"\u2022 "+milestone,inset+4,y,usable-8,footerY,0xFFD3E5E8);
                y+=12;
            }
        }

        // Native button stays clickable and clearly separated from all
        // other actions. Teal = ongoing with auto-stop disabled; amber =
        // automatic silence protection enabled.
        if(autoStopButton!=null)
            ctx.fill(inset,footerY+46,inset+usable,footerY+47,
                progress.autoStopEnabled()?0xFFE7C27D:0xFF6DE5C5);

        // Minecraft Screen.render draws the widgets and potentially its normal
        // background; our renderBackground override above makes that a no-op.
        // Calling it LAST keeps buttons, text and history sharp and clickable.
        super.render(ctx,mouseX,mouseY,delta);
    }

    private String compactEta(GenerationProgress.Snapshot snapshot){
        if(snapshot.finished())return snapshot.failed()
            ?"Generation failed":"Blueprint ready";
        String estimate=GenerationHistory.estimate(snapshot,progress.category());
        String prefix="Estimated time remaining: ";
        if(estimate.startsWith(prefix))estimate=estimate.substring(prefix.length());
        int explanatory=estimate.indexOf(" (rough;");
        if(explanatory>=0)estimate=estimate.substring(0,explanatory);
        if(estimate.startsWith("uncertain"))
            return "ETA uncertain - longer than previous builds";
        if(estimate.startsWith("learning"))
            return snapshot.receivedCharacters()==0
                ?"Waiting for model output...":"Estimating remaining time...";
        return "ETA "+estimate;
    }

    private void drawIfFits(DrawContext ctx,String text,int x,int y,int maxWidth,
                            int footerY,int color) {
        if(y+10>=footerY)return;
        ctx.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(text,maxWidth),x,y,color);
    }

    @Override public boolean shouldPause(){return false;}
    @Override public void close(){if(client!=null)client.setScreen(parent);}
}
