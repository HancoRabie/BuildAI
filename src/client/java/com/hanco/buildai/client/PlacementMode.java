package com.hanco.buildai.client;

import com.hanco.buildai.blueprint.Blueprint;
import com.hanco.buildai.blueprint.BlueprintRotator;
import com.hanco.buildai.blueprint.PlacementAdjustments;
import com.hanco.buildai.net.PlacePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

/**
 * BuildAI placement stays in the normal Minecraft camera (flight and movement
 * are untouched). G freezes the EXACT ghost position currently visible;
 * no raycast, targeted block, or ground selection is required.
 *
 * After G:
 * Arrows LEFT/RIGHT = screen-relative horizontal steps;
 * UP/DOWN = world Y steps;
 * CTRL+LEFT/RIGHT = rotate 90 degrees around the footprint center;
 * CTRL+UP/DOWN or wheel = forward/back relative to heading captured on G;
 * ENTER validates and locks the current XYZ and rotation on the server.
 *
 * R unfreezes/retries. Command-driven designs cannot yet rotate safely.
 */
public final class PlacementMode {
    private PlacementMode(){}
    private static boolean prepared=false, anchored=false,waitingLock=false;
    private static int quarterTurns=0,followOffset=0;
    private static float anchoredYaw=0f;
    private static final int FRONT_GAP=3, MAX_NUDGE=100000;
    public static boolean active(){
        return prepared&&(BuildAIClient.selecting||BuildAIClient.previewLocked);
    }
    public static boolean following(){return active()&&BuildAIClient.selecting&&!anchored;}
    public static int rotation(){return quarterTurns;}
    public static int wheelOffset(){return followOffset;}
    /** Retained API name for existing HUD/client code. G now freezes XYZ, not ground Y. */
    public static boolean groundSelected(){return anchored;}
    public static boolean waitingForLock(){return waitingLock;}
    public static void start(){start(false);}
    public static void inspect(){start(true);}
    private static void start(boolean lockedReview){
        MinecraftClient mc=MinecraftClient.getInstance();
        if(mc.player==null||mc.world==null||BuildAIClient.draft==null)return;
        if(!prepared){
            prepared=true;
            anchored=lockedReview;
            waitingLock=false;
            followOffset=0;
            quarterTurns=Math.floorMod(Math.round(mc.player.getYaw()/90f),4);
            if(!BuildAIClient.draft.commands().isEmpty())quarterTurns=0;
            anchoredYaw=mc.player.getYaw();
        }
        if(lockedReview){
            BuildAIClient.previewLocked=true;
            BuildAIClient.selecting=false;
        }else if(!anchored&&!BuildAIClient.previewLocked){
            BuildAIClient.selecting=true;
            updatePlayerPreview();
        }
        BuildAIClient.message(lockedReview
            ?"Location locked. F8 > Confirm & Build, or R to retry."
            :"Preview follows you. G freezes the current spot, no block targeting. "+
              "Arrows move; CTRL+LEFT/RIGHT rotates; CTRL+UP/DOWN or wheel moves forward/back; ENTER locks.");
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
        switch(Math.floorMod(Math.round(mc.player.getYaw()/90f),4)){
            case 1->{x=player.getX()-FRONT_GAP-w;z=player.getZ()-l/2;}
            case 2->{x=player.getX()-w/2;z=player.getZ()-FRONT_GAP-l;}
            case 3->{x=player.getX()+FRONT_GAP;z=player.getZ()-l/2;}
            default->{x=player.getX()-w/2;z=player.getZ()+FRONT_GAP;}
        }
        PlacementAdjustments.Move f=PlacementAdjustments.forward(mc.player.getYaw());
        BuildAIClient.previewOrigin=new BlockPos(
            x+f.x()*followOffset,
            player.getY()-BuildAIClient.surfaceLayer,
            z+f.z()*followOffset);
    }
    public static void snapGround(){
        MinecraftClient mc=MinecraftClient.getInstance();
        if(!active()||!BuildAIClient.selecting||waitingLock)return;
        if(mc.player==null)return;
        if(BuildAIClient.previewOrigin==null)updatePlayerPreview();
        if(BuildAIClient.previewOrigin==null)return;
        anchoredYaw=mc.player.getYaw();
        anchored=true;
        BuildAIClient.message("Preview FROZEN at "+BuildAIClient.previewOrigin.toShortString()+
            ". Arrows left/right move sideways; up/down change Y; CTRL+arrows rotate/move depth. "+
            "Wheel moves forward/back. ENTER locks, R retries.");
    }
    private static void shift(int x,int y,int z){
        if(BuildAIClient.previewOrigin==null)return;
        BlockPos p=BuildAIClient.previewOrigin;
        long nx=(long)p.getX()+x,ny=(long)p.getY()+y,nz=(long)p.getZ()+z;
        if(Math.abs(nx)>MAX_NUDGE||Math.abs(nz)>MAX_NUDGE||ny< -2048||ny>2048)return;
        BuildAIClient.previewOrigin=new BlockPos((int)nx,(int)ny,(int)nz);
    }
    public static void arrow(int key,boolean ctrl){
        if(!active()||!BuildAIClient.selecting||!anchored||waitingLock)return;
        if(MinecraftClient.getInstance().currentScreen!=null)return;
        if(ctrl&&(key==-1||key==1)){
            rotate(key);
            return;
        }
        PlacementAdjustments.Move delta=PlacementAdjustments.nudge(anchoredYaw,key,ctrl);
        shift(delta.x(),delta.y(),delta.z());
    }
    /** Consume the wheel only during an unlocked preview; preserve hotbar otherwise. */
    public static boolean scroll(double vertical){
        if(!active()||!BuildAIClient.selecting||waitingLock||
            MinecraftClient.getInstance().currentScreen!=null)return false;
        int amount=(int)Math.round(vertical);
        if(amount==0)amount=vertical>0?1:-1;
        amount=Math.max(-16,Math.min(16,amount));
        if(!anchored){
            followOffset=Math.max(-512,Math.min(512,followOffset+amount));
            updatePlayerPreview();
        }else{
            PlacementAdjustments.Move f=PlacementAdjustments.forward(anchoredYaw);
            shift(f.x()*amount,0,f.z()*amount);
        }
        return true;
    }
    private static void rotate(int direction){
        if(!active()||!BuildAIClient.selecting||!anchored||waitingLock)return;
        Blueprint b=BuildAIClient.draft;
        if(b==null)return;
        if(!b.commands().isEmpty()){
            BuildAIClient.message("Command-block maps cannot rotate safely yet: "+
                "their command coordinates and local direction would change.");
            return;
        }
        int oldW=BlueprintRotator.width(b,quarterTurns);
        int oldL=BlueprintRotator.length(b,quarterTurns);
        quarterTurns=Math.floorMod(quarterTurns+direction,4);
        if(BuildAIClient.previewOrigin!=null){
            BlockPos base=BuildAIClient.previewOrigin;
            int w=BlueprintRotator.width(b,quarterTurns),l=BlueprintRotator.length(b,quarterTurns);
            BuildAIClient.previewOrigin=new BlockPos(
                base.getX()+Math.floorDiv(oldW-w,2),base.getY(),
                base.getZ()+Math.floorDiv(oldL-l,2));
        }
    }
    public static void lock(){
        if(!active()||!BuildAIClient.selecting||waitingLock)return;
        if(!anchored){
            BuildAIClient.message("Press G to FREEZE the preview first (no target required), "+
                "adjust with arrow keys, then ENTER to lock.");
            return;
        }
        if(BuildAIClient.previewOrigin==null)return;
        if(!ClientPlayNetworking.canSend(PlacePayload.ID)){
            BuildAIClient.message("Server does not support BuildAI placement.");return;
        }
        waitingLock=true;
        BlockPos proposal=BuildAIClient.previewOrigin.toImmutable();
        ClientPlayNetworking.send(new PlacePayload(proposal,quarterTurns));
        BuildAIClient.message("Checking XYZ "+proposal.toShortString()+" rotation "+
            (quarterTurns*90)+" degrees. Rejected positions stay editable.");
    }
    public static void receive(boolean accepted,boolean locked,BlockPos origin,String message){
        waitingLock=false;
        if(!accepted){
            BuildAIClient.message(message+" Adjust arrows/wheel, then ENTER again.");
            return;
        }
        prepared=true;
        if(locked){
            BuildAIClient.selecting=false;
            BuildAIClient.previewLocked=true;
            anchored=true;
            BuildAIClient.previewOrigin=origin.toImmutable();
            BuildAIClient.message("Position locked! F8 > Confirm & Build, or R to retry.");
        }else{
            anchored=false;
            BuildAIClient.previewLocked=false;
            BuildAIClient.selecting=true;
            updatePlayerPreview();
            BuildAIClient.message("Placement reset; G freezes visible spot. Arrows/wheel edit; ENTER locks.");
        }
    }
    public static void retry(){
        MinecraftClient mc=MinecraftClient.getInstance();
        if(mc.player!=null&&mc.getNetworkHandler()!=null)
            mc.getNetworkHandler().sendChatCommand("buildai retry");
    }
    public static void stop(){
        prepared=false;anchored=false;waitingLock=false;
        quarterTurns=0;followOffset=0;anchoredYaw=0f;
    }
}
