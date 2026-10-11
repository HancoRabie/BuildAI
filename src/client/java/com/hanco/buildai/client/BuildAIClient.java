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
    private static volatile boolean uploadPending=false;
    private static String apiKey="";
    private static KeyBinding open;
    /** Hide the visual-only ghost without erasing a saved blueprint or built terrain. */
    public static void clearPreview() {
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
        }else{
            clearPreview();
        }
        message(payload.message());
        MinecraftClient client=MinecraftClient.getInstance();
        if(client.currentScreen instanceof BuildAIScreen screen)
            screen.updateStatus(payload.accepted()
                ? "Server accepted blueprint. Close F8 to choose X/Z."
                : payload.message());
    }
    public static void setKey(String key){apiKey=key==null?"":key.trim();}
    public static String getKey(){return apiKey;}
    public static void message(String message){
        MinecraftClient mc=MinecraftClient.getInstance();
        if(mc.player!=null)mc.player.sendMessage(Text.literal("[BuildAI] "+message),false);
    }
    public static void generate(String prompt) {
        if(apiKey.isBlank()){message("Connect an OpenAI developer API key in F8 before enabling AI building chat.");return;}
        String text=prompt==null?"":prompt.trim();
        if(text.isBlank()){message("Describe a building or revision.");return;}
        boolean newBuild=text.startsWith("new ")||text.startsWith("new:");
        if(newBuild)text=text.substring(4).trim();
        final String description=text;
        final String key=apiKey;
        final String previous=newBuild?null:designSpecification;
        message(previous==null?"Generating new architectural blueprint...":"Revising existing blueprint...");
        CompletableFuture.supplyAsync(()->{
            try {
                return previous==null
                    ? AIArchitect.generateDesign(key,description)
                    : AIArchitect.reviseDesign(key,description,previous);
            }catch(Exception e){throw new RuntimeException(e);}
        }).whenComplete((result,error)->MinecraftClient.getInstance().execute(()->{
            if(error!=null){message("AI design failed: "+rootCause(error));return;}
            acceptGenerated(result);
            message((previous==null?"Generated ":"Revised ")+draft.name+" ("+
                draft.width+"x"+draft.height+"x"+draft.length+", "+
                draft.solidCount()+" blocks). Review in F8 before placing.");
            MinecraftClient mc=MinecraftClient.getInstance();
            if(mc.currentScreen==null)mc.setScreen(new BuildAIScreen());
        }));
    }
    public static void acceptGenerated(AIArchitect.DesignResult result) {
        clearPreview();
        draft=result.blueprint();
        designSpecification=result.specification();
    }
    public static void acceptImport(Blueprint imported) {
        clearPreview();
        draft=imported;
        designSpecification=null; // importing a schematic does not create an AI design history
    }
    private static String rootCause(Throwable t){while(t.getCause()!=null)t=t.getCause();return String.valueOf(t.getMessage());}
    @Override public void onInitializeClient(){
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(com.hanco.buildai.build.BuildBlocks.CONSTRUCTION_BLOCK,net.minecraft.client.render.RenderLayer.getTranslucent());
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(com.hanco.buildai.build.BuildBlocks.CONSTRUCTION_SLAB,net.minecraft.client.render.RenderLayer.getTranslucent());
        PreviewRenderer.register();
        ClientPlayNetworking.registerGlobalReceiver(com.hanco.buildai.net.BlueprintReadyPayload.ID,
            (payload,context)->context.client().execute(()->receiveUpload(payload)));
        ClientPlayNetworking.registerGlobalReceiver(
            com.hanco.buildai.net.ClearPreviewPayload.ID,
            (payload,context)->context.client().execute(BuildAIClient::clearPreview));
        ProjectHud.register();
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            com.hanco.buildai.net.ProjectStatusPayload.ID,
            (payload,context)->context.client().execute(()->{
                ProjectHud.latest=payload;
                if(payload.phase().equals("COMPLETE") || payload.phase().equals("CANCELLED"))
                    clearPreview();
            }));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
            (handler,client)->{ProjectHud.latest=null;clearPreview();});
        UseBlockCallback.EVENT.register((player,world,hand,hit)->{
            if(world.isClient() && surfacePending && player.isSneaking()){
                BlockPos surface=hit.getBlockPos();
                previewOrigin=new BlockPos(previewOrigin.getX(),surface.getY()+1-surfaceLayer,previewOrigin.getZ());
                surfacePending=false;previewLocked=true;
                message("Preview anchored to selected surface. Inspect it and run /buildai confirm to build.");
            }
            return ActionResult.PASS;
        });
        open=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.buildai.open",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_F8,"category.buildai"));
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            while(open.wasPressed())client.setScreen(new BuildAIScreen());
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
        ClientPreAttackCallback.EVENT.register((client,player,clickCount)->{
            if(!selecting||clickCount==0||!(client.crosshairTarget instanceof BlockHitResult hit))return false;
            if(!ClientPlayNetworking.canSend(PlacePayload.ID))return false;
            ClientPlayNetworking.send(new PlacePayload(hit.getBlockPos()));
            previewOrigin=new BlockPos(hit.getBlockPos().getX(),hit.getBlockPos().getY()+1-surfaceLayer,hit.getBlockPos().getZ());
            selecting=false;surfacePending=true;previewLocked=false;
            message("Horizontal location selected. Sneak-right-click the desired surface block.");
            return true;
        });
    }
}
