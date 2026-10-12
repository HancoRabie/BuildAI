package com.hanco.buildai.client;

import com.hanco.buildai.blueprint.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
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
    /** Chat-generated designs open directly in the in-game blueprint review tab. */
    public static BuildAIScreen generatedResult(){
        BuildAIScreen s=new BuildAIScreen();
        s.tab=1;
        if(BuildAIClient.draft!=null)
            s.status="Blueprint ready: "+BuildAIClient.draft.name+
                ". Inspect its layers, then upload for 3D placement.";
        return s;
    }
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
        add("Feedback & Styles | Personal Building Library",
            left,row(2),panelWidth,buttonHeight,b->{
                saveFields();client.setScreen(new BuildAIFeedbackScreen(this));
            });

        if(tab==0){
            prompt=new TextFieldWidget(textRenderer,left,row(3),panelWidth,buttonHeight,text("Building description"));
            prompt.setMaxLength(4000);prompt.setText(promptValue);
            prompt.setPlaceholder(text("Describe a castle, house, factory, or a requested revision"));
            addDrawableChild(prompt);
            add(ChatGPTSignIn.connected()?"Reconnect ChatGPT":"Continue with ChatGPT",
                left,row(5),half,buttonHeight,b->connectChatGPT());
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
            add("Import File / My Builds",left,row(9),half,buttonHeight,b->browse());
            add("Load Demo Cottage",left+half+gap,row(9),half,buttonHeight,b->{
                if(busy){status="Wait for the current operation to finish.";return;}
                Blueprint demo=DemoBlueprint.cottage();
                BuildAIClient.acceptImport(demo);surfaceLayer=0;
                status="Demo cottage ready ("+demo.solidCount()+" blocks). Inspect it below.";
                switchTab(1);
            });
            add("Chat Mode: "+(BuildAIClient.chatMode?"ON":"OFF"),left,row(10),half,buttonHeight,b->{
                if(key!=null)BuildAIClient.setKey(key.getText());
                if(!BuildAIClient.chatMode&&!ChatGPTSignIn.connected()&&BuildAIClient.getKey().isBlank()){
                    status="Connect your ChatGPT account or enter an optional developer API key first.";
                    return;
                }
                BuildAIClient.chatMode=!BuildAIClient.chatMode;
                b.setMessage(text("Chat Mode: "+(BuildAIClient.chatMode?"ON":"OFF")));
                status=BuildAIClient.chatMode?"Private building chat enabled. Type exit to stop.":"Chat Mode disabled.";
            });
            add(ChatGPTSignIn.connected()?"Disconnect ChatGPT":"Login Help",
                left+half+gap,row(10),half,buttonHeight,b->{
                    if(ChatGPTSignIn.connected()){
                        ChatGPTSignIn.disconnect();
                        status="ChatGPT disconnected locally. Your saved blueprint remains.";
                        saveFields();switchTab(0);
                    }else{
                        saveFields();client.setScreen(new BuildAIHelpScreen(this,1));
                    }
                });
        }else{
            add("Inspect Blueprint Layers",left,row(5),half,buttonHeight,b->{
                if(BuildAIClient.draft==null){status="Generate or import a blueprint first.";return;}
                if(client!=null)client.setScreen(new BlueprintInspectorScreen(this,
                    BuildAIClient.draft,surfaceLayer));
            });
            add("Design QA / Warnings",left+half+gap,row(5),half,buttonHeight,b->{
                if(BuildAIClient.draft==null){status="Generate or import a blueprint first.";return;}
                if(client!=null)client.setScreen(new BuildAIQualityScreen(this,BuildAIClient.draft));
            });
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
                    status="Close F8: building starts IN FRONT. Wheel moves Z; arrows rotate; G selects ground Y; ENTER locks.";
                }catch(Exception ex){status="Upload failed: "+ex.getMessage();}
            });
            add("Preview in World (Close F8)",left,row(7),half,buttonHeight,b->{
                if(BuildAIClient.draft==null||(!BuildAIClient.selecting&&!BuildAIClient.previewLocked)){
                    status="First click Upload & Preview and wait for server acceptance.";return;
                }
                close();
            });
            add("Lock XYZ (Enter)",left+half+gap,row(7),half,buttonHeight,b->{
                if(!BuildAIClient.selecting){
                    status="Upload first, or press R to retry placement. G chooses ground Y, then ENTER locks XYZ.";return;
                }
                PlacementMode.lock();
                status=PlacementMode.groundSelected()?"Placement lock requested; wait for server approval.":
                    "To lock: close F8, aim at the TOP of a ground block and press G, then ENTER.";
            });
            add("Save Blueprint to Library",left,row(8),panelWidth,buttonHeight,b->exportSchematic());
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
                status="Retry requested. Wheel: Z; LEFT/RIGHT arrows: rotate; G: ground Y; ENTER: lock XYZ.";
            });
            add("Confirm & Build",left+half+gap,row(10),half,buttonHeight,b->{
                if(!BuildAIClient.previewLocked){
                    status="Placement not locked: aim at ground top + press G, then ENTER. Only then confirm the build.";return;
                }
                if(client==null||client.getNetworkHandler()==null){
                    status="Join a world before confirming.";return;
                }
                client.getNetworkHandler().sendChatCommand("buildai confirm");
                status="Construction requested; watch server status and the BuildAI HUD.";
            });
        }
        add("Close",left,row(11),panelWidth,buttonHeight,b->close());
    }
    private void connectChatGPT(){
        if(busy){status="Wait for the current operation to finish.";return;}
        saveFields();
        if(client!=null)client.setScreen(new ChatGPTConnectScreen(this));
    }
    private void requestAI(boolean revise){
        // If the player hid the monitor, pressing Generate again reopens the
        // SAME active request instead of starting another or showing a dead end.
        GenerationProgress inProgress=BuildAIClient.currentGeneration();
        if(inProgress!=null&&!inProgress.snapshot().finished()){
            if(client!=null)client.setScreen(new BuildAIGenerationScreen(this,inProgress));
            return;
        }
        if(busy){status="Wait for the current operation to finish.";return;}
        saveFields();
        String description=promptValue.trim();
        if(description.isBlank()){status="Enter your building description.";return;}
        String previous=BuildAIClient.designSpecification;
        if(revise&&(previous==null||previous.isBlank())){
            status="Generate a new design before requesting revisions.";return;
        }
        if(key!=null)BuildAIClient.setKey(key.getText());
        if(!ChatGPTSignIn.connected()&&BuildAIClient.getKey().isBlank()){
            status="Connect with ChatGPT, or open the optional API key field.";
            return;
        }
        GenerationProgress progress=new GenerationProgress(description);
        if(!BuildAIClient.beginGeneration(progress)){
            status="Another blueprint is already being generated.";
            return;
        }
        busy=true;
        status=revise?"Revising blueprint...":"Generating blueprint...";
        String activeKey=BuildAIClient.getKey();
        if(client!=null)client.setScreen(new BuildAIGenerationScreen(this,progress));
        CompletableFuture.supplyAsync(()->{
            try{
                if(ChatGPTSignIn.connected())return AIArchitect.generateWithChatGPT(
                    description,revise?previous:null,progress);
                return revise
                    ? AIArchitect.reviseDesign(activeKey,description,previous,progress)
                    : AIArchitect.generateDesign(activeKey,description,progress);
            }catch(Exception ex){throw new RuntimeException(ex);}
        }).whenComplete((result,error)->MinecraftClient.getInstance().execute(()->{
            busy=false;
            BuildAIClient.endGeneration(progress);
            if(error!=null){
                String reason=message(error);
                progress.failure(reason);
                status="Generation failed: "+reason;
                BuildAIClient.message(status);
                // The progress screen remains visible so the player can read the failure.
                return;
            }
            BuildAIClient.acceptGenerated(result);
            surfaceLayer=BuildAIClient.surfaceLayer;
            progress.success("Generated "+result.blueprint().solidCount()+
                " blocks. The blueprint is available for inspection.");
            GenerationHistory.record(progress);
            status=(revise?"Revised: ":"Created: ")+result.blueprint().name+
                " ("+result.blueprint().solidCount()+" blocks, "+
                result.blueprint().warnings.size()+" design QA warnings). Inspect layers or Design QA before placement.";
            // Preserve player control when the monitor was minimized.
            // Background completions should never force a menu over active play.
            tab=1;
            if(client!=null && (client.currentScreen instanceof BuildAIGenerationScreen ||
                client.currentScreen==this)){
                switchTab(1);
                client.setScreen(this);
            }else {
                BuildAIClient.message("Blueprint ready and auto-saving. Press F8 to review and place.");
            }
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
    public void importedFromLibrary(Blueprint loaded){
        BuildAIClient.acceptImport(loaded);
        surfaceLayer=0;tab=1;busy=false;
        status="Imported "+loaded.name+" ("+loaded.solidCount()+" blocks). Inspect or Upload & Preview.";
        if(client!=null)client.setScreen(this);
    }
    private void browse(){
        if(busy){status="Wait for the current operation to finish.";return;}
        if(client!=null){
            saveFields();
            client.setScreen(new BlueprintLibraryScreen(this));
        }
    }
    private void exportSchematic(){
        if(busy){status="Wait for the current operation to finish.";return;}
        if(BuildAIClient.draft==null){status="No blueprint loaded.";return;}
        final Blueprint selected=BuildAIClient.draft;
        busy=true;
        status="Saving to your Minecraft buildai/blueprints library...";
        CompletableFuture.supplyAsync(()->{
            try{return BlueprintLibrary.save(selected);}
            catch(Exception e){throw new RuntimeException(e);}
        }).whenComplete((saved,error)->MinecraftClient.getInstance().execute(()->{
            busy=false;
            if(error!=null){
                status="Save failed: "+message(error);
            }else{
                status="Saved "+saved.getFileName()+" to Minecraft/buildai/blueprints. Open Import File to reload.";
                BuildAIClient.message("Saved blueprint: "+saved.toAbsolutePath());
            }
        }));
    }
    @Override public void render(DrawContext ctx,int mx,int my,float delta){
        super.render(ctx,mx,my,delta);
        int x=left;
        ctx.drawCenteredTextWithShadow(textRenderer,"BuildAI | 1.21.1 | Architect",width/2,row(0),0x66ECC4);
        if(tab==0){
            ctx.drawTextWithShadow(textRenderer,"Describe your build or a revision",x,row(2),0xF1F6FA);
            String account=ChatGPTSignIn.connected()?"ChatGPT connected: "+ChatGPTSignIn.accountLabel():
                "ChatGPT: Not connected | Continue with ChatGPT to generate without API key";
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
