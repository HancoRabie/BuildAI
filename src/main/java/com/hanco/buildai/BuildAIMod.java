package com.hanco.buildai;

import com.google.gson.*;
import com.hanco.buildai.blueprint.*;
import com.hanco.buildai.build.*;
import com.hanco.buildai.net.*;
import com.mojang.brigadier.Command;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.command.CommandManager;

import static net.minecraft.server.command.CommandManager.literal;

/** Server-authoritative world mutation. The local UI only sends blueprint proposals. */
public final class BuildAIMod implements ModInitializer {
    private static final UploadAssembler UPLOADS=new UploadAssembler();
    private static void reply(ServerPlayerEntity p,String msg){p.sendMessage(Text.literal("[BuildAI] "+msg),false);}
    @Override public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(BlueprintChunkPayload.ID,BlueprintChunkPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PlacePayload.ID,PlacePayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(BlueprintChunkPayload.ID,(payload,context)->{
            ServerPlayerEntity player=context.player();
            // v0.1 restricts world modification to operators to protect unknown player builds.
            if(!player.hasPermissionLevel(2)) {reply(player,"Operator permission required in initial release.");return;}
            try {
                String response=UPLOADS.accept(player.getUuid(),payload);
                if(response!=null) {
                    int newLine=response.indexOf('\n'),surface=Integer.parseInt(response.substring(0,newLine));
                    Blueprint blueprint=BlueprintJson.decode(JsonParser.parseString(response.substring(newLine+1)).getAsJsonObject());
                    context.server().execute(()->{
                        try {
                            ConstructionManager.begin(player,blueprint,surface);
                            reply(player,"Blueprint loaded: "+blueprint.name+" ("+blueprint.solidCount()+
                                " blocks). Left-click desired placement location, then sneak-right-click surface block.");
                        }catch(Exception e){reply(player,"Blueprint rejected: "+e.getMessage());}
                    });
                }
            }catch(Exception e){reply(player,"Upload error: "+e.getMessage());}
        });
        ServerPlayNetworking.registerGlobalReceiver(PlacePayload.ID,(payload,context)->{
            ServerPlayerEntity player=context.player();
            context.server().execute(()->{
                ConstructionProject p=ConstructionManager.get(player);
                if(p==null||!player.hasPermissionLevel(2))return;
                if(p.phase!=ConstructionProject.Phase.SELECT_XZ)return;
                BlockPos pos=payload.pos();
                if(player.squaredDistanceTo(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>64*64) {
                    reply(player,"Placement point is too far away.");return;
                }
                p.selectXZ(pos);reply(player,"X/Z locked. Sneak-right-click the exact terrain surface block.");
            });
        });
        UseBlockCallback.EVENT.register((player,world,hand,hit)->{
            if(world.isClient()||!(player instanceof ServerPlayerEntity server)||!server.hasPermissionLevel(2))
                return ActionResult.PASS;
            ConstructionProject project=ConstructionManager.get(server);
            if(project==null||project.phase!=ConstructionProject.Phase.SELECT_SURFACE||!player.isSneaking())
                return ActionResult.PASS;
            try {
                project.selectSurface(hit.getBlockPos());
                reply(server,"Surface aligned; "+project.collisions+
                    " existing blocks in construction volume. Inspect site; /buildai confirm to excavate and build.");
            }catch(Exception e){reply(server,"Cannot position: "+e.getMessage());}
            return ActionResult.SUCCESS;
        });
        PlayerBlockBreakEvents.BEFORE.register((world,player,pos,state,entity)->{
            if(ConstructionManager.protectedChest(pos,world)) {
                player.sendMessage(Text.literal("[BuildAI] Supply chest is protected while project is active."),true);
                return false;
            }
            return true;
        });
        ServerTickEvents.END_SERVER_TICK.register(ConstructionManager::tick);
        CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->
            dispatcher.register(literal("buildai")
                .then(literal("status").executes(c->{
                    ServerPlayerEntity player=c.getSource().getPlayerOrThrow();
                    ConstructionProject p=ConstructionManager.get(player);
                    if(p==null){reply(player,"No project. Open BuildAI GUI to generate/import.");return 1;}
                    reply(player,p.phase+" | "+p.blueprint.name+" | "+p.placed()+"/"+p.total()+
                        " placed | "+p.excavated()+"/"+p.cells()+" excavation cells | "+p.collisions+" collisions.");
                    return 1;
                }))
                .then(literal("materials").executes(c->{
                    ServerPlayerEntity player=c.getSource().getPlayerOrThrow();
                    ConstructionProject p=ConstructionManager.get(player);
                    if(p==null){reply(player,"No project");return 0;}
                    for(var e:p.requirements().entrySet())
                        reply(player,e.getKey()+": "+p.consumed().getOrDefault(e.getKey(),0)+" used / "+e.getValue()+" needed");
                    return 1;
                }))
                .then(literal("confirm").requires(s->s.hasPermissionLevel(2)).executes(c->{
                    ServerPlayerEntity player=c.getSource().getPlayerOrThrow();
                    ConstructionProject p=ConstructionManager.get(player);
                    if(p==null){reply(player,"No project");return 0;}
                    try{p.confirm();reply(player,"Construction approved. Excavation and progressive building started.");return 1;}
                    catch(Exception e){reply(player,"Cannot confirm: "+e.getMessage());return 0;}
                }))
                .then(literal("cancel").requires(s->s.hasPermissionLevel(2)).executes(c->{
                    ServerPlayerEntity player=c.getSource().getPlayerOrThrow();
                    ConstructionProject p=ConstructionManager.get(player);
                    if(p==null){reply(player,"No project");return 0;}
                    try{p.cancel(false);reply(player,"Project stopped; partial build retained.");return 1;}
                    catch(Exception e){reply(player,"Cancel failed: "+e.getMessage());return 0;}
                }))
                .then(literal("undo").requires(s->s.hasPermissionLevel(2)).executes(c->{
                    ServerPlayerEntity player=c.getSource().getPlayerOrThrow();
                    ConstructionProject p=ConstructionManager.get(player);
                    if(p==null){reply(player,"No project");return 0;}
                    try{p.cancel(true);reply(player,"Blockstate rollback completed. Used materials are NOT automatically refunded in v0.1.");return 1;}
                    catch(Exception e){reply(player,"Undo failed: "+e.getMessage());return 0;}
                }))
            )
        );
    }
}
