package com.hanco.buildai.client;

import com.hanco.buildai.blueprint.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import javax.swing.*;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Responsive, paged Minecraft UI. Keep total height bounded by the current
 * SCALED window height (not the physical monitor resolution or GUI scale).
 */
public final class BuildAIScreen extends Screen {
    private TextFieldWidget prompt,key;
    private String promptValue="", keyValue="";
    private int tab=0, surfaceLayer=BuildAIClient.surfaceLayer;
    private String status="Create a design, import a schematic, or load the offline demo.";
    private boolean busy=false, showApiKey=false;
    private int left,panelWidth,top,lineHeight;
    public void updateStatus(String message){status=message;}

    public BuildAIScreen(){super(Text.literal("BuildAI — Minecraft Architect"));}
    private Text text(String s){return Text.literal(s);}
    private void saveFields(){
        if(prompt!=null)promptValue=prompt.getText();
        if(key!=null)keyValue=key.getText();
    }
    private void switchTab(int index){
        if(busy){status="Wait for the current operation to finish.";return;}
        saveFields();tab=index;
        clearChildren();
        init();
    }
    private int row(int number){return top+number*lineHeight;}
    private ButtonWidget add(String title,int x,int y,int w,int h,java.util.function.Consumer<ButtonWidget> action){
        ButtonWidget widget=ButtonWidget.builder(text(title),action::accept)
            .dimensions(x,y,Math.max(28,w),Math.max(12,h)).build();
        addDrawableChild(widget);
        return widget;
    }
    @Override protected void init(){
        if(prompt!=null||key!=null)saveFields();
        panelWidth=Math.min(510,Math.max(140,width-16));
        left=(width-panelWidth)/2;
        // Reserve explicit space for the status footer BELOW the Close button.
        // All geometry uses Minecraft's scaled GUI dimensions, not monitor pixels.
        lineHeight=Math.min(23,Math.max(9,(height-12)/14));
        top=Math.max(2,(height-14*lineHeight)/2);
        int buttonHeight=Math.max(9,lineHeight-2);
        int gap=4;
        int third=(panelWidth-gap*2)/3;
        int half=(panelWidth-gap)/2;

        add("Create / AI",left,row(1),third,buttonHeight,b->switchTab(0));
        add("Placement / Export",left+third+gap,row(1),third,buttonHeight,b->switchTab(1));
        add("How To",left+2*(third+gap),row(1),panelWidth-2*(third+gap),buttonHeight,
            b->{saveFields();client.setScreen(new BuildAIHelpScreen(this));});

        if(tab==0){
            prompt=new TextFieldWidget(textRenderer,left,row(3),panelWidth,buttonHeight,text("Building description"));
            prompt.setMaxLength(4000);prompt.setText(promptValue);
            prompt.setPlaceholder(text("Describe a castle, house, factory, or a requested revision"));
            addDrawableChild(prompt);
            add("Account / Login Help",left,row(5),half,buttonHeight,b->{
                saveFields();
                if(client!=null)client.setScreen(new BuildAIHelpScreen(this,1));
            });
            add("API key option",left+half+gap,row(5),half,buttonHeight,b->{
                saveFields();showApiKey=!showApiKey;switchTab(0);
                status=showApiKey?"API key field opened (separate OpenAI billing).":"API key field hidden.";
            });
            if(showApiKey){
                key=new TextFieldWidget(textRenderer,left,row(7),panelWidth,buttonHeight,text("OpenAI developer API key"));
                key.setMaxLength(500);key.setText(keyValue.isBlank()?BuildAIClient.getKey():keyValue);
                addDrawableChild(key);
            }
            add("Generate New",left,row(8),half,buttonHeight,b->requestAI(false));
            add("Revise Existing",left+half+gap,row(8),half,buttonHeight,b->requestAI(true));
            add("Import File",left,row(9),half,buttonHeight,b->browse());
            add("Load Demo Cottage",left+half+gap,row(9),half,buttonHeight,b->{
                if(busy){status="Wait for the current operation to finish.";return;}
                Blueprint demo=DemoBlueprint.cottage();
                BuildAIClient.acceptImport(demo);surfaceLayer=0;
                status="Demo cottage ready ("+demo.solidCount()+" blocks). Open Placement / Export.";
            });
            add("Chat Mode: "+(BuildAIClient.chatMode?"ON":"OFF"),left,row(10),half,buttonHeight,b->{
                if(key!=null)BuildAIClient.setKey(key.getText());
                if(!BuildAIClient.chatMode&&BuildAIClient.getKey().isBlank()){
                    status="AI Chat Mode needs an OpenAI API key. Use Account / Login Help for details.";
                    return;
                }
                BuildAIClient.chatMode=!BuildAIClient.chatMode;
                b.setMessage(text("Chat Mode: "+(BuildAIClient.chatMode?"ON":"OFF")));
                status=BuildAIClient.chatMode?"Private building chat enabled. Type exit to stop.":"Chat Mode disabled.";
            });
            add("Hide Preview",left+half+gap,row(10),half,buttonHeight,b->{
                BuildAIClient.clearPreview();
                status="Ghost grid hidden; placed blocks remain untouched.";
            });
        }else{
            add("Surface Y -",left,row(4),half,buttonHeight,b->{
                if(BuildAIClient.draft==null){status="Load a blueprint first.";return;}
                surfaceLayer=Math.max(0,surfaceLayer-1);
                BuildAIClient.surfaceLayer=surfaceLayer;
                status="Surface reference layer: "+surfaceLayer;
            });
            add("Surface Y +",left+half+gap,row(4),half,buttonHeight,b->{
                if(BuildAIClient.draft==null){status="Load a blueprint first.";return;}
                surfaceLayer=Math.min(BuildAIClient.draft.height-1,surfaceLayer+1);
                BuildAIClient.surfaceLayer=surfaceLayer;
                status="Surface reference layer: "+surfaceLayer;
            });
            add("Upload & Preview",left,row(6),panelWidth,buttonHeight,b->{
                if(busy){status="Wait for the current operation to finish.";return;}
                if(BuildAIClient.draft==null){status="Create or import a blueprint first.";return;}
                try{
                    BuildAIClient.beginUpload(BuildAIClient.draft,surfaceLayer);
                    status="Sending blueprint to server; wait for acceptance, then close F8 for placement.";
                }catch(Exception ex){status="Upload failed: "+ex.getMessage();}
            });
            add("Resume Flight Camera",left,row(7),half,buttonHeight,b->{
                if(BuildAIClient.draft==null||(!BuildAIClient.selecting&&!BuildAIClient.previewLocked)){
                    status="Upload and get server approval first.";return;
                }
                close();
                PlacementMode.start();
            });
            add("Lock XYZ (Enter)",left+half+gap,row(7),half,buttonHeight,b->{
                if(!PlacementMode.active()){
                    status="First click Resume Flight Camera, then press Enter while in the world.";return;
                }
                PlacementMode.lock();
            });
            add("Export Blueprint as .schem",left,row(8),panelWidth,buttonHeight,b->exportSchematic());
            add("Hide Preview",left,row(9),half,buttonHeight,b->{
                BuildAIClient.clearPreview();
                status="Ghost grid hidden. Existing blocks are unchanged.";
            });
            add("Cancel Project",left+half+gap,row(9),half,buttonHeight,b->{
                if(client==null||client.getNetworkHandler()==null){
                    status="Join a Minecraft world first.";return;
                }
                client.getNetworkHandler().sendChatCommand("buildai cancel");
                status="Cancellation requested. Already placed blocks remain; see chat for result.";
            });
            add("Retry Placement",left,row(10),half,buttonHeight,b->{
                if(BuildAIClient.draft==null){status="Load a blueprint first.";return;}
                PlacementMode.retry();
                status="Retry requested: no excavation will begin.";
            });
            add("Return to Player",left+half+gap,row(10),half,buttonHeight,b->{
                PlacementMode.stop();
                status="Returned to your real character. Preview remains until hidden or canceled.";
            });
        }
        add("Close",left,row(11),panelWidth,buttonHeight,b->close());
    }
    private void requestAI(boolean revise){
        if(busy){status="Wait for the current operation to finish.";return;}
        saveFields();
        String description=promptValue.trim();
        if(description.isBlank()){status="Enter your building description.";return;}
        String previous=BuildAIClient.designSpecification;
        if(revise&&(previous==null||previous.isBlank())){
            status="Generate a new design before requesting revisions.";return;
        }
        if(key!=null)BuildAIClient.setKey(key.getText());
        if(BuildAIClient.getKey().isBlank()){
            status="Open API key option to connect OpenAI; ChatGPT account login is not implemented.";
            return;
        }
        busy=true;status=revise?"Revising blueprint...":"Generating blueprint...";
        CompletableFuture.supplyAsync(()->{
            try{
                return revise
                    ? AIArchitect.reviseDesign(BuildAIClient.getKey(),description,previous)
                    : AIArchitect.generateDesign(BuildAIClient.getKey(),description);
            }catch(Exception ex){throw new RuntimeException(ex);}
        }).whenComplete((result,error)->MinecraftClient.getInstance().execute(()->{
            busy=false;
            if(error!=null){status="Generation failed: "+message(error);return;}
            BuildAIClient.acceptGenerated(result);
            surfaceLayer=BuildAIClient.surfaceLayer;
            status=(revise?"Revised: ":"Created: ")+result.blueprint().name+
                " ("+result.blueprint().solidCount()+" blocks). Open Placement / Export.";
        }));
    }
    private static String message(Throwable throwable){
        Throwable t=throwable;
        while(t.getCause()!=null)t=t.getCause();
        return t.getMessage()==null?t.toString():t.getMessage();
    }
    /**
     * Swing file pickers must be created and shown on Swing's event thread,
     * while Minecraft rendering and file parsing remain asynchronous.
     */
    private static <T> T onSwing(java.util.concurrent.Callable<T> action) throws Exception {
        if(SwingUtilities.isEventDispatchThread())return action.call();
        java.util.concurrent.atomic.AtomicReference<T> result=new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Exception> error=new java.util.concurrent.atomic.AtomicReference<>();
        SwingUtilities.invokeAndWait(()->{
            try{result.set(action.call());}
            catch(Exception e){error.set(e);}
        });
        if(error.get()!=null)throw error.get();
        return result.get();
    }
    private void browse(){
        if(busy){status="Wait for the current operation to finish.";return;}
        busy=true;status="Choose a schematic...";
        CompletableFuture.supplyAsync(()->{
            try{
                java.io.File chosen=onSwing(()->{
                    JFileChooser chooser=new JFileChooser();
                    chooser.setDialogTitle("Import Minecraft schematic");
                    chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                        "Minecraft schematics","schem","schematic","litematic","nbt"));
                    return chooser.showOpenDialog(null)==JFileChooser.APPROVE_OPTION
                        ?chooser.getSelectedFile():null;
                });
                return chosen==null?null:SchematicReader.read(chosen.toPath());
            }catch(Exception e){throw new RuntimeException(e);}
        }).whenComplete((loaded,error)->MinecraftClient.getInstance().execute(()->{
            busy=false;
            if(error!=null){status="Import failed: "+message(error);return;}
            if(loaded==null){status="Import canceled.";return;}
            try{
                BlockStates.validate(loaded);
                BuildAIClient.acceptImport(loaded);surfaceLayer=0;
                status="Imported "+loaded.name+" ("+loaded.solidCount()+" blocks). Open Placement / Export.";
            }catch(Exception e){status="Import rejected: "+e.getMessage();}
        }));
    }
    private void exportSchematic(){
        if(busy){status="Wait for the current operation to finish.";return;}
        if(BuildAIClient.draft==null){status="No active blueprint to export.";return;}
        Blueprint bp=BuildAIClient.draft;
        busy=true;status="Choose .schem destination...";
        CompletableFuture.supplyAsync(()->{
            try{
                java.io.File chosen=onSwing(()->{
                    JFileChooser chooser=new JFileChooser();
                    chooser.setDialogTitle("Export BuildAI schematic");
                    chooser.setSelectedFile(new java.io.File(bp.name.replaceAll("[^a-zA-Z0-9_-]","_")+".schem"));
                    return chooser.showSaveDialog(null)==JFileChooser.APPROVE_OPTION
                        ?chooser.getSelectedFile():null;
                });
                if(chosen==null)return null;
                String path=chosen.getAbsolutePath();
                if(!path.toLowerCase(Locale.ROOT).endsWith(".schem"))path+=".schem";
                java.nio.file.Path output=java.nio.file.Path.of(path);
                if(java.nio.file.Files.exists(output))
                    throw new java.io.IOException("A file with that name already exists; choose a new filename.");
                return SchematicWriter.write(bp,output);
            }catch(Exception e){throw new RuntimeException(e);}
        }).whenComplete((saved,error)->MinecraftClient.getInstance().execute(()->{
            busy=false;
            status=error!=null?"Export failed: "+message(error):saved==null?"Export canceled.":"Saved "+saved.getFileName();
        }));
    }
    @Override public void render(DrawContext ctx,int mx,int my,float delta){
        super.render(ctx,mx,my,delta);
        int x=left;
        ctx.drawCenteredTextWithShadow(textRenderer,"BuildAI | 1.21.1 | Architect",width/2,row(0),0x66ECC4);
        if(tab==0){
            ctx.drawTextWithShadow(textRenderer,"Describe your build or a revision",x,row(2),0xF1F6FA);
            String account="ChatGPT sign-in: pending | Use API key or offline demo";
            ctx.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(account,panelWidth),x,row(4),0xA4C7DA);
            if(showApiKey)ctx.drawTextWithShadow(textRenderer,"Optional developer API key (separate billing)",x,row(6),0xBFC6D2);
        }else{
            Blueprint bp=BuildAIClient.draft;
            String label=bp==null?"No blueprint loaded. Open Create / AI.":bp.name+
                " | "+bp.width+" × "+bp.height+" × "+bp.length+" | "+bp.solidCount()+" blocks";
            ctx.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(label,panelWidth),x,row(2),0xD8E8F2);
            String level="Blueprint surface layer: "+surfaceLayer+(bp==null?"":" / "+(bp.height-1));
            ctx.drawTextWithShadow(textRenderer,level,x,row(3),0xCDD3DC);
        }
        var messages=textRenderer.wrapLines(text(status),panelWidth);
        for(int line=0;line<Math.min(2,messages.size());line++)
            ctx.drawTextWithShadow(textRenderer,messages.get(line),
                x,Math.min(height-10,row(12+line)),0x79EBBD);
    }
    @Override public void close(){
        if(client!=null){
            client.setScreen(null);
            if(BuildAIClient.selecting&&!PlacementMode.active()&&BuildAIClient.previewOrigin==null)
                PlacementMode.start();
        }
    }
    @Override public boolean shouldPause(){return false;}
}
