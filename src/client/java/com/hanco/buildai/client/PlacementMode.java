package com.hanco.buildai.client;

import com.hanco.buildai.blueprint.Blueprint;
import com.hanco.buildai.blueprint.BlueprintRotator;
import com.hanco.buildai.net.PlacePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * BuildAI placement in the real Minecraft camera. Never hijacks mouse-look,
 * player movement, survival reach, creative flight or third-person settings.
 *
 * Before G: ghost follows player, starting in FRONT of their camera.
 * Wheel moves along WORLD Z. Left/Right rotate 90 degrees about footprint center.
 * G fixes surface Y and the proposed XYZ; player can then walk around to inspect.
 * Enter locks the exact visible rotation and XYZ server-side. R retries.
 */
public final class PlacementMode {
    private PlacementMode(){}
    private static boolean prepared=false,groundSelected=false,waitingLock=false;
    private static int quarterTurns=0,zOffset=0;
    private static final int FRONT_GAP=3;
    public static boolean active(){
        return prepared && (BuildAIClient.selecting || BuildAIClient.previewLocked);
    }
    public static boolean following(){return active() && BuildAIClient.selecting && !groundSelected;}
    public static int rotation(){return quarterTurns;}
    public static int wheelOffset(){return zOffset;}
    public static boolean groundSelected(){return groundSelected;}
    public static boolean waitingForLock(){return waitingLock;}
    public static void start(){start(false);}
    public static void inspect(){start(true);}
    private static void start(boolean lockedReview){
        MinecraftClient mc=MinecraftClient.getInstance();
        if(mc.player==null||mc.world==null||BuildAIClient.draft==null)return;
        if(!prepared){
            prepared=true;
            groundSelected=lockedReview;
            waitingLock=false;
            zOffset=0;
            // Mouse yaw 0 faces south, 90 west, 180 north, -90 east.
            // The unrotated schematic front is local -Z.
            quarterTurns=Math.floorMod(Math.round(mc.player.getYaw()/90f),4);
        }
        if(lockedReview){
            BuildAIClient.previewLocked=true;
            BuildAIClient.selecting=false;
        }else if(!groundSelected&&!BuildAIClient.previewLocked){
            BuildAIClient.selecting=true;
            updatePlayerPreview();
        }
        BuildAIClient.message(lockedReview
            ? "Location locked. F8 > Confirm & Build; R to retry."
            : "Building starts IN FRONT of you. Scroll moves world Z; LEFT/RIGHT arrows rotate. Aim at ground + G for height, ENTER locks.");
    }
    public static void tick(){
        if(!active())return;
        MinecraftClient mc=MinecraftClient.getInstance();
        if(mc.player==null||mc.world==null||mc.player.isDead()){
            stop();BuildAIClient.clearPreview();return;
        }
        if(mc.currentScreen==null&&following()&&!waitingLock)updatePlayerPreview();
    }
    private static void updatePlayerPreview(){
        MinecraftClient mc=MinecraftClient.getInstance();
        Blueprint b=BuildAIClient.draft;
        if(b==null||mc.player==null)return;
        int w=BlueprintRotator.width(b,quarterTurns),l=BlueprintRotator.length(b,quarterTurns);
        BlockPos player=mc.player.getBlockPos();
        int x,z;
        // Player stands OUTSIDE the schematic's front edge, facing its facade.
        switch(quarterTurns){
            case 1->{x=player.getX()-FRONT_GAP-w;z=player.getZ()-l/2;}
            case 2->{x=player.getX()-w/2;z=player.getZ()-FRONT_GAP-l;}
            case 3->{x=player.getX()+FRONT_GAP;z=player.getZ()-l/2;}
            default->{x=player.getX()-w/2;z=player.getZ()+FRONT_GAP;}
        }
        BuildAIClient.previewOrigin=new BlockPos(
            x,player.getY()-BuildAIClient.surfaceLayer,z+zOffset);
    }
    /** Cancel vanilla hotbar scroll ONLY during an unlocked in-world placement. */
    public static boolean scroll(double vertical){
        if(!active()||!BuildAIClient.selecting||waitingLock)return false;
        MinecraftClient mc=MinecraftClient.getInstance();
        if(mc.currentScreen!=null)return false;
        int amount=(int)Math.round(vertical);
        if(amount==0)amount=vertical>0?1:-1;
        amount=Math.max(-16,Math.min(16,amount));
        // Mouse up advances +Z and mouse down moves -Z, independent of look direction.
        int updated=Math.max(-100_000,Math.min(100_000,zOffset+amount));
        int difference=updated-zOffset;
        if(difference==0)return true;
        zOffset=updated;
        if(groundSelected){
            if(BuildAIClient.previewOrigin!=null)
                BuildAIClient.previewOrigin=BuildAIClient.previewOrigin.add(0,0,difference);
        }else updatePlayerPreview();
        return true;
    }
    public static void rotate(int direction){
        if(!active()||!BuildAIClient.selecting||waitingLock)return;
        MinecraftClient mc=MinecraftClient.getInstance();
        if(mc.currentScreen!=null)return;
        Blueprint b=BuildAIClient.draft;
        if(b==null)return;
        int oldW=BlueprintRotator.width(b,quarterTurns);
        int oldL=BlueprintRotator.length(b,quarterTurns);
        quarterTurns=Math.floorMod(quarterTurns+direction,4);
        if(groundSelected && BuildAIClient.previewOrigin!=null){
            BlockPos origin=BuildAIClient.previewOrigin;
            int w=BlueprintRotator.width(b,quarterTurns);
            int l=BlueprintRotator.length(b,quarterTurns);
            // Keep the XZ center stationary on rotation.
            BuildAIClient.previewOrigin=new BlockPos(
                origin.getX()+Math.floorDiv(oldW-w,2),
                origin.getY(),
                origin.getZ()+Math.floorDiv(oldL-l,2));
        }else updatePlayerPreview();
        BuildAIClient.message("Rotated "+(quarterTurns*90)+
            " degrees. Scroll moves world Z; G selects ground level; ENTER locks placement.");
    }
    public static void snapGround(){
        MinecraftClient mc=MinecraftClient.getInstance();
        if(!active()||!BuildAIClient.selecting||waitingLock)return;
        if(!(mc.crosshairTarget instanceof BlockHitResult hit) ||
            hit.getType()!=HitResult.Type.BLOCK || hit.getSide()!=Direction.UP){
            BuildAIClient.message("Point at the TOP of a ground block and press G to set blueprint surface Y.");
            return;
        }
        if(BuildAIClient.previewOrigin==null)updatePlayerPreview();
        BlockPos base=BuildAIClient.previewOrigin;
        if(base==null)return;
        BlockPos ground=hit.getBlockPos();
        // Never re-center the building on the player's feet or clicked block.
        // G is the SURFACE reference. Horizontal placement remains user controlled.
        BuildAIClient.previewOrigin=new BlockPos(
            base.getX(),ground.getY()+1-BuildAIClient.surfaceLayer,base.getZ());
        groundSelected=true;
        BuildAIClient.message("Ground Y set to "+(ground.getY()+1)+
            ". Preview is fixed; scroll adjusts world Z, LEFT/RIGHT rotates. Press ENTER to lock XYZ (R retries).");
    }
    public static void lock(){
        if(!active()||!BuildAIClient.selecting||waitingLock)return;
        if(!groundSelected){
            BuildAIClient.message("Placement not locked: aim at TOP of a ground block and press G, then press ENTER.");
            return;
        }
        if(BuildAIClient.previewOrigin==null)return;
        if(!ClientPlayNetworking.canSend(PlacePayload.ID)){
            BuildAIClient.message("Server does not support BuildAI placement.");return;
        }
        waitingLock=true;
        BlockPos proposal=BuildAIClient.previewOrigin.toImmutable();
        ClientPlayNetworking.send(new PlacePayload(proposal,quarterTurns));
        BuildAIClient.message("Validating XYZ "+proposal.toShortString()+
            " at "+(quarterTurns*90)+" degrees. After approval: F8 > Confirm & Build.");
    }
    public static void receive(boolean accepted,boolean locked,BlockPos origin,String message){
        waitingLock=false;
        if(!accepted){
            BuildAIClient.message(message+" Adjust position, then press ENTER again.");
            return;
        }
        prepared=true;
        if(locked){
            BuildAIClient.selecting=false;
            BuildAIClient.previewLocked=true;
            groundSelected=true;
            BuildAIClient.previewOrigin=origin.toImmutable();
            BuildAIClient.message("Position locked! F8 > Confirm & Build, or /buildai confirm. R to retry.");
        }else{
            groundSelected=false;
            BuildAIClient.previewLocked=false;
            BuildAIClient.selecting=true;
            updatePlayerPreview();
            BuildAIClient.message("Placement reset. Scroll world Z; LEFT/RIGHT rotate; aim at ground + G, then ENTER.");
        }
    }
    public static void retry(){
        MinecraftClient mc=MinecraftClient.getInstance();
        if(mc.player!=null&&mc.getNetworkHandler()!=null)
            mc.getNetworkHandler().sendChatCommand("buildai retry");
    }
    public static void stop(){
        prepared=false;
        groundSelected=false;
        waitingLock=false;
        quarterTurns=0;
        zOffset=0;
    }
}
