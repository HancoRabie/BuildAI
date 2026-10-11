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
        int gap=5,w=(content-gap)/2,third=(content-gap*2)/3;
        addDrawableChild(ButtonWidget.builder(Text.literal("Generate New"),b->requestAI(false))
            .dimensions(x,top+111,third,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Revise Existing"),b->requestAI(true))
            .dimensions(x+third+gap,top+111,third,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Import File"),b->browse())
            .dimensions(x+2*(third+gap),top+111,content-2*(third+gap),20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Surface Y -"),b->surfaceLayer=Math.max(0,surfaceLayer-1))
            .dimensions(x,top+139,w,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Surface Y +"),b->{
            if(BuildAIClient.draft!=null)surfaceLayer=Math.min(BuildAIClient.draft.height-1,surfaceLayer+1);
        }).dimensions(x+w+gap,top+139,w,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Upload Blueprint"),b->{
            if(BuildAIClient.draft==null){status="Generate or import a blueprint first";return;}
            try {
                ClientUpload.send(BuildAIClient.draft,surfaceLayer);
                BuildAIClient.surfaceLayer=surfaceLayer;
                BuildAIClient.previewLocked=false;
                BuildAIClient.surfacePending=false;
                BuildAIClient.previewOrigin=null;
                BuildAIClient.selecting=true;
                status="Uploaded. Close GUI; left-click horizontal placement point.";
            }catch(Exception ex){status="Upload failed: "+ex.getMessage();}
        }).dimensions(x,top+168,content,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Load Demo Cottage"),b->{
            Blueprint sample=DemoBlueprint.cottage();
            BuildAIClient.acceptImport(sample);
            surfaceLayer=0;
            status="Demo cottage loaded ("+sample.solidCount()+" blocks). Upload or export; no AI key needed.";
        }).dimensions(x,top+197,w,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Chat Mode: "+(BuildAIClient.chatMode?"ON":"OFF")),b->{
            BuildAIClient.setKey(key.getText());
            BuildAIClient.chatMode=!BuildAIClient.chatMode;
            b.setMessage(Text.literal("Chat Mode: "+(BuildAIClient.chatMode?"ON":"OFF")));
            status="Chat Mode "+(BuildAIClient.chatMode?"enabled - chat now goes privately to AI. Type exit to leave.":"disabled.");
        }).dimensions(x+w+gap,top+197,w,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Export Blueprint as .schem"),b->exportSchematic())
            .dimensions(x,top+224,content,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("How To"),b->{
            if(client!=null)client.setScreen(new BuildAIHelpScreen(this));
        }).dimensions(x,top+251,w,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"),b->close())
            .dimensions(x+w+gap,top+251,w,20).build());
    }
    private void requestAI(boolean revise) {
        if(busy)return;
        String description=prompt.getText().trim();
        if(description.isBlank()){status="Describe the building or requested change first.";return;}
        String previous=BuildAIClient.designSpecification;
        if(revise&&(previous==null||previous.isBlank())){
            status="Generate a new AI blueprint first, then revise it.";return;
        }
        final String currentKey=key.getText();
        BuildAIClient.setKey(currentKey);
        busy=true;
        status=revise?"Revising existing blueprint...":"Generating new blueprint...";
        CompletableFuture.supplyAsync(()->{
            try {
                return revise
                    ? AIArchitect.reviseDesign(currentKey,description,previous)
                    : AIArchitect.generateDesign(currentKey,description);
            }catch(Exception ex){throw new RuntimeException(ex);}
        }).whenComplete((result,error)->MinecraftClient.getInstance().execute(()->{
            busy=false;
            if(error!=null){status="AI error: "+error.getCause();return;}
            BuildAIClient.acceptGenerated(result);
            surfaceLayer=Math.min(surfaceLayer,result.blueprint().height-1);
            status=(revise?"Revised: ":"Generated: ")+result.blueprint().name+
                " | "+result.blueprint().solidCount()+" blocks. Review before placement.";
        }));
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
                BuildAIClient.acceptImport(loaded);surfaceLayer=0;
                status="Imported "+loaded.name+" | "+loaded.solidCount()+" blocks | "+loaded.warnings.size()+" notes";
            }catch(Exception ex){status="Invalid block states: "+ex.getMessage();}
        }));
    }
    private void exportSchematic(){
        if(BuildAIClient.draft==null){status="Generate or import a blueprint first.";return;}
        Blueprint blueprint=BuildAIClient.draft;
        busy=true;status="Select .schem export location...";
        CompletableFuture.supplyAsync(()->{
            try {
                JFileChooser chooser=new JFileChooser();
                chooser.setDialogTitle("Save BuildAI schematic");
                chooser.setSelectedFile(new java.io.File(blueprint.name.replaceAll("[^a-zA-Z0-9_-]","_")+".schem"));
                if(chooser.showSaveDialog(null)!=JFileChooser.APPROVE_OPTION)return null;
                java.io.File selected=chooser.getSelectedFile();
                String filename=selected.getAbsolutePath();
                if(!filename.toLowerCase(java.util.Locale.ROOT).endsWith(".schem"))filename+=".schem";
                return SchematicWriter.write(blueprint,java.nio.file.Path.of(filename));
            }catch(Exception e){throw new RuntimeException(e);}
        }).whenComplete((saved,error)->MinecraftClient.getInstance().execute(()->{
            busy=false;
            if(error!=null){status="Export failed: "+error.getCause();return;}
            status=saved==null?"Export canceled":"Saved "+saved.getFileName();
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
        context.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(status,content),x,top+278,0xA5F4CF);
    }
    @Override public void close(){if(client!=null)client.setScreen(null);}
    @Override public boolean shouldPause(){return false;}
}
