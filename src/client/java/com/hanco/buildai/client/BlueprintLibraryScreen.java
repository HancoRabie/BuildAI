package com.hanco.buildai.client;

import com.hanco.buildai.blueprint.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Colored, bounded project browser: list, status and actions never overlap. */
public final class BlueprintLibraryScreen extends Screen {
    private static final int MINT=0xFF6DE5C5,BLUE=0xFF8EC9F3,
        BODY=0xFFE4EFF5,DIM=0xFFA6BECA,GOLD=0xFFE8C782;
    private final BuildAIScreen parent;
    private List<Path> saved=List.of();
    private TextFieldWidget pathField;
    private Path selected;
    private String enteredPath="",status="Select a project and click Import Selected.";
    private int offset=0,visible=0,panel=0,x=0,listTop=67,step=28,navY=0,statusY=0;
    private boolean loading=false;

    public BlueprintLibraryScreen(BuildAIScreen parent){
        super(Text.literal("BuildAI | My Blueprints"));this.parent=parent;
    }
    private void refresh(){
        try {
            saved=BlueprintLibrary.files();
            offset=Math.min(offset,Math.max(0,saved.size()-visible));
            if(selected!=null&&!saved.contains(selected))selected=null;
        }catch(Exception ex){
            saved=List.of();status="Cannot read saved blueprints: "+ex.getMessage();
        }
    }
    private ButtonWidget control(String label,int px,int py,int w,int h,Runnable callback){
        ButtonWidget b=ButtonWidget.builder(
            Text.literal(textRenderer.trimToWidth(label,Math.max(30,w-16))),
            button->callback.run()).dimensions(px,py,Math.max(28,w),Math.max(12,h)).build();
        addDrawableChild(b);return b;
    }
    private void rebuild(){
        if(pathField!=null)enteredPath=pathField.getText();
        clearChildren();init();
    }
    private static String label(Path file){
        String name=file.getFileName().toString();
        name=name.replaceFirst("(?i)\\.(schem|schematic|litematic|nbt)$","");
        name=name.replaceFirst("-20[0-9]{6}-[0-9]{6}-[0-9]+$","");
        return name.replace('_',' ').replace('-',' ');
    }
    @Override protected void init(){
        if(pathField!=null)enteredPath=pathField.getText();
        pathField=null;
        panel=Math.min(670,Math.max(150,width-24));x=(width-panel)/2;
        navY=height-115;statusY=navY-24;
        visible=height<255?0:Math.max(1,Math.min(16,(statusY-listTop-9)/step));
        refresh();
        if(visible==0){
            control("BACK",x,Math.max(3,height-25),panel,19,this::close);return;
        }
        int gap=5,third=(panel-2*gap)/3;
        for(int i=0;i<visible;i++){
            int index=offset+i;
            if(index>=saved.size())break;
            Path file=saved.get(index);
            String name=(selected!=null&&selected.equals(file)?"[SELECTED]  ":"")+
                String.format(Locale.ROOT,"%02d  ",index+1)+label(file);
            control(name,x+7,listTop+i*step,panel-14,22,()->{
                selected=file;
                status="Selected: "+file.getFileName();
                rebuild();
            }).active=!loading;
        }
        control("PREVIOUS",x,navY,third,20,()->{
            offset=Math.max(0,offset-visible);rebuild();
        }).active=offset>0&&!loading;
        control("NEXT PAGE",x+third+gap,navY,third,20,()->{
            offset=Math.min(Math.max(0,saved.size()-visible),offset+visible);rebuild();
        }).active=offset+visible<saved.size()&&!loading;
        control("REFRESH",x+2*(third+gap),navY,
            panel-2*(third+gap),20,this::rebuild).active=!loading;
        pathField=new TextFieldWidget(textRenderer,x,navY+27,panel,19,
            Text.literal("Optional external schematic path"));
        pathField.setMaxLength(1024);pathField.setText(enteredPath);
        pathField.setPlaceholder(Text.literal("Optional .schem / .litematic / .nbt file path"));
        addDrawableChild(pathField);
        control("IMPORT SELECTED",x,navY+53,third,20,()->{
            if(selected!=null)load(selected);
            else status="Select a project first.";
        }).active=selected!=null&&!loading;
        control("IMPORT PATH",x+third+gap,navY+53,third,20,()->{
            enteredPath=pathField.getText();
            try{load(BlueprintLibrary.resolveUserPath(enteredPath));}
            catch(Exception ex){status="Invalid path: "+ex.getMessage();}
        }).active=!loading;
        control("BACK TO BUILDAI",x+2*(third+gap),navY+53,
            panel-2*(third+gap),20,this::close);
    }
    private void load(Path file){
        if(loading){status="Import already running.";return;}
        loading=true;status="Importing "+file.getFileName()+"...";
        CompletableFuture.supplyAsync(()->{
            try{
                Blueprint b=SchematicReader.read(file);
                BlockStates.validate(b);
                return b;
            }catch(Exception ex){throw new RuntimeException(ex);}
        }).whenComplete((b,error)->MinecraftClient.getInstance().execute(()->{
            loading=false;
            if(error!=null){
                Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();
                status="Import failed: "+cause.getMessage();return;
            }
            parent.importedFromLibrary(b);
        }));
    }
    private void drawLine(DrawContext ctx,String value,int px,int py,int available,int color){
        ctx.drawTextWithShadow(textRenderer,
            textRenderer.trimToWidth(value,Math.max(14,available)),px,py,color);
    }
    @Override public void renderBackground(DrawContext ctx,int mx,int my,float dt){
        ctx.fill(x-10,5,x+panel+10,height-4,0xEF152938);
        ctx.fill(x-10,5,x+panel+10,8,MINT);
        if(visible>0){
            ctx.fill(x+1,listTop-7,x+panel-1,statusY-3,0x55345061);
            for(int i=0;i<visible&&offset+i<saved.size();i++){
                int y=listTop+i*step;
                ctx.fill(x+2,y-1,x+5,y+22,
                    saved.get(offset+i).equals(selected)?MINT:0xFF406D81);
            }
        }
    }
    @Override public void render(DrawContext ctx,int mx,int my,float dt){
        super.render(ctx,mx,my,dt);
        drawLine(ctx,"BUILDAI  /  PERSONAL BLUEPRINT LIBRARY",
            x+5,14,panel-10,MINT);
        drawLine(ctx,saved.size()+" SAVED  |  "+
            (saved.isEmpty()?"Empty library":"Viewing "+
                (offset+1)+"-"+Math.min(saved.size(),offset+visible))+
            "  |  Minecraft local instance",x+5,30,panel-10,BLUE);
        drawLine(ctx,"Select a project, then Import Selected. Files are not modified.",
            x+5,47,panel-10,DIM);
        if(visible==0){
            drawLine(ctx,"Lower Minecraft GUI Scale or enlarge the window.",
                x+5,Math.min(height-50,65),panel-10,GOLD);return;
        }
        if(saved.isEmpty())drawLine(ctx,"No saved projects yet. Paste a path below to import.",
            x+13,listTop+7,panel-26,DIM);
        drawLine(ctx,status,x+7,statusY,panel-14,
            status.toLowerCase(Locale.ROOT).contains("fail")||
            status.toLowerCase(Locale.ROOT).contains("invalid")?0xFFFFB49D:GOLD);
    }
    @Override public void close(){if(client!=null)client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
