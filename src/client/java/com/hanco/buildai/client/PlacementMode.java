package com.hanco.buildai.client;

import com.mojang.authlib.GameProfile;
import com.hanco.buildai.blueprint.Blueprint;
import com.hanco.buildai.net.PlacePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.UUID;

/**
 * Client-only detached placement camera, never inserted into world entity lists.
 * Real player's position, inventory, game mode, collisions and chunks remain authoritative.
 * The moving blueprint is a ghost until a server-reviewed lock and /buildai confirm.
 */
public final class PlacementMode {
    private PlacementMode() {}
    private static final double MAX_CAMERA_DISTANCE=48.0;
    private static OtherClientPlayerEntity camera;
    private static Perspective oldPerspective;
    private static Vec3d startCamera;
    private static BlockPos startOrigin;
    private static int scrollZ;
    private static boolean following=false,waitingLock=false;
    private static boolean active=false;

    public static boolean active(){return active;}
    public static boolean following(){return active && following;}

    public static void start(){start(false);}
    public static void inspect(){start(true);}
    private static void start(boolean lockedReview){
        MinecraftClient mc=MinecraftClient.getInstance();
        Blueprint b=BuildAIClient.draft;
        if(mc.player==null||mc.world==null||b==null)return;
        if(active)stop();
        camera=new OtherClientPlayerEntity(mc.world,
            new GameProfile(UUID.randomUUID(),"BuildAICamera"));
        camera.setPosition(mc.player.getX(),mc.player.getY(),mc.player.getZ());
        camera.setYaw(mc.player.getYaw());
        camera.setPitch(mc.player.getPitch());
        oldPerspective=mc.options.getPerspective();
        mc.options.setPerspective(Perspective.FIRST_PERSON);
        mc.setCameraEntity(camera);
        startCamera=camera.getPos();
        scrollZ=0;
        following=!lockedReview;
        waitingLock=false;
        active=true;
        if(!lockedReview)centerInFront();
        BuildAIClient.selecting=!lockedReview;
        BuildAIClient.surfacePending=false;
        BuildAIClient.previewLocked=lockedReview;
        BuildAIClient.message(lockedReview
            ? "Review camera: WASD/Space/Shift to inspect fixed build; R retries placement; /buildai confirm builds."
            : "Placement: WASD/Space/Shift move ghost, wheel shifts world Z, G snaps ground, Enter locks XYZ, R retries.");
    }
    private static void centerInFront(){
        MinecraftClient mc=MinecraftClient.getInstance();
        Blueprint b=BuildAIClient.draft;
        if(b==null||mc.player==null||camera==null)return;
        double radians=Math.toRadians(mc.player.getYaw());
        double fx=-Math.sin(radians),fz=Math.cos(radians);
        // The build begins 14 blocks in front, not pinned at the crosshair or a clicked block.
        startOrigin=new BlockPos(
            (int)Math.floor(camera.getX()+fx*14-b.width/2.0),
            (int)Math.floor(camera.getY()+1-BuildAIClient.surfaceLayer),
            (int)Math.floor(camera.getZ()+fz*14-b.length/2.0));
        BuildAIClient.previewOrigin=startOrigin;
    }
    public static void scroll(double wheel){
        if(!active||!following||waitingLock)return;
        scrollZ=Math.max(-64,Math.min(64,scrollZ+(wheel>0?1:-1)));
        updateFollowing();
    }
    private static void updateFollowing(){
        if(!following||startCamera==null||startOrigin==null||camera==null)return;
        Vec3d delta=camera.getPos().subtract(startCamera);
        BuildAIClient.previewOrigin=startOrigin.add(
            (int)Math.round(delta.x),(int)Math.round(delta.y),(int)Math.round(delta.z)+scrollZ);
    }
    public static void tick(){
        if(!active)return;
        MinecraftClient mc=MinecraftClient.getInstance();
        if(mc.player==null||mc.world==null||camera==null||mc.player.isDead()){
            stop();BuildAIClient.clearPreview();return;
        }
        // Menus temporarily return to the actual player, leaving the preview at its last XYZ.
        if(mc.currentScreen!=null){stop();return;}
        float yaw=mc.player.getYaw(),pitch=mc.player.getPitch();
        camera.setYaw(yaw);
        camera.setPitch(pitch);
        // Camera uses key state directly; KeyboardInput mixin suppresses real body movement.
        int forward=(mc.options.forwardKey.isPressed()?1:0)-(mc.options.backKey.isPressed()?1:0);
        int strafe=(mc.options.rightKey.isPressed()?1:0)-(mc.options.leftKey.isPressed()?1:0);
        int up=(mc.options.jumpKey.isPressed()?1:0)-(mc.options.sneakKey.isPressed()?1:0);
        double radians=Math.toRadians(yaw);
        double sin=Math.sin(radians),cos=Math.cos(radians);
        Vec3d motion=new Vec3d(-sin*forward+cos*strafe,up,cos*forward+sin*strafe);
        if(motion.lengthSquared()>0){
            double step=mc.options.sprintKey.isPressed()?0.7:0.32;
            Vec3d candidate=camera.getPos().add(motion.normalize().multiply(step));
            // Never load chunks to follow the camera. In Survival this must not be a scouting hack.
            if(candidate.squaredDistanceTo(mc.player.getPos())<=MAX_CAMERA_DISTANCE*MAX_CAMERA_DISTANCE &&
                mc.world.isChunkLoaded(BlockPos.ofFloored(candidate)))
                camera.setPosition(candidate);
        }
        if(following&&!waitingLock)updateFollowing();
    }
    /** Ground selection is editable; aim camera at ground and press G to snap blueprint's surface layer. */
    public static void snapGround(){
        if(!following||!active||waitingLock||camera==null||startOrigin==null)return;
        MinecraftClient mc=MinecraftClient.getInstance();
        if(!(mc.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit)){
            BuildAIClient.message("Aim at a terrain block first, then press G again.");return;
        }
        BlockPos p=BuildAIClient.previewOrigin;
        if(p==null)return;
        int deltaY=hit.getBlockPos().getY()+1-BuildAIClient.surfaceLayer-p.getY();
        startOrigin=startOrigin.up(deltaY);
        updateFollowing();
        BuildAIClient.message("Ground surface snapped to Y="+hit.getBlockPos().getY()+". Fly/scroll or press Enter to lock; R retries.");
    }
    public static void lock(){
        if(!active||!following||waitingLock||BuildAIClient.previewOrigin==null)return;
        if(!ClientPlayNetworking.canSend(PlacePayload.ID)){
            BuildAIClient.message("Server does not support BuildAI placement.");return;
        }
        waitingLock=true;
        BlockPos proposal=BuildAIClient.previewOrigin.toImmutable();
        ClientPlayNetworking.send(new PlacePayload(proposal));
        BuildAIClient.message("Checking proposed location "+proposal.toShortString()+" with server...");
    }
    public static void receive(boolean accepted,boolean locked,BlockPos serverOrigin,String message){
        if(!accepted){waitingLock=false;BuildAIClient.message(message);return;}
        waitingLock=false;
        if(locked){
            following=false;
            BuildAIClient.selecting=false;
            BuildAIClient.previewLocked=true;
            BuildAIClient.surfacePending=false;
            BuildAIClient.previewOrigin=serverOrigin.toImmutable();
            BuildAIClient.message("XYZ LOCKED. Fly to inspect, press R to retry, or leave camera and type /buildai confirm.");
        }else {
            // A retry resumes the moving ghost even if the original camera was closed.
            if(!active)start();
            else {following=true;centerInFront();startCamera=camera.getPos();scrollZ=0;}
            BuildAIClient.selecting=true;
            BuildAIClient.previewLocked=false;
            BuildAIClient.message("Retry ready. Move the preview again. Enter locks the new XYZ.");
        }
    }
    public static void retry(){
        MinecraftClient mc=MinecraftClient.getInstance();
        if(mc.player==null||mc.getNetworkHandler()==null)return;
        mc.getNetworkHandler().sendChatCommand("buildai retry");
    }
    public static void stop(){
        MinecraftClient mc=MinecraftClient.getInstance();
        if(active && mc.player!=null && mc.getCameraEntity()==camera)mc.setCameraEntity(mc.player);
        if(oldPerspective!=null){mc.options.setPerspective(oldPerspective);oldPerspective=null;}
        camera=null;startCamera=null;startOrigin=null;
        active=false;following=false;waitingLock=false;
    }
}
