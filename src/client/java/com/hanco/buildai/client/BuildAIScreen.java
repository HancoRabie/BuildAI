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
    /** Clear both the live text and the restart backup. Used by the
     * editor and by Cancel & Refine, so F8 cannot resurrect old text.
     */
    public void clearRetainedPrompt(){
        promptValue="";
        if(prompt!=null){
            prompt.setText("");
            prompt.setFocused(true);
        }
        boolean cleared=GenerationPromptJournal.clear();
        status=cleared?"Prompt cleared. Saved prompt backup removed.":
            "Text cleared, but saved backup could not be removed.";
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
        // Recover prior request across Minecraft restart or a stalled session.
        // Never overwrite text the user has already typed in this UI.
        if(prompt==null && promptValue.isBlank())
            promptValue=GenerationPromptJournal.load();
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
            // One unified prompt row: text entry plus a small trailing
            // in-field clear button, never drawn ON TOP of the typed text.
            int clearWidth=20;
            int promptHeight=Math.max(12,buttonHeight);
            prompt=new TextFieldWidget(textRenderer,left,row(3),
                panelWidth-clearWidth,promptHeight,text("Building description"));
            prompt.setMaxLength(4000);prompt.setText(promptValue);
            prompt.setPlaceholder(text("Describe a castle, house, factory, or a requested revision"));
            addDrawableChild(prompt);
            ButtonWidget clearPrompt=ButtonWidget.builder(
                text("×"),button->{
                    clearRetainedPrompt();
                }).dimensions(left+panelWidth-clearWidth,row(3),
                    clearWidth,promptHeight).build();
            clearPrompt.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(
                text("Clear the prompt AND its saved local backup")));
            addDrawableChild(clearPrompt);
            add(ChatGPTSignIn.connected()?"Reconnect ChatGPT":"Continue with ChatGPT",
                left,row(5),half,buttonHeight,b->connectChatGPT());
            add("API key option",left+half+gap,row(5),half,buttonHeight,b->{
                saveFields();showApiKey=!showApiKey;switchTab(0);
                status=showApiKey?"API key field opened (separate OpenAI billing).":"API key field hidden.";
            });
            GenerationProgress running=BuildAIClient.currentGeneration();
            if(running!=null&&!running.snapshot().finished()){
                add("View Running Generation | Return to Game Does Not Cancel",left,row(6),
                    panelWidth,buttonHeight,b->client.setScreen(
                        new BuildAIGenerationScreen(this,running)));
            }else{
                add("Online Inspiration: "+(InspirationSettings.enabled()?"ON":"OFF")+
                    " | research examples before building",
                    left,row(6),panelWidth,buttonHeight,button->{
                        try{
                            boolean next=!InspirationSettings.enabled();
                            InspirationSettings.setEnabled(next);
                            button.setMessage(text("Online Inspiration: "+(next?"ON":"OFF")+
                                " | research examples before building"));
                            status=next
                                ?"Online research enabled for every new build/revision. May add time and API charges."
                                :"Online research disabled. BuildAI uses local references and design rules.";
                        }catch(Exception ex){
                            status="Could not change inspiration setting: "+ex.getMessage();
                        }
                    });
            }
            if(showApiKey){
                key=new TextFieldWidget(textRenderer,left,row(7),panelWidth,buttonHeight,text("OpenAI developer API key"));
                key.setMaxLength(500);key.setText(keyValue.isBlank()?BuildAIClient.getKey():keyValue);
                addDrawableChild(key);
            }else{
                add("Upload Image > Pixel Art  |  Offline, No AI Credits",
                    left,row(7),panelWidth,buttonHeight,b->{
                        if(busy){status="Finish the current operation first.";return;}
                        saveFields();
                        client.setScreen(new BuildAIPixelArtScreen(this));
                    });
            }
            add("Generate New",left,row(8),half,buttonHeight,b->requestAI(false));
            add("Revise Existing",left+half+gap,row(8),half,buttonHeight,b->requestAI(true));
            add("My Blueprints / Import",left,row(9),half,buttonHeight,b->browse());
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
            add("Safe Undo",left,row(3),half,buttonHeight,b->{
                if(client==null||client.getNetworkHandler()==null){
                    status="Join a world to request undo.";return;
                }
                client.getNetworkHandler().sendChatCommand("buildai undo");
                status="Safe undo requested. Follow /buildai status; protected containers are not overwritten.";
            });
            add("Force Undo (OP4)...",left+half+gap,row(3),half,buttonHeight,b->{
                if(client==null||client.getNetworkHandler()==null){
                    status="Join a world to request undo.";return;
                }
                saveFields();
                client.setScreen(new BuildAIUndoConfirmScreen(this));
            });
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
                    status="Close F8: G freezes the ghost without targeting. Arrows change XYZ, CTRL+L/R rotates, CTRL+U/D or wheel adjusts depth; Enter locks.";
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
                    status="Upload first, or press R to retry. Press G outside the GUI to freeze the ghost, then arrows/wheel to adjust.";return;
                }
                PlacementMode.lock();
                status=PlacementMode.groundSelected()?"Placement lock requested; wait for server approval.":
                    "Close F8 and press G to freeze the visible ghost (no block required), then press ENTER.";
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
                status="Retry requested. G freezes; arrows adjust, CTRL+L/R rotates, CTRL+U/D or wheel moves depth. ENTER locks.";
            });
            add("Confirm & Build",left+half+gap,row(10),half,buttonHeight,b->{
                if(!BuildAIClient.previewLocked){
                    status="Placement not locked: close F8 and press G to freeze the preview; arrows adjust; Enter locks XYZ.";return;
                }
                if(client==null||client.getNetworkHandler()==null){
                    status="Join a world before confirming.";return;
                }
                client.getNetworkHandler().sendChatCommand("buildai confirm");
                status="Default full-clear build requested. To excavate ONLY enclosed room interiors, use Minecraft chat: /buildai confirm hollow:yes (add foundation:no to omit identifiable base platforms). For terrain overlay use foundation:no excavate:no.";
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
        saveFields();
        String description=promptValue.trim();
        String existing=BuildAIClient.designSpecification;
        if(revise && (existing==null||existing.isBlank()) && BuildAIClient.draft==null){
            status="No previous design. Generate first, or choose an older file in My Blueprints.";
            return;
        }
        String original=revise&&BuildAIClient.draft!=null
            ?BuildAIClient.draft.originalPrompt():description;
        launchAI(description,revise,original,revise?BuildAIClient.draft:null);
    }

    /** Called by My Blueprints; works with the explicitly chosen saved file,
     * never whichever draft happened to be active in another BuildAI tab.
     * Every success autosaves a NEW uniquely named file. The source is never
     * touched, and failures leave the user's active draft unchanged.
     */
    public void startFromLibrary(Blueprint source,String instruction,boolean revision){
        if(source==null){status="No saved blueprint selected.";return;}
        promptValue=instruction;
        tab=0;
        launchAI(instruction.trim(),revision,
            revision?source.originalPrompt():instruction.trim(),revision?source:null);
    }

    /** A cancelled/refused run is never resumed. Send the revised text as
     * a totally fresh request, with NO previous AI blueprint or response.
     */
    public boolean startFreshAfterClarification(String revisedPrompt){
        if(revisedPrompt==null||revisedPrompt.isBlank()||revisedPrompt.length()>4000){
            status="Keep the refined request between 1 and 4000 characters.";
            return false;
        }
        GenerationProgress pending=BuildAIClient.currentGeneration();
        if(pending!=null&&!pending.snapshot().finished()){
            status="Stopping previous request. Wait for its network worker to close.";
            return false;
        }
        if(busy){
            status="Previous network request is still closing. Try again shortly.";
            return false;
        }
        promptValue=revisedPrompt;
        if(prompt!=null)prompt.setText(revisedPrompt);
        tab=0;
        launchAI(revisedPrompt,false,revisedPrompt,null);
        return BuildAIClient.currentGeneration()!=null;
    }

    private void launchAI(String description,boolean revise,String originalPrompt,
                          Blueprint revisionSource) {
        GenerationProgress current=BuildAIClient.currentGeneration();
        if(current!=null&&!current.snapshot().finished()){
            if(client!=null)client.setScreen(new BuildAIGenerationScreen(this,current));
            return;
        }
        if(busy){status="An AI request is already running.";return;}
        if(description.isBlank()||description.length()>4000){
            status="Enter an instruction between 1 and 4000 characters.";return;
        }
        if(key!=null)BuildAIClient.setKey(key.getText());
        if(!ChatGPTSignIn.connected()&&BuildAIClient.getKey().isBlank()){
            status="Connect ChatGPT or enter your optional API key to generate.";return;
        }
        final String apiKey=BuildAIClient.getKey();
        final String activeOriginal=originalPrompt==null?"":originalPrompt;
        final String previousInMemory=BuildAIClient.designSpecification;
        GenerationProgress progress=new GenerationProgress(description);
        progress.revisionMode(revise);
        if(!BuildAIClient.beginGeneration(progress)){
            status="Another blueprint generation is active.";return;
        }
        busy=true;
        status=revise?"Creating revised COPY from the selected blueprint...":
            StagedFarmPlan.applies(description)?
                "Starting staged multi-floor engineering: architecture, "+
                "crop systems, then shared controls...":
                "Generating an independent NEW blueprint from the original prompt...";
        if(client!=null)client.setScreen(new BuildAIGenerationScreen(this,progress));
        CompletableFuture.supplyAsync(()->{
            progress.bindWorker();
            try{
                // Legacy schematics have no source JSON; build a bounded,
                // exact geometry revision reference off the render thread.
                String previous=null;
                if(revise)
                    previous=revisionSource!=null?
                        BlueprintRevisionSeed.from(revisionSource):previousInMemory;
                if(revise&&(previous==null||previous.isBlank()))
                    throw new IllegalArgumentException("No design to revise. "+
                        "Use My Blueprints > Remake / Revise.");
                // The staged farm architect audits each crop stage and the
                // final assembled blueprint itself; do not re-request
                // the massive full integrated design here.
                if(!revise && StagedFarmPlan.applies(description))
                    return StagedFarmArchitect.generate(description,apiKey,
                        ChatGPTSignIn.connected(),progress);
                AIArchitect.DesignResult result=ChatGPTSignIn.connected()
                    ?AIArchitect.generateWithChatGPT(description,previous,progress)
                    :previous==null
                        ?AIArchitect.generateDesign(apiKey,description,progress)
                        :AIArchitect.reviseDesign(apiKey,description,previous,progress);
                return BuildQualityReview.review(description,result,apiKey,
                    ChatGPTSignIn.connected(),progress);
            }catch(Exception ex){throw new RuntimeException(ex);}
            finally{progress.releaseWorker();}
        }).whenComplete((result,error)->MinecraftClient.getInstance().execute(()->{
            busy=false;
            BuildAIClient.endGeneration(progress);
            if(progress.cancellationRequested()){
                progress.cancelled();
                status="Previous attempt cancelled. Original prompt preserved. "+
                    "Use Refine & Start Fresh for a new independent request.";
                return; // Never install a late blueprint from a cancelled request.
            }
            if(error!=null){
                String reason=message(error);
                progress.failure(reason);
                status="Generation failed: "+progress.failureSummary()+
                    " Use COPY FAILURE REPORT on the generation screen "+
                    "for the actual stream and validation details.";
                BuildAIClient.message(status);
                return;
            }
            BuildAIClient.acceptGenerated(result,activeOriginal);
            surfaceLayer=BuildAIClient.surfaceLayer;
            progress.success("NEW copy generated with "+result.blueprint().solidCount()+
                " blocks; original saved blueprint remains unchanged.");
            GenerationHistory.record(progress);
            status=(revise?"Revised COPY: ":"Fresh COPY: ")+result.blueprint().name+
                " ("+result.blueprint().solidCount()+" blocks). "+
                "New file auto-saved; original remains untouched.";
            tab=1;
            if(client!=null && (client.currentScreen instanceof BuildAIGenerationScreen ||
                client.currentScreen==this)){
                switchTab(1);
                client.setScreen(this);
            }else{
                BuildAIClient.message("New blueprint saved without modifying the old file. "+
                    "Press F8 to inspect and place it.");
            }
        }));
    }
    private static String message(Throwable throwable){
        // CompletableFuture wraps IOException in CompletionException and
        // RuntimeException. The old version returned ONLY the deepest cause
        // and discarded the important "missing response.completed", HTTP,
        // recovery-path, and validation context from the intermediate error.
        java.util.LinkedHashSet<String> distinct=new java.util.LinkedHashSet<>();
        Throwable t=throwable;int depth=0;
        while(t!=null&&depth++<7){
            String message=t.getMessage();
            if(message!=null&&!message.isBlank()&&
                !(message.equals(t.getClass().getName())))
                distinct.add(message.trim());
            t=t.getCause();
        }
        String combined=String.join(" | Caused by: ",distinct);
        return combined.isBlank()?throwable.toString():
            combined.substring(0,Math.min(combined.length(),3000));
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
    @Override public void renderBackground(DrawContext ctx,int mx,int my,float delta){
        // Minecraft GUI scale controls logical pixels; the dark studio panel
        // and thin color-coded rails are decorative and never cover widgets.
        ctx.fill(left-11,Math.max(0,row(0)-12),left+panelWidth+11,
            Math.min(height,row(13)+15),0xEB172C3A);
        ctx.fill(left-11,Math.max(0,row(0)-12),left+panelWidth+11,
            Math.max(2,row(0)-9),0xFF69D7BD);
        ctx.fill(left-5,row(2)-2,left-2,row(2)+Math.max(12,lineHeight-2),0xFF69D7BD);
        ctx.fill(left-5,row(tab==0?8:6)-2,left-2,
            row(tab==0?8:6)+Math.max(12,lineHeight-2),0xFF87C1EA);
    }
    @Override public void render(DrawContext ctx,int mx,int my,float delta){
        super.render(ctx,mx,my,delta);
        int x=left;
        ctx.drawCenteredTextWithShadow(textRenderer,"BuildAI | 1.21.1 | Architect",width/2,row(0),0x66ECC4);
        if(tab==0){
            // The prompt field already has a placeholder: do NOT overdraw the
            // Feedback & Styles button on row 2 with a second label.
            // Demo-safe indicator: do not print OAuth email/subject here.
            String account=ChatGPTSignIn.connected()?"ChatGPT: Connected":
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
        // Status fits in its dedicated footer, not over the buttons above.
        for(int line=0;line<Math.min(2,messages.size());line++)
            ctx.drawTextWithShadow(textRenderer,messages.get(line),
                x,Math.min(height-11,row(12+line)),0x79EBBD);
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
