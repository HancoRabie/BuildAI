package com.hanco.buildai.client;

import com.hanco.buildai.blueprint.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import java.nio.file.Path;
import javax.swing.*;
import java.util.concurrent.CompletableFuture;

public final class BuildAIScreen extends Screen {
    private TextFieldWidget prompt,key;
    private int surfaceLayer=0;
    private String status="Describe a building, or import a schematic.";
    private boolean busy=false;

    public BuildAIScreen(){super(Text.literal("BuildAI - Architect & Importer"));}
    @Override protected void init(){
        int content=Math.min(470,width-28),x=(width-content)/2,top=Math.max(35,height/2-135);
        prompt=new TextFieldWidget(textRenderer,x,top+32,content,20,Text.literal("Describe your building"));
        prompt.setMaxLength(4000);
        prompt.setPlaceholder(Text.literal("Example: ornate white mansion with 4 columns, stairs, glass greenhouse"));
        addDrawableChild(prompt);
        key=new TextFieldWidget(textRenderer,x,top+80,content,20,Text.literal("OpenAI API key"));
        key.setMaxLength(500);
        key.setText(BuildAIClient.getKey());addDrawableChild(key);
        int gap=5,w=(content-gap)/2;
        addDrawableChild(ButtonWidget.builder(Text.literal("Generate with AI"),b->{
            if(busy)return;
            BuildAIClient.setKey(key.getText());
            if(prompt.getText().isBlank()){status="Enter a building description first.";return;}
            busy=true;status="Generating, please wait...";
            CompletableFuture.supplyAsync(()->{
                try{return AIArchitect.generate(BuildAIClient.getKey(),prompt.getText());}
                catch(Exception ex){throw new RuntimeException(ex);}
            }).whenComplete((result,error)->MinecraftClient.getInstance().execute(()->{
                busy=false;
                if(error!=null){status="AI error: "+error.getCause();return;}
                BuildAIClient.draft=result;surfaceLayer=0;
                status="Generated "+result.name+" | "+result.solidCount()+" blocks";
            }));
        }).dimensions(x,top+111,w,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Import File"),b->browse())
            .dimensions(x+w+gap,top+111,w,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Surface Y -"),b->surfaceLayer=Math.max(0,surfaceLayer-1))
            .dimensions(x,top+139,w,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Surface Y +"),b->{
            if(BuildAIClient.draft!=null)surfaceLayer=Math.min(BuildAIClient.draft.height-1,surfaceLayer+1);
        }).dimensions(x+w+gap,top+139,w,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Upload Blueprint"),b->{
            if(BuildAIClient.draft==null){status="Generate or import a blueprint first";return;}
            try {
                ClientUpload.send(BuildAIClient.draft,surfaceLayer);
                BuildAIClient.selecting=true;
                status="Uploaded. Close GUI; left-click horizontal placement point.";
            }catch(Exception ex){status="Upload failed: "+ex.getMessage();}
        }).dimensions(x,top+168,content,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Chat Mode: "+(BuildAIClient.chatMode?"ON":"OFF")),b->{
            BuildAIClient.setKey(key.getText());
            BuildAIClient.chatMode=!BuildAIClient.chatMode;
            b.setMessage(Text.literal("Chat Mode: "+(BuildAIClient.chatMode?"ON":"OFF")));
            status="Chat Mode "+(BuildAIClient.chatMode?"enabled - chat now goes privately to AI. Type exit to leave.":"disabled.");
        }).dimensions(x,top+197,content,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"),b->close()).dimensions(x,top+224,content,20).build());
    }
    private void browse(){
        if(busy)return;
        busy=true;status="Select a .schem, .schematic, .litematic or .nbt file...";
        CompletableFuture.supplyAsync(()->{
            try {
                JFileChooser chooser=new JFileChooser();
                chooser.setDialogTitle("Import Minecraft schematic");
                chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "Minecraft schematics","schem","schematic","litematic","nbt"));
                int result=chooser.showOpenDialog(null);
                if(result!=JFileChooser.APPROVE_OPTION)return null;
                return SchematicReader.read(chooser.getSelectedFile().toPath());
            }catch(Exception e){throw new RuntimeException(e);}
        }).whenComplete((loaded,error)->MinecraftClient.getInstance().execute(()->{
            busy=false;
            if(error!=null){status="Import failed: "+error.getCause();return;}
            if(loaded==null){status="Import canceled";return;}
            try {
                BlockStates.validate(loaded);
                BuildAIClient.draft=loaded;surfaceLayer=0;
                status="Imported "+loaded.name+" | "+loaded.solidCount()+" blocks | "+loaded.warnings.size()+" notes";
            }catch(Exception ex){status="Invalid block states: "+ex.getMessage();}
        }));
    }
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta){
        super.render(context,mouseX,mouseY,delta);
        int content=Math.min(470,width-28),x=(width-content)/2,top=Math.max(35,height/2-135);
        context.drawCenteredTextWithShadow(textRenderer,"BuildAI | 1.21.1 | Architect",width/2,top-1,0x65E5BD);
        context.drawTextWithShadow(textRenderer,"Building description",x,top+20,0xFFFFFF);
        context.drawTextWithShadow(textRenderer,"OpenAI API Key (temporary; not saved)",x,top+68,0xBBBBBB);
        String level="Blueprint surface layer: "+surfaceLayer;
        if(BuildAIClient.draft!=null)level+=" / "+(BuildAIClient.draft.height-1);
        context.drawTextWithShadow(textRenderer,level,x,top+129,0xCCCCCC);
        context.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(status,content),x,top+252,0xA5F4CF);
    }
    @Override public void close(){if(client!=null)client.setScreen(null);}
    @Override public boolean shouldPause(){return false;}
}
