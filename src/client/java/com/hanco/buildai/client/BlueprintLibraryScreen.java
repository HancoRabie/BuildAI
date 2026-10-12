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

/**
 * Responsive Minecraft-native schematic browser. Positioning uses *scaled*
 * Minecraft GUI dimensions; fullscreen can have a SMALLER logical height
 * than windowed at auto GUI scale. Never hide the list at an arbitrary
 * threshold or render content outside its reserved viewport.
 */
public final class BlueprintLibraryScreen extends Screen {
    private static final int MINT=0xFF6DE5C5,BLUE=0xFF8EC9F3,
        BODY=0xFFE4EFF5,DIM=0xFFA6BECA,GOLD=0xFFE8C782,
        RED=0xFFFFA69D;
    private final BuildAIScreen parent;
    private List<Path> saved=List.of();
    private TextFieldWidget pathField;
    private Path selected;
    private String enteredPath="",status="Select a blueprint to import or attach commands.";
    private int offset=0,panel=0,x=0;
    private LibraryViewport view;
    private boolean loading=false,pathEditor=false;

    public BlueprintLibraryScreen(BuildAIScreen parent){
        super(Text.literal("BuildAI | My Blueprints"));this.parent=parent;
    }
    private void refresh(){
        try{
            saved=BlueprintLibrary.files();
            offset=Math.min(offset,Math.max(0,saved.size()-view.visibleItems()));
            if(selected!=null&&!saved.contains(selected))selected=null;
        }catch(Exception ex){
            saved=List.of();
            status="Cannot read saved blueprints: "+ex.getMessage();
        }
    }
    private ButtonWidget control(String label,int px,int py,int w,int h,Runnable callback){
        ButtonWidget b=ButtonWidget.builder(
            Text.literal(textRenderer.trimToWidth(label,Math.max(20,w-14))),
            button->callback.run()).dimensions(px,py,Math.max(27,w),Math.max(15,h)).build();
        addDrawableChild(b);
        return b;
    }
    private void rebuild(){
        if(pathField!=null)enteredPath=pathField.getText();
        clearChildren();
        init();
    }
    private static String label(Path file){
        String name=file.getFileName().toString();
        name=name.replaceFirst("(?i)\\.(schem|schematic|litematic|nbt)$","");
        name=name.replaceFirst("-20[0-9]{6}-[0-9]{6}-[0-9]+$","");
        return name.replace('_',' ').replace('-',' ');
    }
    private int listX(){return x+7;}
    private int listWidth(){return panel-14;}
    private int maxOffset(){return Math.max(0,saved.size()-view.visibleItems());}
    private int countVisible(){return Math.min(view.visibleItems(),Math.max(0,saved.size()-offset));}
    private void showPathEditor(){
        pathEditor=true;status="Paste a schematic file path; the saved library is unchanged.";
        rebuild();
    }
    private void showList(){
        pathEditor=false;status="Choose a blueprint and an action below.";
        rebuild();
    }
    @Override protected void init(){
        if(pathField!=null)enteredPath=pathField.getText();
        pathField=null;
        panel=Math.min(750,Math.max(120,width-18));
        x=(width-panel)/2;
        view=LibraryViewport.fit(width,height);
        refresh();

        // Even at full-screen GUI Scale 4 the header and footer adapt and
        // the scaled viewport still has usable paging controls.
        int gap=4,third=(panel-2*gap)/3,half=(panel-gap)/2;
        int nav=view.navY(),act=view.actionY(),util=view.utilityY();
        final int buttonHeight=19;

        if(pathEditor){
            int fieldY=view.listTop()+13;
            pathField=new TextFieldWidget(textRenderer,x+7,fieldY,panel-14,19,
                Text.literal("Optional schematic path"));
            pathField.setMaxLength(1024);
            pathField.setText(enteredPath);
            pathField.setPlaceholder(Text.literal("Paste .schem / .litematic / .nbt path"));
            addDrawableChild(pathField);

            control("LOAD ENTERED PATH",x,nav,panel,buttonHeight,()->{
                enteredPath=pathField.getText();
                try{load(BlueprintLibrary.resolveUserPath(enteredPath));}
                catch(Exception ex){status="Invalid path: "+ex.getMessage();}
            }).active=!loading;
            control("RETURN TO BLUEPRINT LIST",x,act,panel,buttonHeight,this::showList);
            control("BACK TO BUILDAI",x,util,panel,buttonHeight,this::close);
            return;
        }

        for(int i=0;i<countVisible();i++){
            int index=offset+i;
            Path file=saved.get(index);
            boolean isSelected=file.equals(selected);
            String display=(isSelected?"[SELECTED] ":"")+
                String.format(Locale.ROOT,"%02d  ",index+1)+label(file);
            control(display,listX(),view.listTop()+i*view.rowStep(),
                listWidth(),view.rowHeight(),()->{
                    selected=file;
                    status="Selected: "+label(file);
                    rebuild();
                }).active=!loading;
        }

        control("PREV",x,nav,third,buttonHeight,()->{
            offset=Math.max(0,offset-view.visibleItems());
            rebuild();
        }).active=!loading&&offset>0;
        control("NEXT",x+third+gap,nav,third,buttonHeight,()->{
            offset=Math.min(maxOffset(),offset+view.visibleItems());
            rebuild();
        }).active=!loading&&offset+view.visibleItems()<saved.size();
        control("REFRESH",x+2*(third+gap),nav,panel-2*(third+gap),
            buttonHeight,()->{
                selected=null;status="Library refreshed.";
                rebuild();
            }).active=!loading;

        control("IMPORT",x,act,third,buttonHeight,()->{
            if(selected!=null)load(selected);
        }).active=selected!=null&&!loading;

        control("REMAKE / REVISE...",x+third+gap,act,third,buttonHeight,()->{
            if(selected!=null)openRemix(selected);
        }).active=selected!=null&&!loading;

        control("ATTACH SCRIPTS",x+2*(third+gap),act,
            panel-2*(third+gap),buttonHeight,()->{
                if(selected==null){
                    status="Select the saved schematic for your existing structure.";
                    return;
                }
                if(client==null||client.getNetworkHandler()==null){
                    status="Join your world first.";return;
                }
                if(!selected.getFileName().toString().endsWith(".schem")){
                    status="Command-plan recovery needs a saved BuildAI .schem file.";return;
                }
                // Server uniquely matches every scripted command block; no
                // build paste occurs. Return to game so the queued in-game
                // commissioning screen can appear and errors are visible.
                client.getNetworkHandler().sendChatCommand(
                    "buildai commands recover "+selected.getFileName());
                client.setScreen(null);
            }).active=selected!=null&&!loading;

        control("IMPORT OTHER FILE",x,util,third,buttonHeight,this::showPathEditor);
        control("DELETE SELECTED...",x+third+gap,util,third,buttonHeight,
            this::confirmDeleteSelected).active=selected!=null&&!loading&&
                BlueprintLibrary.isLocalSavedPath(selected);
        control("BACK TO BUILDAI",x+2*(third+gap),util,
            panel-2*(third+gap),buttonHeight,this::close);
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
        if(!pathEditor&&mx>=listX()&&mx<listX()+listWidth()&&
            my>=view.listTop()&&my<view.statusY()-5){
            int step=Math.max(1,(int)Math.round(Math.abs(vertical)*2));
            int newOffset=Math.max(0,Math.min(maxOffset(),
                offset+(vertical>0?-step:step)));
            if(newOffset!=offset){offset=newOffset;rebuild();}
            return true;
        }
        return super.mouseScrolled(mx,my,horizontal,vertical);
    }
    private void confirmDeleteSelected(){
        if(loading||selected==null||!BlueprintLibrary.isLocalSavedPath(selected)){
            status="Choose a saved BuildAI blueprint first. External imports cannot be deleted here.";
            return;
        }
        final Path deleteTarget=selected;
        if(client!=null)client.setScreen(new BlueprintDeleteConfirmScreen(
            this,deleteTarget,result->{
                selected=null;
                status=result.warning().isBlank()
                    ?"Permanently deleted from Minecraft saves: "+
                        result.deleted().getFileName()
                    :result.warning();
            }));
    }
    private void openRemix(Path file){
        if(loading){status="Wait until the previous import completes.";return;}
        loading=true;
        status="Reading saved blueprint and original prompt...";
        CompletableFuture.supplyAsync(()->{
            try{
                Blueprint b=SchematicReader.read(file);
                BlockStates.validate(b);
                return b;
            }catch(Exception ex){throw new RuntimeException(ex);}
        }).whenComplete((source,error)->MinecraftClient.getInstance().execute(()->{
            loading=false;
            if(error!=null){
                Throwable cause=error;
                while(cause.getCause()!=null)cause=cause.getCause();
                status="Cannot open for revision: "+cause.getMessage();
                return;
            }
            // Only selected source geometry is passed; the current draft and
            // all earlier saved schematics remain untouched.
            if(client!=null)client.setScreen(new BuildAIRemixScreen(this,parent,source));
        }));
    }
    private void load(Path file){
        if(loading){status="Import already running.";return;}
        loading=true;
        status="Importing "+file.getFileName()+"...";
        CompletableFuture.supplyAsync(()->{
            try{
                Blueprint b=SchematicReader.read(file);
                BlockStates.validate(b);
                return b;
            }catch(Exception ex){throw new RuntimeException(ex);}
        }).whenComplete((b,error)->MinecraftClient.getInstance().execute(()->{
            loading=false;
            if(error!=null){
                Throwable cause=error;
                while(cause.getCause()!=null)cause=cause.getCause();
                status="Import failed: "+cause.getMessage();
                return;
            }
            parent.importedFromLibrary(b);
        }));
    }
    private void drawLine(DrawContext ctx,String value,int px,int py,int available,int color){
        ctx.drawTextWithShadow(textRenderer,
            textRenderer.trimToWidth(value,Math.max(12,available)),px,py,color);
    }
    @Override public void renderBackground(DrawContext ctx,int mx,int my,float delta){
        ctx.fill(x-7,4,x+panel+7,height-3,0xF0152938);
        ctx.fill(x-7,4,x+panel+7,7,MINT);
        if(!pathEditor){
            int top=view.listTop()-3,bottom=view.statusY()-6;
            if(bottom>top)
                ctx.fill(x+2,top,x+panel-2,bottom,0x55345061);
            for(int i=0;i<countVisible();i++){
                int y=view.listTop()+i*view.rowStep();
                boolean isSelected=saved.get(offset+i).equals(selected);
                ctx.fill(x+3,y,x+6,y+view.rowHeight(),isSelected?MINT:0xFF4C788C);
            }
        }
    }
    @Override public void render(DrawContext ctx,int mx,int my,float delta){
        super.render(ctx,mx,my,delta);
        drawLine(ctx,"BUILDAI / MY BLUEPRINTS",x+7,9,panel-14,MINT);
        String range=saved.isEmpty()?"NO FILES":
            (offset+1)+"-"+Math.min(saved.size(),offset+view.visibleItems());
        if(view.listTop()>=30)
            drawLine(ctx,saved.size()+" SAVED  |  "+range+"  |  "+
                (pathEditor?"IMPORT OTHER FILE":"CLICK A FILE TO SELECT"),
                x+7,22,panel-14,BLUE);

        if(!view.compact()){
            drawLine(ctx,"Select a saved blueprint to import, revise, attach commands or permanently delete.",
                x+7,44,panel-14,DIM);
        }else if(pathEditor){
            drawLine(ctx,"Optional full .schem / .nbt file path:",
                x+7,view.listTop(),panel-14,DIM);
        }
        if(saved.isEmpty()&&!pathEditor)
            drawLine(ctx,"No saved projects. Import another file below.",
                listX()+3,view.listTop()+5,listWidth()-6,DIM);

        int color=status.toLowerCase(Locale.ROOT).contains("fail")||
            status.toLowerCase(Locale.ROOT).contains("invalid")?RED:GOLD;
        drawLine(ctx,status,x+7,view.statusY(),panel-14,color);
    }
    @Override public void close(){
        if(client!=null)client.setScreen(parent);
    }
    @Override public boolean shouldPause(){return false;}
}
