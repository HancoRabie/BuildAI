package com.hanco.buildai.build;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import com.hanco.buildai.blueprint.*;

/** Initial per-player projects; persistent project journals planned after in-world verification. */
public final class ConstructionManager {
    private static final Map<UUID,ConstructionProject> PROJECTS=new HashMap<>();
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
    }
    public static void tick(MinecraftServer server){
        for(ConstructionProject project:new ArrayList<>(PROJECTS.values())) {
            try{
                project.tick();
                ServerPlayerEntity owner=server.getPlayerManager().getPlayer(project.owner);
                if(owner!=null&&project.world.getTime()%20==0&&
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(owner,
                        com.hanco.buildai.net.ProjectStatusPayload.ID))
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(owner,project.status());
            }catch(RuntimeException e){
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
