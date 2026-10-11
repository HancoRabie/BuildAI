package com.hanco.buildai.client;

import com.hanco.buildai.blueprint.Blueprint;
import com.hanco.buildai.net.PlacePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

/**
 * Normal-player placement. BuildAI NEVER creates a spectator entity, redirects
 * mouse look, freezes keyboard input, or changes Minecraft's camera/perspective.
 *
 * Creative: use vanilla flight. Survival: walk to/aim at the intended ground.
 * Until G is pressed the ghost follows the player (approximate site preview).
 * G anchors the blueprint around the actual targeted ground block at its chosen
 * surface layer. The ghost STAYS at that spot while the player moves to inspect.
 * Enter asks the server to validate XYZ; R resets without excavating anything.
 */
public final class PlacementMode {
    private PlacementMode(){}
    private static boolean prepared=false,groundSelected=false,waitingLock=false;

    public static boolean active(){
        return prepared && (BuildAIClient.selecting || BuildAIClient.previewLocked);
    }
    public static boolean following(){return active() && BuildAIClient.selecting && !groundSelected;}
    public static void start(){start(false);}
    public static void inspect(){start(true);}
    private static void start(boolean lockedReview){
        MinecraftClient mc=MinecraftClient.getInstance();
        if(mc.player==null||mc.world==null||BuildAIClient.draft==null)return;
        if(!prepared) {
            prepared=true;
            groundSelected=lockedReview;
            waitingLock=false;
        }
        if(lockedReview){
            BuildAIClient.previewLocked=true;
            BuildAIClient.selecting=false;
        }else if(!groundSelected&&!BuildAIClient.previewLocked){
            BuildAIClient.selecting=true;
            updatePlayerPreview();
        }
        BuildAIClient.message(lockedReview
            ? "Position locked. Inspect in your normal view, then /buildai confirm; R retries."
            : "Normal movement and camera enabled. Stand near site, aim at ground, press G; Enter locks, R retries.");
    }
    public static void tick(){
        if(!active())return;
        MinecraftClient mc=MinecraftClient.getInstance();
        if(mc.player==null||mc.world==null||mc.player.isDead()){
            stop();BuildAIClient.clearPreview();return;
        }
        // No camera changes. Vanilla movement, hotbar, look, flight and reach stay intact.
        if(mc.currentScreen==null&&following()&&!waitingLock)updatePlayerPreview();
    }
    private static void updatePlayerPreview(){
        MinecraftClient mc=MinecraftClient.getInstance();
        Blueprint b=BuildAIClient.draft;
        if(b==null||mc.player==null)return;
        // Initial approximate footprint centered near the player's ground location.
        BlockPos p=mc.player.getBlockPos();
        BuildAIClient.previewOrigin=new BlockPos(
            p.getX()-b.width/2,
            p.getY()-BuildAIClient.surfaceLayer,
            p.getZ()-b.length/2);
    }
    public static void snapGround(){
        MinecraftClient mc=MinecraftClient.getInstance();
        if(!active()||!BuildAIClient.selecting||waitingLock)return;
        if(!(mc.crosshairTarget instanceof BlockHitResult hit) ||
            hit.getType()!=net.minecraft.util.hit.HitResult.Type.BLOCK){
            BuildAIClient.message("Aim directly at the ground block where the building should be centered; press G.");
            return;
        }
        Blueprint b=BuildAIClient.draft;
        if(b==null)return;
        BlockPos ground=hit.getBlockPos();
        // Ground top is the selected schematic's surface layer.
        BuildAIClient.previewOrigin=new BlockPos(
            ground.getX()-b.width/2,
            ground.getY()+1-BuildAIClient.surfaceLayer,
            ground.getZ()-b.length/2);
        groundSelected=true;
        BuildAIClient.message("Surface selected at "+ground.toShortString()+
            ". Move normally to inspect the fixed ghost; Enter locks XYZ, R retries.");
    }
    public static void lock(){
        if(!active()||!BuildAIClient.selecting||waitingLock)return;
        if(!groundSelected){
            BuildAIClient.message("First aim at the chosen ground block and press G. Then press Enter.");
            return;
        }
        if(BuildAIClient.previewOrigin==null)return;
        if(!ClientPlayNetworking.canSend(PlacePayload.ID)){
            BuildAIClient.message("Server does not support BuildAI placement.");return;
        }
        waitingLock=true;
        BlockPos proposal=BuildAIClient.previewOrigin.toImmutable();
        ClientPlayNetworking.send(new PlacePayload(proposal));
        BuildAIClient.message("Checking blueprint at "+proposal.toShortString()+" against world limits and protected blocks...");
    }
    public static void receive(boolean accepted,boolean locked,BlockPos origin,String message){
        waitingLock=false;
        if(!accepted){BuildAIClient.message(message);return;}
        prepared=true;
        if(locked){
            BuildAIClient.selecting=false;
            BuildAIClient.previewLocked=true;
            groundSelected=true;
            BuildAIClient.previewOrigin=origin.toImmutable();
            BuildAIClient.message("Location locked. Inspect with your NORMAL player view and run /buildai confirm, or R to retry.");
        }else{
            groundSelected=false;
            BuildAIClient.previewLocked=false;
            BuildAIClient.selecting=true;
            updatePlayerPreview();
            BuildAIClient.message("Placement reset. Move normally, aim at ground and press G; Enter locks.");
        }
    }
    public static void retry(){
        MinecraftClient mc=MinecraftClient.getInstance();
        if(mc.player!=null&&mc.getNetworkHandler()!=null)
            mc.getNetworkHandler().sendChatCommand("buildai retry");
    }
    /** Retained for legacy callsites; default hotbar scroll is NEVER intercepted. */
    public static void scroll(double ignored){}
    public static void stop(){
        prepared=false;
        groundSelected=false;
        waitingLock=false;
    }
}
