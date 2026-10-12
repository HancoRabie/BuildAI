package com.hanco.buildai.client;

import com.hanco.buildai.blueprint.Blueprint;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Explicitly regenerates from the exact archived user prompt or revises an
 * archived blueprint. These are different actions: original re-prompt has
 * no previous geometry context, while revision uses the chosen source design.
 *
 * The original file is read-only; any successful result receives its own
 * unique timestamped .schem via BuildAIClient.acceptGenerated.
 */
public final class BuildAIRemixScreen extends Screen {
    private static final int TEAL=0xFF6DE5C5,BLUE=0xFF9BCDF0,
        DIM=0xFFB1C5D2,GOLD=0xFFEBC98A,ERROR=0xFFFF9F97;
    private final BlueprintLibraryScreen library;
    private final BuildAIScreen studio;
    private final Blueprint original;
    private TextFieldWidget entry;
    private boolean revision=false;
    private String recreateText,changeText="",status="";
    private int panel,x,modeTop,inputTop,notesTop,statusTop,actionTop,backTop;

    public BuildAIRemixScreen(BlueprintLibraryScreen library,BuildAIScreen studio,
                              Blueprint original) {
        super(Text.literal("BuildAI | Remake or Revise a Saved Build"));
        this.library=library;this.studio=studio;this.original=original;
        this.recreateText=original.originalPrompt();
        this.status=original.hasOriginalPrompt()
            ?"Exact original prompt recovered. Choose REMAKE to rerun it, or REVISE for specific changes."
            :"Legacy file: original prompt wasn't saved. You may enter it manually to remake, "+
              "or revise using the saved block geometry.";
    }
    private int availableWidth(){return Math.min(720,Math.max(130,width-18));}
    private void saveText(){
        if(entry!=null){
            if(revision)changeText=entry.getText();
            else recreateText=entry.getText();
        }
    }
    private ButtonWidget button(String label,int posX,int posY,int buttonWidth,
                                Runnable click){
        return addDrawableChild(ButtonWidget.builder(
            Text.literal(textRenderer.trimToWidth(label,Math.max(30,buttonWidth-12))),
            widget->click.run()).dimensions(posX,posY,buttonWidth,20).build());
    }
    private void mode(boolean newRevision){
        saveText();
        revision=newRevision;
        clearChildren();
        init();
    }
    @Override protected void init(){
        panel=availableWidth();x=(width-panel)/2;
        modeTop=height<180?31:height<245?45:63;
        inputTop=modeTop+24;
        notesTop=inputTop+26;
        actionTop=height-52;
        backTop=height-26;
        statusTop=Math.min(actionTop-14,Math.max(notesTop+20,height-80));
        int gap=4,half=(panel-gap)/2;
        button(revision?"REMAKE SAME PROMPT":"REMAKE SAME PROMPT  •  SELECTED",
            x,modeTop,half,()->mode(false));
        button(revision?"REVISE SAVED BUILD  •  SELECTED":"REVISE SAVED BUILD",
            x+half+gap,modeTop,half,()->mode(true));
        entry=new TextFieldWidget(textRenderer,x,inputTop,panel,20,
            Text.literal(revision?"Revision instruction":"Archived original request"));
        entry.setMaxLength(4000);
        entry.setText(revision?changeText:recreateText);
        entry.setPlaceholder(Text.literal(revision
            ?"Describe what to change or improve in this saved structure"
            :"Original prompt (enter manually for older schematics)"));
        addDrawableChild(entry);
        button(revision?"GENERATE REVISED COPY":"GENERATE FRESH COPY",
            x,actionTop,panel,20,()->{
                saveText();
                String instruction=(revision?changeText:recreateText).trim();
                if(instruction.isEmpty()){
                    status=revision?"Describe the changes you want applied to this design.":
                        "This legacy file has no original prompt. Type the request to remake it.";
                    return;
                }
                studio.startFromLibrary(original,instruction,revision);
                // The studio will show the generation monitor if authorized.
                // If it cannot begin, its status remains on the studio screen.
            });
        button("BACK TO BLUEPRINT LIBRARY",x,backTop,panel,20,this::close);
    }
    private void print(DrawContext ctx,String value,int py,int color){
        if(py<0||py>height-10)return;
        ctx.drawTextWithShadow(textRenderer,
            textRenderer.trimToWidth(value,Math.max(20,panel)),x,py,color);
    }
    @Override public void renderBackground(DrawContext ctx,int mx,int my,float dt){
        ctx.fill(x-8,5,x+panel+8,height-3,0xF0152938);
        ctx.fill(x-8,5,x+panel+8,8,TEAL);
        if(height>=145)
            ctx.fill(x,modeTop-2,x+panel,modeTop+22,0x88315063);
    }
    @Override public void render(DrawContext ctx,int mx,int my,float dt){
        super.render(ctx,mx,my,dt);
        print(ctx,"BUILDAI  /  REMAKE OR REVISE",12,TEAL);
        print(ctx,original.name,24,BLUE);
        if(height>=180){
            print(ctx,revision
                ?"REVISE: use this saved design as the source; give a change instruction."
                :"REMAKE: use EXACTLY the stored user prompt, without copying the old blocks.",
                Math.max(36,modeTop-13),DIM);
        }
        if(height>=205){
            print(ctx,revision
                ?"Source: "+(original.hasRevisionContext()?
                    "Saved AI design history":"Legacy geometry (converted for revision)")
                :"Source prompt: "+(original.hasOriginalPrompt()?
                    "Archived verbatim":"UNAVAILABLE in this older .schem"),
                notesTop,GOLD);
        }
        if(statusTop>notesTop+3 && statusTop<actionTop-6)
            print(ctx,status,statusTop,
                status.toLowerCase().contains("legacy")?GOLD:DIM);
        print(ctx,"Original file is NEVER overwritten. New output autosaves separately.",
            actionTop-11,TEAL);
    }
    @Override public void close(){if(client!=null)client.setScreen(library);}
    @Override public boolean shouldPause(){return false;}
}
