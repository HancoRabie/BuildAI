package com.hanco.buildai.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** User-managed private build ratings and optional labeled decorative example packs. */
public final class BuildAIFeedbackScreen extends Screen {
    private final Screen parent;
    private List<Path> builds=List.of();
    private Path selected;
    private int offset=0,stars=4;
    private TextFieldWidget noteField,pathField;
    private String noteText="",pathText="";
    private String status="Select a saved build, write feedback or scan labeled decoration examples.";
    private boolean busy=false;
    public BuildAIFeedbackScreen(Screen parent){
        super(Text.literal("BuildAI | Feedback & Style Library"));
        this.parent=parent;
    }
    @Override protected void init(){
        int w=Math.min(550,Math.max(150,width-18)),x=(width-w)/2;
        int row=Math.max(17,Math.min(23,(height-40)/16));
        int top=57,buttonH=Math.max(15,row-2),gap=4,half=(w-gap)/2;
        try{builds=BlueprintLibrary.files();}
        catch(Exception ex){status="Could not list saved blueprints: "+ex.getMessage();}
        int visible=Math.max(1,Math.min(4,(height-180)/row));
        for(int i=0;i<visible&&offset+i<builds.size();i++){
            Path p=builds.get(offset+i);
            String name=p.getFileName().toString();
            addDrawableChild(ButtonWidget.builder(Text.literal(
                (p.equals(selected)?"* ":"")+
                textRenderer.trimToWidth(name,w-24)),b->{
                    selected=p;
                    status="Selected "+name+". Save a review or scan its [BuildAI] signs.";
                }).dimensions(x,top+i*row,w,buttonH).build());
        }
        int navY=top+visible*row+2;
        addDrawableChild(ButtonWidget.builder(Text.literal("Previous Builds"),b->{
            capture();offset=Math.max(0,offset-visible);clearChildren();init();
        }).dimensions(x,navY,half,buttonH).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("More Builds"),b->{
            capture();offset=Math.min(Math.max(0,builds.size()-visible),offset+visible);
            clearChildren();init();
        }).dimensions(x+half+gap,navY,half,buttonH).build());

        pathField=new TextFieldWidget(textRenderer,x,navY+row+4,w,buttonH,Text.literal("Optional external schematic"));
        pathField.setMaxLength(1024);
        pathField.setText(pathText);
        pathField.setPlaceholder(Text.literal("Optional: paste a full path to an imported .schem / .nbt file"));
        addDrawableChild(pathField);

        noteField=new TextFieldWidget(textRenderer,x,navY+2*row+8,w,buttonH,Text.literal("Your BuildAI feedback"));
        noteField.setMaxLength(1100);
        noteField.setText(noteText);
        noteField.setPlaceholder(Text.literal("What should future builds repeat or avoid? E.g. use more natural balconies"));
        addDrawableChild(noteField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Rating: "+stars+"/5 ★ (click to change)"),
            b->{stars=stars%5+1;b.setMessage(Text.literal("Rating: "+stars+"/5 ★ (click to change)"));})
            .dimensions(x,navY+3*row+10,w,buttonH).build());

        int actions=navY+4*row+12;
        addDrawableChild(ButtonWidget.builder(Text.literal("Save Feedback for Future Builds"),
            b->saveFeedback()).dimensions(x,actions,w,buttonH).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Learn Labeled Decorations from Schematic"),
            b->addExamples()).dimensions(x,actions+row,w,buttonH).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Analyze Whole Builds / Reference Library"),
            widget->{capture();client.setScreen(new BuildAIArchitectureScreen(this));})
            .dimensions(x,actions+2*row,w,buttonH).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Back to BuildAI"),
            widget->close()).dimensions(x,Math.min(height-26,actions+3*row+4),w,buttonH).build());
    }
    private void capture(){
        if(pathField!=null)pathText=pathField.getText();
        if(noteField!=null)noteText=noteField.getText();
    }
    private Path chosen() throws Exception{
        capture();
        if(!pathText.isBlank())return BlueprintLibrary.resolveUserPath(pathText);
        if(selected!=null)return selected;
        throw new IllegalArgumentException("Select a saved build above, or paste a schematic path");
    }
    private void saveFeedback(){
        if(busy)return;
        try{
            Path path=chosen();
            if(noteText.isBlank()){status="Write your feedback first.";return;}
            busy=true;
            String text=noteText;int rating=stars;
            status="Saving local feedback...";
            CompletableFuture.runAsync(()->{
                try{BuildAIStyleMemory.review(path,rating,text);}
                catch(Exception ex){throw new RuntimeException(ex);}
            }).whenComplete((v,error)->MinecraftClient.getInstance().execute(()->{
                busy=false;
                if(error!=null)status="Feedback failed: "+root(error);
                else status="Feedback saved locally; future AI generations will consider it.";
            }));
        }catch(Exception ex){status=ex.getMessage();}
    }
    private void addExamples(){
        if(busy)return;
        try{
            Path path=chosen();
            busy=true;
            status="Scanning sign labels and nearby block geometry...";
            CompletableFuture.supplyAsync(()->{
                try{return BuildAIStyleMemory.addReference(path);}
                catch(Exception ex){throw new RuntimeException(ex);}
            }).whenComplete((count,error)->MinecraftClient.getInstance().execute(()->{
                busy=false;
                if(error!=null)status="Style pack failed: "+root(error);
                else status=count+" decoration examples saved. Future AI designs can reference their block layouts.";
            }));
        }catch(Exception ex){status=ex.getMessage();}
    }
    private static String root(Throwable error){
        while(error.getCause()!=null)error=error.getCause();
        return error.getMessage()==null?error.toString():error.getMessage();
    }
    @Override public void renderBackground(DrawContext ctx,int mx,int my,float dt){}
    @Override public void render(DrawContext ctx,int mx,int my,float dt){
        int w=Math.min(550,Math.max(150,width-18)),x=(width-w)/2;
        ctx.fill(x-8,6,x+w+8,height-6,0xF0162831);
        ctx.fill(x-8,6,x+w+8,10,0xFF67DCC5);
        ctx.drawCenteredTextWithShadow(textRenderer,"BuildAI | Feedback & Styles",width/2,15,0xFF70F0CE);
        ctx.drawTextWithShadow(textRenderer,
            textRenderer.trimToWidth("Only reviews and references you save are included in future AI prompts.",w-8),
            x+4,31,0xFFD8E5EF);
        // Keep live feedback above the first file choice; never paint it over
        // the action buttons on smaller Minecraft GUI scale/window heights.
        int statusY=43;
        ctx.drawTextWithShadow(textRenderer,
            textRenderer.trimToWidth(status,w-8),x+4,statusY,0xFFEFD195);
        super.render(ctx,mx,my,dt);
    }
    @Override public void close(){if(client!=null)client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
