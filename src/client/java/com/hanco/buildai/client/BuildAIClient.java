package com.hanco.buildai.client;

import com.hanco.buildai.blueprint.Blueprint;
import com.hanco.buildai.net.PlacePayload;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import org.lwjgl.glfw.GLFW;
import java.util.concurrent.CompletableFuture;

public final class BuildAIClient implements ClientModInitializer {
    public static volatile Blueprint draft;
    /** Complete last AI-authored geometry document; never retained for imported files. */
    public static volatile String designSpecification;
    public static volatile boolean selecting=false;
    public static volatile boolean surfacePending=false;
    public static volatile boolean previewLocked=false;
    public static volatile int surfaceLayer=0;
    public static volatile BlockPos previewOrigin=null;
    public static volatile boolean chatMode=false;
    private static volatile GenerationProgress activeGeneration;
    private static volatile GenerationProgress minimizedProgress;
    public static void minimizeGeneration(GenerationProgress progress){
        minimizedProgress=progress;
        message("Generation continues while you play. A compact live progress HUD is now active; F8 reopens BuildAI.");
    }
    public static boolean isMinimized(GenerationProgress progress){
        return minimizedProgress==progress;
    }
    public static GenerationProgress minimizedProgress(){return minimizedProgress;}

    public static synchronized boolean beginGeneration(GenerationProgress progress){
        if(activeGeneration!=null && !activeGeneration.snapshot().finished())return false;
        activeGeneration=progress;minimizedProgress=null;return true;
    }
    public static GenerationProgress currentGeneration(){return activeGeneration;}
    public static synchronized void endGeneration(GenerationProgress progress){
        if(activeGeneration==progress)activeGeneration=null;
    }
    private static volatile boolean uploadPending=false;
    private static String apiKey="";
    private static KeyBinding open, lockPlacement, retryPlacement, snapGround, rotateLeft, rotateRight;
    /** Hide the visual-only ghost without erasing a saved blueprint or built terrain. */
    public static void clearPreview() {
        PlacementMode.stop();
        uploadPending=false;
        selecting=false;
        surfacePending=false;
        previewLocked=false;
        previewOrigin=null;
    }
    public static boolean uploadPending(){return uploadPending;}
    public static void beginUpload(Blueprint blueprint,int layer) throws java.io.IOException {
        if(uploadPending)throw new java.io.IOException("A blueprint upload is already awaiting server confirmation");
        if(blueprint==null)throw new java.io.IOException("No blueprint selected");
        if(layer<0||layer>=blueprint.height)throw new java.io.IOException("Surface layer out of range");
        clearPreview();
        surfaceLayer=layer;
        uploadPending=true;
        try{
            ClientUpload.send(blueprint,layer);
        }catch(java.io.IOException | RuntimeException e){
            uploadPending=false;
            throw e;
        }
    }
    private static void receiveUpload(com.hanco.buildai.net.BlueprintReadyPayload payload){
        if(!uploadPending)return;
        uploadPending=false;
        if(payload.accepted()){
            selecting=true;
            surfacePending=false;
            previewLocked=false;
            previewOrigin=null;
            // Prepare a normal-camera preview. F8.close() activates positioning
            // without changing the player's view, movement or flight mode.
            if(MinecraftClient.getInstance().currentScreen==null)PlacementMode.start();
        }else{
            clearPreview();
        }
        message(payload.message());
        MinecraftClient client=MinecraftClient.getInstance();
        if(client.currentScreen instanceof BuildAIScreen screen)
            screen.updateStatus(payload.accepted()
                ? "Blueprint accepted. Close F8, move normally, aim at the chosen ground and press G. Enter locks XYZ; R retries."
                : payload.message());
    }
    public static void setKey(String key){apiKey=key==null?"":key.trim();}
    public static String getKey(){return apiKey;}
    public static void message(String message){
        MinecraftClient mc=MinecraftClient.getInstance();
        if(mc.player!=null)mc.player.sendMessage(Text.literal("[BuildAI] "+message),false);
    }
    public static void generate(String prompt) {
        if(!ChatGPTSignIn.connected()&&apiKey.isBlank()){
            message("Open F8 and Continue with ChatGPT (or use an API key) to generate buildings.");return;
        }
        String text=prompt==null?"":prompt.trim();
        if(text.isBlank()){message("Describe a building or revision.");return;}
        boolean newBuild=text.startsWith("new ")||text.startsWith("new:");
        if(newBuild)text=text.substring(4).trim();
        final String description=text;
        final String key=apiKey;
        final String previous=newBuild?null:designSpecification;
        GenerationProgress progress=new GenerationProgress(description);
        if(!beginGeneration(progress)){
            message("A blueprint is already being generated. Wait for it to finish.");
            GenerationProgress pending=currentGeneration();
            if(pending!=null)MinecraftClient.getInstance().setScreen(
                new BuildAIGenerationScreen(new BuildAIScreen(),pending));
            return;
        }
        message(previous==null?"Generating new architectural blueprint...":"Revising existing blueprint...");
        MinecraftClient.getInstance().setScreen(
            new BuildAIGenerationScreen(new BuildAIScreen(),progress));
        CompletableFuture.supplyAsync(()->{
            try{
                if(ChatGPTSignIn.connected())return AIArchitect.generateWithChatGPT(description,previous,progress);
                return previous==null
                    ? AIArchitect.generateDesign(key,description,progress)
                    : AIArchitect.reviseDesign(key,description,previous,progress);
            }catch(Exception error){throw new RuntimeException(error);}
        }).whenComplete((result,error)->MinecraftClient.getInstance().execute(()->{
            endGeneration(progress);
            if(error!=null){
                String reason=rootCause(error);
                progress.failure(reason);
                message("AI design failed: "+reason);
                return;
            }
            acceptGenerated(result);
            progress.success("Generated "+draft.solidCount()+" blocks. Ready for inspection.");
            GenerationHistory.record(progress);
            message((previous==null?"Generated ":"Revised ")+draft.name+" ("+
                draft.width+"x"+draft.height+"x"+draft.length+", "+draft.solidCount()+
                " blocks, "+draft.warnings.size()+
                " architectural QA warning(s)). Open F8 > Placement / Export > Design QA / Warnings.");
            MinecraftClient mc=MinecraftClient.getInstance();
            if(mc.currentScreen instanceof BuildAIGenerationScreen || mc.currentScreen instanceof BuildAIScreen)
                mc.setScreen(BuildAIScreen.generatedResult());
            else if(isMinimized(progress))
                message("Blueprint generated and autosave started. Open F8 to inspect and place it.");
            else if(mc.currentScreen==null)
                message("Blueprint generated. Open F8 to inspect and place it.");
        }));
    }
    public static void acceptGenerated(AIArchitect.DesignResult result) {
        clearPreview();
        draft=result.blueprint();
        surfaceLayer=Math.max(0,Math.min(surfaceLayer,draft.height-1));
        designSpecification=result.specification();
        // Autosave immediately after a successful AI blueprint compilation, even if
        // the player closes F8 or never presses the manual Export control.
        Blueprint completed=draft;
        CompletableFuture.supplyAsync(()->{
            try{return BlueprintLibrary.save(completed);}
            catch(Exception e){throw new RuntimeException(e);}
        }).whenComplete((saved,error)->MinecraftClient.getInstance().execute(()->{
            if(error!=null){
                message("Automatic blueprint save failed. Keep this world open and retry Export: "+
                    rootCause(error));
            }else{
                message("Auto-saved: "+saved.getFileName()+
                    " (F8 > Import File to reload later)");
            }
        }));
    }
    public static void acceptImport(Blueprint imported) {
        clearPreview();
        draft=imported;
        surfaceLayer=0;
        designSpecification=null; // importing a schematic does not create an AI design history
    }
    private static String rootCause(Throwable t){while(t.getCause()!=null)t=t.getCause();return String.valueOf(t.getMessage());}
    @Override public void onInitializeClient(){
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(com.hanco.buildai.build.BuildBlocks.CONSTRUCTION_BLOCK,net.minecraft.client.render.RenderLayer.getTranslucent());
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(com.hanco.buildai.build.BuildBlocks.CONSTRUCTION_SLAB,net.minecraft.client.render.RenderLayer.getTranslucent());
        PreviewRenderer.register();
        PlacementHud.register();
        CinematicExcavationRenderer.register();
        ClientPlayNetworking.registerGlobalReceiver(
            com.hanco.buildai.net.CinematicExcavationPayload.ID,
            (payload,context)->context.client().execute(()->
                CinematicExcavationRenderer.receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(com.hanco.buildai.net.PlacementReplyPayload.ID,
            (payload,context)->context.client().execute(()->PlacementMode.receive(
                payload.accepted(),payload.locked(),payload.origin(),payload.message())));
        ClientPlayNetworking.registerGlobalReceiver(com.hanco.buildai.net.BlueprintReadyPayload.ID,
            (payload,context)->context.client().execute(()->receiveUpload(payload)));
        ClientPlayNetworking.registerGlobalReceiver(
            com.hanco.buildai.net.ClearPreviewPayload.ID,
            (payload,context)->context.client().execute(BuildAIClient::clearPreview));
        ProjectHud.register();
        GenerationHud.register();
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            com.hanco.buildai.net.ProjectStatusPayload.ID,
            (payload,context)->context.client().execute(()->{
                ProjectHud.latest=payload;
                if(payload.phase().equals("COMPLETE") || payload.phase().equals("CANCELLED")){
                    clearPreview();
                    CinematicExcavationRenderer.reset();
                }
            }));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
            (handler,client)->{
                ProjectHud.latest=null;chatMode=false;clearPreview();
                CinematicExcavationRenderer.reset();
            });
        open=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.buildai.open",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_F8,"category.buildai"));
        lockPlacement=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.buildai.lock_placement",
            InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_ENTER,"category.buildai"));
        retryPlacement=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.buildai.retry_placement",
            InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_R,"category.buildai"));
        snapGround=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.buildai.snap_ground",
            InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_G,"category.buildai"));
        rotateLeft=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.buildai.rotate_left",
            InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_LEFT,"category.buildai"));
        rotateRight=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.buildai.rotate_right",
            InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_RIGHT,"category.buildai"));
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(client.player==null)return;
            while(open.wasPressed())client.setScreen(new BuildAIScreen());
            if(client.currentScreen==null){
                while(lockPlacement.wasPressed())PlacementMode.lock();
                while(snapGround.wasPressed())PlacementMode.snapGround();
                while(rotateLeft.wasPressed())PlacementMode.rotate(-1);
                while(rotateRight.wasPressed())PlacementMode.rotate(1);
                while(retryPlacement.wasPressed()){
                    if(selecting||previewLocked||PlacementMode.active())PlacementMode.retry();
                }
            }
            PlacementMode.tick();
            CinematicExcavationRenderer.tick();
        });
        ClientSendMessageEvents.ALLOW_CHAT.register(message->{
            if(!chatMode)return true;
            if(message.trim().equalsIgnoreCase("exit")){
                chatMode=false;BuildAIClient.message("Chat Mode disabled.");return false;
            }
            if(message.trim().equalsIgnoreCase("reset")){
                clearPreview();draft=null;designSpecification=null;
                BuildAIClient.message("Previous AI design cleared. Describe a new building.");return false;
            }
            generate(message);
            return false; // never leak private building prompts to public server chat
        });
    }
}
