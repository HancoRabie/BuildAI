package com.hanco.buildai;

import com.google.gson.*;
import com.hanco.buildai.blueprint.*;
import com.hanco.buildai.build.*;
import com.hanco.buildai.net.*;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import static net.minecraft.server.command.CommandManager.argument;
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
    private static void uploadResult(ServerPlayerEntity p,boolean ok,String message){
        if(ServerPlayNetworking.canSend(p,BlueprintReadyPayload.ID))
            ServerPlayNetworking.send(p,new BlueprintReadyPayload(ok,message.length()>240?message.substring(0,240):message));
        reply(p,message);
    }
    public static void clearGhost(ServerPlayerEntity player) {
        if(ServerPlayNetworking.canSend(player,ClearPreviewPayload.ID))
            ServerPlayNetworking.send(player,new ClearPreviewPayload());
    }
    @Override public void onInitialize() {
        com.hanco.buildai.build.BuildBlocks.register();
        PayloadTypeRegistry.playC2S().register(BlueprintChunkPayload.ID,BlueprintChunkPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PlacePayload.ID,PlacePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ClearPreviewPayload.ID, ClearPreviewPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BlueprintReadyPayload.ID,BlueprintReadyPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CinematicExcavationPayload.ID,CinematicExcavationPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PlacementReplyPayload.ID,PlacementReplyPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(com.hanco.buildai.net.ProjectStatusPayload.ID,
            com.hanco.buildai.net.ProjectStatusPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(BlueprintChunkPayload.ID,(payload,context)->{
            ServerPlayerEntity player=context.player();
            context.server().execute(()->{
                if(!player.hasPermissionLevel(2)){
                    uploadResult(player,false,"Operator permission required to upload a building.");return;
                }
                try{
                    String response=UPLOADS.accept(player.getUuid(),payload);
                    if(response==null)return; // multipart upload is still in progress
                    int newLine=response.indexOf('\n');
                    if(newLine<1)throw new IllegalArgumentException("Missing blueprint surface layer");
                    int surface=Integer.parseInt(response.substring(0,newLine));
                    Blueprint blueprint=BlueprintJson.decode(JsonParser.parseString(
                        response.substring(newLine+1)).getAsJsonObject());
                    ConstructionManager.begin(player,blueprint,surface);
                    uploadResult(player,true,"Blueprint accepted: "+blueprint.name+
                        " ("+blueprint.solidCount()+" blocks). Close F8. Move normally, aim at the ground and press G, then Enter to lock; R retries.");
                }catch(Exception e){
                    uploadResult(player,false,"Blueprint rejected: "+String.valueOf(e.getMessage()));
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(PlacePayload.ID,(payload,context)->{
            ServerPlayerEntity player=context.player();
            context.server().execute(()->{
                ConstructionProject p=ConstructionManager.get(player);
                if(p==null||!player.hasPermissionLevel(2))return;
                BlockPos candidate=payload.pos();
                try {
                    // Placement always uses the normal player camera; the server is authoritative
                    // for reach, world limits and protected-block validation.
                    int turn=payload.quarterTurns();
                    if(turn<0||turn>3)throw new IllegalArgumentException("Invalid blueprint rotation");
                    double cx=candidate.getX()+BlueprintRotator.width(p.blueprint,turn)/2.0;
                    double cy=candidate.getY()+p.blueprint.height/2.0;
                    double cz=candidate.getZ()+BlueprintRotator.length(p.blueprint,turn)/2.0;
                    if(player.squaredDistanceTo(cx,cy,cz)>100*100)
                        throw new IllegalArgumentException("Move the proposal closer to your real character (100 block limit)");
                    p.selectOrigin(candidate,turn);
                    String msg="Placement locked at "+candidate.toShortString()+" (rotation "+(turn*90)+"°); "+p.collisions+
                        " existing blocks overlap. Inspect normally, /buildai retry, or /buildai confirm.";
                    if(ServerPlayNetworking.canSend(player,PlacementReplyPayload.ID))
                        ServerPlayNetworking.send(player,new PlacementReplyPayload(true,true,msg,p.origin));
                    reply(player,msg);
                }catch(Exception e){
                    String msg="Placement not accepted: "+e.getMessage();
                    if(ServerPlayNetworking.canSend(player,PlacementReplyPayload.ID))
                        ServerPlayNetworking.send(player,new PlacementReplyPayload(false,false,msg,p.origin));
                    reply(player,msg);
                }
            });
        });
        UseBlockCallback.EVENT.register((player,world,hand,hit)->{
            if(world.isClient()||!(player instanceof ServerPlayerEntity server)||!server.hasPermissionLevel(2))
                return ActionResult.PASS;
            ConstructionProject project=ConstructionManager.get(server);
            if(project==null||!player.isSneaking())return ActionResult.PASS;
            try {
                if(world.getBlockEntity(hit.getBlockPos()) instanceof net.minecraft.inventory.Inventory) {
                    project.linkChest(hit.getBlockPos());
                    reply(server,"BuildAI supply chest linked and protected. /buildai materials shows progress.");
                    return ActionResult.SUCCESS;
                }
            }catch(Exception e){reply(server,"Could not select: "+e.getMessage());return ActionResult.SUCCESS;}
            return ActionResult.PASS;
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
                        " placed | "+p.excavated()+"/"+p.cells()+" cells inspected | "+p.actuallyRemoved()+
                        " removed | "+p.collisions+" collisions | "+p.normalizedPanes()+" fence/pane/wall connections updated.");
                    return 1;
                }))
                .then(literal("materials").executes(c->{
                    ServerPlayerEntity player=c.getSource().getPlayerOrThrow();
                    ConstructionProject p=ConstructionManager.get(player);
                    if(p==null){reply(player,"No project");return 0;}
                    java.util.Map<String,Integer> stored=p.stored();
                    for(var e:p.requirements().entrySet()) {
                        int used=p.consumed().getOrDefault(e.getKey(),0);
                        int available=stored.getOrDefault(e.getKey(),0);
                        reply(player,e.getKey()+": "+used+" placed + "+available+" available / "+e.getValue()+" required");
                    }
                    return 1;
                }))
                .then(literal("substitute").requires(s->s.hasPermissionLevel(2))
                    .then(argument("mapping",StringArgumentType.greedyString()).executes(c->{
                        ServerPlayerEntity player=c.getSource().getPlayerOrThrow();
                        ConstructionProject p=ConstructionManager.get(player);
                        if(p==null){reply(player,"No active blueprint");return 0;}
                        String[] parts=StringArgumentType.getString(c,"mapping").trim().split("\\s+");
                        if(parts.length!=2){reply(player,"Usage: /buildai substitute minecraft:stone minecraft:cobblestone");return 0;}
                        try{
                            p.substitute(parts[0],parts[1]);
                            reply(player,"Approved replacement: "+parts[0]+" -> "+parts[1]);
                            return 1;
                        }catch(Exception e){reply(player,"Replacement rejected: "+e.getMessage());return 0;}
                    })))
                .then(literal("cinematic").requires(s->s.hasPermissionLevel(2)).executes(c->{
                    ServerPlayerEntity player=c.getSource().getPlayerOrThrow();
                    ConstructionProject p=ConstructionManager.get(player);
                    if(p==null){reply(player,"No project");return 0;}
                    p.setCinematic(true);reply(player,"Cinematic enabled. The site is visually masked while excavated, then a black-hole suction animation plays before building.");return 1;
                }))
                .then(literal("normalize").requires(s->s.hasPermissionLevel(2)).executes(c->{
                    ServerPlayerEntity player=c.getSource().getPlayerOrThrow();
                    ConstructionProject p=ConstructionManager.get(player);
                    if(p!=null&&p.requestPaneRepair()){
                        reply(player,"Normalizing finished fence rails, walls, glass panes and iron bars. Watch /buildai status.");
                        return 1;
                    }
                    if(p!=null&&(p.phase==ConstructionProject.Phase.BUILD||
                        p.phase==ConstructionProject.Phase.EXCAVATE||
                        p.phase==ConstructionProject.Phase.CLEANUP||
                        p.phase==ConstructionProject.Phase.NORMALIZE)){
                        reply(player,"The build is still active; automatic fence/wall/pane connection repair runs on completion.");
                        return 0;
                    }
                    int count=PaneNormalizer.normalizeNearby(player.getServerWorld(),player.getBlockPos());
                    reply(player,"Repaired "+count+" fence/wall/pane/iron-bar block states near you (20 block horizontal radius).");
                    return 1;
                }))
                .then(literal("fast").requires(s->s.hasPermissionLevel(2)).executes(c->{
                    ServerPlayerEntity player=c.getSource().getPlayerOrThrow();
                    ConstructionProject p=ConstructionManager.get(player);
                    if(p==null){reply(player,"No project");return 0;}
                    p.setCinematic(false);reply(player,"Fast placement (256 blocks/tick) enabled.");return 1;
                }))
                .then(literal("retry").requires(s->s.hasPermissionLevel(2)).executes(c->{
                    ServerPlayerEntity player=c.getSource().getPlayerOrThrow();
                    ConstructionProject project=ConstructionManager.get(player);
                    if(project==null){reply(player,"No blueprint loaded; upload one in F8.");return 0;}
                    try {
                        project.retryPlacement();
                        String msg="Placement reset. Move normally, point at the new ground block and press G, then Enter to lock XYZ.";
                        if(ServerPlayNetworking.canSend(player,PlacementReplyPayload.ID))
                            ServerPlayNetworking.send(player,new PlacementReplyPayload(true,false,msg,BlockPos.ORIGIN));
                        reply(player,msg);
                        return 1;
                    }catch(Exception e){reply(player,"Retry unavailable: "+e.getMessage());return 0;}
                }))
                .then(literal("hide").executes(c->{
                    ServerPlayerEntity player=c.getSource().getPlayerOrThrow();
                    clearGhost(player);
                    reply(player,"Placement preview hidden. Blueprint and built blocks unchanged.");
                    return 1;
                }))
                .then(literal("commands").requires(s->s.hasPermissionLevel(2))
                    .then(literal("list").executes(c->{
                        ServerPlayerEntity player=c.getSource().getPlayerOrThrow();
                        ConstructionProject p=ConstructionManager.get(player);
                        if(p==null){reply(player,"No blueprint loaded.");return 0;}
                        int size=p.commandCount();
                        reply(player,"Command plan: "+size+" blocks | "+
                            (p.commandsApproved()?"approved":"NOT APPROVED")+
                            " | stable physical build order by Y/Z/X, local XYZ -> locked world XYZ. "+
                            "Review and approve as operator-level 4.");
                        for(int i=0;i<Math.min(10,size);i++){
                            var block=p.blueprint.commands().get(i);
                            BlockPos at=p.origin.add(block.x(),block.y(),block.z());
                            reply(player,"#"+(i+1)+" local["+block.x()+","+block.y()+","+block.z()+"]"+
                                " world["+at.toShortString()+"] "+
                                (block.auto()?"always-active":"redstone-driven")+"  "+
                                block.command().substring(0,Math.min(100,block.command().length())));
                        }
                        if(size>10)reply(player,"More commands: /buildai commands show <index> (0.."+(size-1)+"). "+
                                "Or review the paged in-game GUI in F8 > Placement / Export > Design QA.");
                        return 1;
                    }))
                    .then(literal("show")
                        .then(argument("index",com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                            .executes(c->{
                                ServerPlayerEntity player=c.getSource().getPlayerOrThrow();
                                ConstructionProject p=ConstructionManager.get(player);
                                if(p==null){reply(player,"No blueprint.");return 0;}
                                int index=com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"index");
                                if(index>=p.commandCount()){reply(player,"No command at index "+index);return 0;}
                                var block=p.blueprint.commands().get(index);
                                BlockPos at=p.origin.add(block.x(),block.y(),block.z());
                                reply(player,"#"+(index+1)+" local("+block.x()+","+block.y()+","+
                                    block.z()+") => world("+at.toShortString()+") "+
                                    Blueprint.id(p.blueprint.get(block.x(),block.y(),block.z()))+" "+
                                    (block.auto()?"always-active":"requires redstone")+
                                    ": "+block.command());
                                return 1;
                            })))
                    .then(literal("approve").requires(src->src.hasPermissionLevel(4)).executes(c->{
                        ServerPlayerEntity player=c.getSource().getPlayerOrThrow();
                        ConstructionProject p=ConstructionManager.get(player);
                        if(p==null){reply(player,"No blueprint.");return 0;}
                        try{
                            p.approveCommands(player);
                            reply(player,"Approved "+p.commandCount()+
                                " command-block instructions. Confirm build to begin; scripts install "+
                                "only AFTER construction and normalization. Review full commands before approving.");
                            return 1;
                        }catch(Exception ex){reply(player,"Command approval rejected: "+ex.getMessage());return 0;}
                    })))
                .then(literal("confirm").requires(s->s.hasPermissionLevel(2)).executes(c->{
                    ServerPlayerEntity player=c.getSource().getPlayerOrThrow();
                    ConstructionProject p=ConstructionManager.get(player);
                    if(p==null){reply(player,"No project");return 0;}
                    try{p.confirm();clearGhost(player);reply(player,
                        p.cinematic?"Construction approved. Preparing cinematic excavation; the site will clear together, then blocks spiral into a black hole before construction.":
                            "Construction approved. Fast excavation and progressive building started.");return 1;}
                    catch(Exception e){reply(player,"Cannot confirm: "+e.getMessage());return 0;}
                }))
                .then(literal("cancel").requires(s->s.hasPermissionLevel(2)).executes(c->{
                    ServerPlayerEntity player=c.getSource().getPlayerOrThrow();
                    ConstructionProject p=ConstructionManager.get(player);
                    if(p==null){reply(player,"No project");return 0;}
                    try{p.cancel(false);clearGhost(player);reply(player,"Project stopped; partial build retained.");return 1;}
                    catch(Exception e){reply(player,"Cancel failed: "+e.getMessage());return 0;}
                }))
                .then(literal("undo").requires(src->src.hasPermissionLevel(2))
                    .executes(c->{
                        ServerPlayerEntity player=c.getSource().getPlayerOrThrow();
                        ConstructionProject p=ConstructionManager.get(player);
                        if(p==null){reply(player,"No project to undo.");return 0;}
                        try{
                            int changed=p.requestUndo(false);
                            clearGhost(player);
                            reply(player,"Safe undo started for "+changed+
                                " positions. Checking for protected block entities first; "+
                                "restoration runs in batches. Follow /buildai status. Materials are not refunded.");
                            return 1;
                        }catch(Exception e){reply(player,"Undo unavailable: "+e.getMessage());return 0;}
                    })
                    .then(literal("force").requires(src->src.hasPermissionLevel(4)).executes(c->{
                        ServerPlayerEntity player=c.getSource().getPlayerOrThrow();
                        ConstructionProject p=ConstructionManager.get(player);
                        if(p==null){reply(player,"No project to undo.");return 0;}
                        try{
                            int changed=p.requestUndo(true);
                            clearGhost(player);
                            reply(player,"FORCE UNDO started on "+changed+
                                " positions. WARNING: new containers, command scripts and edits "+
                                "inside this construction area may be destroyed; bedrock is still protected. "+
                                "Follow /buildai status. Materials and inventory contents are NOT refunded.");
                            return 1;
                        }catch(Exception e){reply(player,"Force undo unavailable: "+e.getMessage());return 0;}
                    })))
            )
        );
    }
}
