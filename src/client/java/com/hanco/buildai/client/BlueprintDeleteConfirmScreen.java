package com.hanco.buildai.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Explicit two-step PERMANENT deletion: a selected local saved schematic
 * cannot be deleted by clicking its row, selecting its feedback, or
 * pressing Escape. No file deletion until the player clicks the red
 * confirmation action. I/O runs off the Minecraft render thread.
 */
public final class BlueprintDeleteConfirmScreen extends Screen {
    private static final int BG=0xF0192B39,TEAL=0xFF6DE5C5,
        WHITE=0xFFE8F0F5,RED=0xFFFF968B,GOLD=0xFFF1C986,DIM=0xFFB4CAD8;
    private final Screen previous;
    private final Path target;
    private final Consumer<BlueprintLibrary.DeletionResult> afterDelete;
    private boolean working=false;
    private String error="";
    private ButtonWidget confirm;
    private int panel,x,buttonY;

    public BlueprintDeleteConfirmScreen(Screen previous,Path target,
                           Consumer<BlueprintLibrary.DeletionResult> afterDelete){
        super(Text.literal("BuildAI | Permanently Delete Blueprint"));
        this.previous=previous;
        this.target=target;
        this.afterDelete=afterDelete;
    }

    @Override protected void init(){
        panel=Math.min(700,Math.max(110,width-16));
        x=(width-panel)/2;
        buttonY=Math.max(42,height-31);
        int gap=5,half=(panel-gap)/2;
        confirm=addDrawableChild(ButtonWidget.builder(
            Text.literal("DELETE FILE PERMANENTLY"),
            button->delete()).dimensions(x,buttonY,half,20).build());
        confirm.active=!working;
        confirm.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(
            Text.literal("Immediately deletes this schematic from Minecraft's BuildAI save directory. Not undoable.")));
        ButtonWidget cancel=addDrawableChild(ButtonWidget.builder(
            Text.literal("CANCEL — KEEP FILE"),
            button->close()).dimensions(x+half+gap,buttonY,panel-half-gap,20).build());
        cancel.active=!working;
    }

    private void delete(){
        if(working)return;
        working=true;
        error="";
        if(confirm!=null)confirm.active=false;
        CompletableFuture.supplyAsync(()->{
            try{return BlueprintLibrary.deleteSaved(target);}
            catch(Exception ex){throw new java.util.concurrent.CompletionException(ex);}
        }).whenComplete((result,failure)->MinecraftClient.getInstance().execute(()->{
            working=false;
            if(failure!=null){
                Throwable cause=failure;
                while(cause.getCause()!=null)cause=cause.getCause();
                error="Deletion failed: "+(cause.getMessage()==null?
                    cause.getClass().getSimpleName():cause.getMessage());
                if(confirm!=null)confirm.active=true;
                return;
            }
            afterDelete.accept(result);
            if(client!=null)client.setScreen(previous);
        }));
    }

    private void line(DrawContext ctx,String text,int y,int color){
        ctx.drawTextWithShadow(textRenderer,
            textRenderer.trimToWidth(text,Math.max(20,panel-20)),
            x+10,y,color);
    }
    @Override public void renderBackground(DrawContext ctx,int mouseX,int mouseY,float delta){
        ctx.fill(x-9,6,x+panel+9,height-4,BG);
        ctx.fill(x-9,6,x+panel+9,10,RED);
        ctx.fill(x+6,32,x+panel-6,33,0xFF6D4E51);
        // Native Minecraft widgets remain readable/keyboard focusable.
        // The irreversible action is framed with a muted red border.
        int half=(panel-5)/2;
        ctx.fill(x-3,buttonY-3,x+half+3,buttonY+23,0xBB522C32);
        ctx.drawBorder(x-3,buttonY-3,half+6,26,RED);
    }
    @Override public void render(DrawContext ctx,int mouseX,int mouseY,float delta){
        super.render(ctx,mouseX,mouseY,delta);
        line(ctx,"BUILDAI / DELETE SAVED BLUEPRINT",13,RED);
        line(ctx,"PERMANENT DELETION — CANNOT BE UNDONE",height<185?28:42,RED);
        int y=height<185?43:60;
        line(ctx,"This will DELETE the actual schematic file from",y,WHITE);
        line(ctx,"Minecraft's buildai/blueprints save directory.",y+11,WHITE);
        if(height>=160){
            line(ctx,"Not just hidden: saved reviews for this file are removed.",y+25,GOLD);
            line(ctx,"File: "+target.getFileName(),y+40,TEAL);
        }
        if(height>=210){
            List<OrderedText> pathLines=textRenderer.wrapLines(
                Text.literal(target.toAbsolutePath().normalize().toString()),
                Math.max(30,panel-24));
            int at=y+55;
            for(int i=0;i<Math.min(3,pathLines.size())&&at+9<buttonY-7;i++,at+=11)
                ctx.drawTextWithShadow(textRenderer,pathLines.get(i),x+10,at,DIM);
        }
        if(!error.isBlank())
            line(ctx,error,Math.max(y+10,buttonY-13),RED);
        else if(working)
            line(ctx,"Deleting the file from disk...",Math.max(y+10,buttonY-13),GOLD);
    }
    @Override public void close(){
        if(working)return;
        if(client!=null)client.setScreen(previous);
    }
    @Override public boolean shouldPause(){return false;}
}
