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

/** Minecraft-native file selector: works when Java AWT is headless in runClient. */
public final class BlueprintLibraryScreen extends Screen {
    private final BuildAIScreen parent;
    private List<Path> saved=List.of();
    private TextFieldWidget pathField;
    private String enteredPath="",status="Choose a saved build, or paste an absolute schematic path.";
    private int offset=0, visible=1;
    private boolean loading=false;

    public BlueprintLibraryScreen(BuildAIScreen parent){
        super(Text.literal("BuildAI | My Blueprints"));
        this.parent=parent;
    }
    private void refresh(){
        try{
            saved=BlueprintLibrary.files();
            offset=Math.min(offset,Math.max(0,saved.size()-visible));
            status=saved.isEmpty()?"No saved schematics yet. Generate a build, or paste an existing file path.":
                saved.size()+" saved blueprint(s). Select one to import.";
        }catch(Exception e){saved=List.of();status="Cannot read saved blueprints: "+e.getMessage();}
    }
    @Override protected void init(){
        int panel=Math.min(530,Math.max(150,width-16)),x=(width-panel)/2;
        int available=Math.max(22,height-150);
        visible=Math.max(1,Math.min(9,available/24));
        refresh();
        for(int i=0;i<visible;i++){
            int index=offset+i;
            if(index>=saved.size())break;
            Path file=saved.get(index);
            String filename=file.getFileName().toString();
            addDrawableChild(ButtonWidget.builder(
                Text.literal(textRenderer.trimToWidth(filename,panel-18)),
                b->load(file)).dimensions(x,48+i*24,panel,20).build());
        }
        int controlsY=Math.max(56,height-99),gap=4,third=(panel-gap*2)/3;
        addDrawableChild(ButtonWidget.builder(Text.literal("Previous"),b->{
            offset=Math.max(0,offset-visible);rebuild();
        }).dimensions(x,controlsY,third,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Next"),b->{
            offset=Math.min(Math.max(0,saved.size()-visible),offset+visible);rebuild();
        }).dimensions(x+third+gap,controlsY,third,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Refresh"),b->rebuild())
            .dimensions(x+2*(third+gap),controlsY,panel-2*(third+gap),20).build());
        pathField=new TextFieldWidget(textRenderer,x,controlsY+24,panel,18,Text.literal("Schematic path"));
        pathField.setMaxLength(1024);pathField.setText(enteredPath);
        pathField.setPlaceholder(Text.literal("Paste a .schem/.litematic/.nbt file path, or a saved filename"));
        addDrawableChild(pathField);
        int half=(panel-gap)/2,footerY=controlsY+46;
        addDrawableChild(ButtonWidget.builder(Text.literal("Load Entered Path"),b->{
            enteredPath=pathField.getText();
            try{load(BlueprintLibrary.resolveUserPath(enteredPath));}
            catch(Exception ex){status="Invalid file: "+ex.getMessage();}
        }).dimensions(x,footerY,half,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Back to BuildAI"),b->close())
            .dimensions(x+half+gap,footerY,half,20).build());
    }
    private void rebuild(){
        if(pathField!=null)enteredPath=pathField.getText();
        clearChildren();init();
    }
    private void load(Path file){
        if(loading){status="Already importing a file.";return;}
        loading=true;
        status="Reading "+file.getFileName()+"...";
        CompletableFuture.supplyAsync(()->{
            try{
                Blueprint imported=SchematicReader.read(file);
                BlockStates.validate(imported);
                return imported;
            }catch(Exception ex){throw new RuntimeException(ex);}
        }).whenComplete((blueprint,error)->MinecraftClient.getInstance().execute(()->{
            loading=false;
            if(error!=null){
                Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();
                status="Import failed: "+cause.getMessage();
                return;
            }
            parent.importedFromLibrary(blueprint);
        }));
    }
    @Override public void renderBackground(DrawContext ctx,int mx,int my,float delta){
        // Draw an opaque custom panel instead of invoking Minecraft's blurred background.
    }
    @Override public void render(DrawContext ctx,int mx,int my,float delta){
        int panel=Math.min(530,Math.max(150,width-16)),x=(width-panel)/2;
        ctx.fill(x-5,6,x+panel+5,height-5,0xEE16242E);
        ctx.drawCenteredTextWithShadow(textRenderer,"BuildAI | My Blueprints",width/2,12,0x6AF0CB);
        ctx.drawCenteredTextWithShadow(textRenderer,"Saved files: game directory / buildai / blueprints",
            width/2,28,0xB3D1DC);
        int y=Math.max(44,height-121);
        ctx.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(status,panel-8),x+4,y,0xE3D7A4);
        super.render(ctx,mx,my,delta);
    }
    @Override public void close(){if(client!=null)client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
