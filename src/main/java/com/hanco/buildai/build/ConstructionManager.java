package com.hanco.buildai.build;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import com.hanco.buildai.blueprint.*;

/** Initial per-player projects; persistent project journals planned after in-world verification. */
public final class ConstructionManager {
    private static final Map<UUID,ConstructionProject> PROJECTS=new HashMap<>();
    private static final Map<UUID,ConstructionProject.Phase> LAST_ANNOUNCED=new HashMap<>();
    private ConstructionManager(){}
    public static ConstructionProject get(ServerPlayerEntity player){return PROJECTS.get(player.getUuid());}
    public static void begin(ServerPlayerEntity player,Blueprint b,int surfaceLayer){
        if(surfaceLayer<0||surfaceLayer>=b.height)throw new IllegalArgumentException("Invalid local surface level");
        if(PROJECTS.containsKey(player.getUuid())){
            ConstructionProject previous=PROJECTS.get(player.getUuid());
            if(previous.phase!=ConstructionProject.Phase.COMPLETE&&previous.phase!=ConstructionProject.Phase.CANCELLED)
                throw new IllegalStateException("Cancel active project before importing another");
        }
        BlockStates.validate(b);
        PROJECTS.put(player.getUuid(),new ConstructionProject(player,b,surfaceLayer));
        LAST_ANNOUNCED.remove(player.getUuid());
    }
    public static void tick(MinecraftServer server){
        for(ConstructionProject project:new ArrayList<>(PROJECTS.values())) {
            try{
                project.tick();
                ServerPlayerEntity owner=server.getPlayerManager().getPlayer(project.owner);
                if(owner!=null){
                    ConstructionProject.Phase last=LAST_ANNOUNCED.put(project.owner,project.phase);
                    // Announce each genuine transition ONCE (not on every server tick).
                    if(project.phase==ConstructionProject.Phase.WAITING_COMMAND_APPROVAL &&
                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(
                            owner,com.hanco.buildai.net.CommandReviewPayload.ID) &&
                        (last!=project.phase || project.world.getTime()%100==0)){
                        // Re-send occasionally to cover client reconnect; the
                        // client only auto-opens one review per wait episode.
                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                            owner,com.hanco.buildai.net.CommandReviewPayload.of(project));
                    }
                    if(last!=project.phase && (project.phase==ConstructionProject.Phase.FOUNDATION_CLEAR||
                         project.phase==ConstructionProject.Phase.EXCAVATE||
                        project.phase==ConstructionProject.Phase.CINEMATIC_PULL||
                        project.phase==ConstructionProject.Phase.BUILD||
                        project.phase==ConstructionProject.Phase.WAITING_SUPPLIES||
                        project.phase==ConstructionProject.Phase.CLEANUP||
                        project.phase==ConstructionProject.Phase.NORMALIZE||
                        project.phase==ConstructionProject.Phase.WAITING_COMMAND_APPROVAL||
                        project.phase==ConstructionProject.Phase.COMMISSION||
                        project.phase==ConstructionProject.Phase.ROLLBACK_CHECK||
                        project.phase==ConstructionProject.Phase.ROLLBACK||
                        project.phase==ConstructionProject.Phase.ROLLBACK_REFUND||
                        project.phase==ConstructionProject.Phase.CANCELLED||
                        project.phase==ConstructionProject.Phase.COMPLETE||
                        project.phase==ConstructionProject.Phase.PAUSED_ERROR)){
                        owner.sendMessage(net.minecraft.text.Text.literal(
                            "[BuildAI] "+project.phaseDescription()),false);
                    }
                }
                if(owner!=null&&project.world.getTime()%20==0&&
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(owner,
                        com.hanco.buildai.net.ProjectStatusPayload.ID)) {
                    // HUD telemetry is strictly best effort. Serialization failure
                    // must never change the build phase or kick someone mid-build.
                    try{
                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                            owner,project.status());
                    }catch(RuntimeException statusError){
                        System.err.println("[BuildAI] Status update skipped (build continues): "
                            +statusError.getMessage());
                    }
                }
            }catch(RuntimeException e){
                project.disarmCommands();
                project.stopCinematicVisuals();
                // Unexpected failures must never turn into a material-shortage
                // resume, which could skip unfinished excavation and corrupt the build.
                project.phase=ConstructionProject.Phase.PAUSED_ERROR;
                ServerPlayerEntity player=server.getPlayerManager().getPlayer(project.owner);
                if(player!=null)player.sendMessage(net.minecraft.text.Text.literal(
                    "[BuildAI] Construction halted for safety: "+e.getMessage()+
                    ". Use /buildai cancel or /buildai undo after inspecting."),false);
                System.err.println("[BuildAI] Project safely halted: "+e.getMessage());
            }
        }
    }
    public static boolean protectedChest(BlockPos pos,net.minecraft.world.World world){
        for(ConstructionProject project:PROJECTS.values()){
            if(project.world==world&&project.phase!=ConstructionProject.Phase.CANCELLED&&
               project.phase!=ConstructionProject.Phase.COMPLETE&&project.protectedChest(pos))return true;
        }
        return false;
    }
}
