package com.hanco.buildai.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import java.util.List;

/**
 * Interactive fresh-generation recovery. Never resumes a cancelled model
 * response or transmits its assistant messages. Two user-editable fields:
 * original building request and a separate positive clarification. Builds
 * an entirely NEW Responses API request with previous=null.
 */
public final class BuildAIRetryScreen extends Screen {
    private static final int MINT=0xFF71E6C9,BLUE=0xFF96D4F8,
        WHITE=0xFFDEEAF0,GOLD=0xFFE8C585,ERROR=0xFFFFA49B;
    private final BuildAIScreen studio;
    private final GenerationProgress previous;
    private TextFieldWidget original,clarification;
    private ButtonWidget start;
    private String sourceText,additionalText,status="";
    private int x,panel,sourceY,extraY,footerY;
    public BuildAIRetryScreen(BuildAIScreen studio,GenerationProgress progress) {
        super(Text.literal("BuildAI | Refine and Start Fresh"));
        this.studio=studio;this.previous=progress;
        sourceText=progress.originalPrompt();
        additionalText=com.hanco.buildai.blueprint.BlueprintDeliveryPolicy
            .suggestClarification(progress.lastModelLimitation());
        status="Original prompt retained. Add clarification; the next attempt is a NEW request.";
    }
    private void save(){
        if(original!=null)sourceText=original.getText();
        if(clarification!=null)additionalText=clarification.getText();
    }
    private ButtonWidget button(String title,int bx,int by,int bw,Runnable action){
        return addDrawableChild(ButtonWidget.builder(
            Text.literal(textRenderer.trimToWidth(title,Math.max(40,bw-12))),
            b->action.run()).dimensions(bx,by,bw,20).build());
    }
    @Override protected void init(){
        save();
        panel=Math.min(740,Math.max(144,width-18));
        x=(width-panel)/2;
        boolean compact=height<240;
        sourceY=compact?42:72;
        extraY=sourceY+(compact?34:47);
        footerY=height-47;
        // The X controls occupy the right edge of each native text row,
        // rather than covering typed characters, at every Minecraft scale.
        int clearWidth=20,fieldWidth=panel-16-clearWidth;
        original=new TextFieldWidget(textRenderer,x+8,sourceY,fieldWidth,19,
            Text.literal("Original building request"));
        original.setMaxLength(4000);
        original.setText(sourceText);
        original.setPlaceholder(Text.literal("Original prompt — edit freely"));
        addDrawableChild(original);
        ButtonWidget clearOriginal=button("×",x+8+fieldWidth,sourceY,
            clearWidth,()->{
                studio.clearRetainedPrompt();
                sourceText="";
                original.setText("");
                original.setFocused(true);
                status="Original prompt and retained backup cleared. "+
                    "You may enter a completely different request.";
            });
        clearOriginal.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(
            Text.literal("Clear original prompt and local backup")));
        clarification=new TextFieldWidget(textRenderer,x+8,extraY,
            fieldWidth,19,Text.literal("Your added clarification"));
        clarification.setMaxLength(2500);
        clarification.setText(additionalText);
        clarification.setPlaceholder(Text.literal(
            "Add corrections: water flow, redstone, inventory, tests..."));
        addDrawableChild(clarification);
        ButtonWidget clearExtra=button("×",x+8+fieldWidth,extraY,
            clearWidth,()->{
                additionalText="";
                clarification.setText("");
                clarification.setFocused(true);
                status="Clarification cleared; original prompt is unchanged.";
            });
        clearExtra.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(
            Text.literal("Clear added clarification only")));
        int half=(panel-20)/2;
        start=button("START NEW INDEPENDENT GENERATION",x+8,footerY,panel-16,
            this::startFresh);
        button("BACK TO BUILDAI",x+8,footerY+23,half,
            ()->{save(); if(client!=null)client.setScreen(studio);});
        button("RESTORE ORIGINAL PROMPT",x+12+half,footerY+23,
            panel-20-half,()->{
                sourceText=previous.originalPrompt();
                original.setText(sourceText);
                status="Restored the original request. Your clarification is still available.";
            });
    }
    private void startFresh(){
        save();
        if(BuildAIClient.currentGeneration()!=null){
            status="Waiting for the previous request to CLOSE before launching a new one.";
            return;
        }
        if(sourceText.isBlank()){
            status="Enter the original building request first.";return;
        }
        final String merged;
        try{
            merged=com.hanco.buildai.blueprint.FreshGenerationRequest
                .merge(sourceText,additionalText);
        }catch(IllegalArgumentException problem){
            status=problem.getMessage();
            return;
        }
        if(studio.startFreshAfterClarification(merged)){
            // Studio owns generation and opens its normal progress monitor.
        }else{
            status="Cannot launch yet. Wait until the old request has closed, "+
                "then retry. Your text remains intact.";
        }
    }
    @Override public void renderBackground(DrawContext context,int mx,int my,float delta){
        context.fill(x-5,5,x+panel+5,height-3,0xF0152838);
        context.fill(x-5,5,x+panel+5,9,MINT);
    }
    private void draw(DrawContext ctx,String value,int px,int py,int color){
        if(py+9>=footerY||py<0)return;
        ctx.drawTextWithShadow(textRenderer,
            textRenderer.trimToWidth(value,Math.max(20,panel-16)),
            px,py,color);
    }
    @Override public void render(DrawContext ctx,int mx,int my,float delta){
        super.render(ctx,mx,my,delta);
        int top=Math.max(9,Math.min(15,height/16));
        draw(ctx,"BUILDAI  /  FRESH ATTEMPT",x+8,top,MINT);
        draw(ctx,"No previous model output, no old response ID, no stale context.",
            x+8,top+12,BLUE);
        if(height>=240){
            draw(ctx,"ORIGINAL BUILD REQUEST  (editable)",x+8,sourceY-13,WHITE);
            draw(ctx,"YOUR CLARIFICATION  (appended to new request)",
                x+8,extraY-13,WHITE);
        }else{
            draw(ctx,"Original prompt",x+8,sourceY-11,WHITE);
            draw(ctx,"Clarification",x+8,extraY-11,WHITE);
        }
        boolean pending=BuildAIClient.currentGeneration()!=null;
        start.active=!pending;
        int feedbackY=extraY+25;
        if(feedbackY+9<footerY-6){
            draw(ctx,pending
                ?"Closing previous request... you may edit both fields now."
                :"Old request closed. Ready to start a new independent request.",
                x+8,feedbackY,pending?GOLD:MINT);
        }
        int statusY=Math.min(footerY-12,feedbackY+22);
        if(statusY>feedbackY+3)
            draw(ctx,status,x+8,statusY,
                status.startsWith("Cannot")||status.startsWith("Waiting")
                    ||status.startsWith("Combined")?ERROR:GOLD);
    }
    @Override public void close(){
        save();
        if(client!=null)client.setScreen(studio);
    }
    @Override public boolean shouldPause(){return false;}
}
