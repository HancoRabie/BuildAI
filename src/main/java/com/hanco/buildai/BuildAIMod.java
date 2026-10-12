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
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
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
    private static int recoverSavedCommands(ServerPlayerEntity player,String fileName) {
        if(!CommandAuthorization.canApprove(player,player.getUuid())){
            reply(player,CommandAuthorization.explanation(player));return 0;
        }
        try{
            String name=fileName.trim();
            if(name.length()<6||name.length()>220||
                !name.matches("[a-zA-Z0-9_.-]+\\.schem")||
                name.contains(".."))
                throw new IllegalArgumentException(
                    "Select one saved .schem filename from F8 > My Blueprints (no folder paths)");
            HitResult target=player.raycast(9.0,0.0f,false);
            if(!(target instanceof BlockHitResult hit)||
                target.getType()!=HitResult.Type.BLOCK)
                throw new IllegalArgumentException(
                    "Look directly at ANY placed command block from the saved build");
            java.nio.file.Path library=net.fabricmc.loader.api.FabricLoader.getInstance()
                .getGameDir().resolve("buildai").resolve("blueprints").toAbsolutePath().normalize();
            java.nio.file.Path saved=library.resolve(name).normalize();
            if(!saved.getParent().equals(library)||!java.nio.file.Files.isRegularFile(saved))
                throw new IllegalArgumentException(
                    "Saved schematic not found in this server's buildai/blueprints folder. "+
                    "Dedicated servers must have the file on the server.");
            Blueprint blueprint=SchematicReader.read(saved);
            int staged=StandaloneCommandReview.stageBlueprint(player,blueprint,hit.getBlockPos());
            reply(player,"RECOVERED "+staged+" command scripts from "+name+
                " and matched EVERY command-block position/facing in the existing world. "+
                "No blocks were changed. Review before approval.");
            if(ServerPlayNetworking.canSend(player,CommandReviewPayload.ID))
                ServerPlayNetworking.send(player,StandaloneCommandReview.snapshot(player));
            return staged;
        }catch(Exception ex){
            reply(player,"Command plan recovery rejected: "+ex.getMessage());return 0;
        }
    }
    private static int stageAtCrosshair(ServerPlayerEntity player,String command){
        if(!CommandAuthorization.canApprove(player,player.getUuid())){
            reply(player,CommandAuthorization.explanation(player));return 0;
        }
        HitResult target=player.raycast(8.0,0.0f,false);
        if(!(target instanceof BlockHitResult hit)||target.getType()!=HitResult.Type.BLOCK){
            reply(player,"Look directly at an existing command block within 8 blocks, then retry.");return 0;
        }
        BlockPos pos=hit.getBlockPos();
        try{
            ConstructionProject project=ConstructionManager.get(player);
            int number;
            boolean sameProject=false;
            if(project!=null&&(project.phase==ConstructionProject.Phase.COMPLETE&&
                project.commandCount()==0 ||project.phase==ConstructionProject.Phase.WAITING_COMMAND_APPROVAL)){
                BlockPos origin=project.origin;
                int x=pos.getX()-origin.getX(),y=pos.getY()-origin.getY(),z=pos.getZ()-origin.getZ();
                sameProject=x>=0&&y>=0&&z>=0&&x<project.blueprint.width&&
                    y<project.blueprint.height&&z<project.blueprint.length&&
                    CommandBlockPlan.isCommandBlock(project.blueprint.get(x,y,z));
            }
            if(sameProject){
                number=project.stageExistingCommand(player,pos,command);
                reply(player,"Assigned command #"+number+" to project block at "+
                    pos.toShortString()+". Physical map stays intact and enters command review.");
            }else{
                number=StandaloneCommandReview.stage(player,pos,command);
                reply(player,"Staged existing world block #"+number+" at "+pos.toShortString()+
                    ". Works even after restarting Minecraft; no rebuild needed.");
                if(ServerPlayNetworking.canSend(player,CommandReviewPayload.ID))
                    ServerPlayNetworking.send(player,StandaloneCommandReview.snapshot(player));
            }
            reply(player,"Review all scripts and approve in the GUI, or type "+
                "/buildai commands approve all (OP4). Scripts remain inactive until approved.");
            return 1;
        }catch(Exception ex){reply(player,"Cannot stage command: "+ex.getMessage());return 0;}
    }
    private static String presetCommand(String choice){
        return switch(choice.toLowerCase(java.util.Locale.ROOT)){
            case "day","daytime"->"time set day";
            case "night","nighttime"->"time set night";
            case "noon"->"time set noon";
            case "midnight"->"time set midnight";
            case "sun","sunshine","clear"->"weather clear";
            case "rain"->"weather rain";
            case "thunder","storm"->"weather thunder";
            case "freeze-day"->"gamerule doDaylightCycle false";
            case "resume-day"->"gamerule doDaylightCycle true";
            case "freeze-weather"->"gamerule doWeatherCycle false";
            case "resume-weather"->"gamerule doWeatherCycle true";
            default->throw new IllegalArgumentException(
                "Use preset day, night, noon, midnight, sun, rain, thunder, freeze-day, or resume-day");
        };
    }
    private static void approvalResult(ServerPlayerEntity player,boolean accepted,String result){
        reply(player,result);
        if(ServerPlayNetworking.canSend(player,CommandApprovalFeedbackPayload.ID))
            ServerPlayNetworking.send(player,new CommandApprovalFeedbackPayload(accepted,result));
    }
    private static int approveSelected(ServerPlayerEntity player,String selection){
        ConstructionProject project=ConstructionManager.get(player);
        try{
            boolean projectPlan=project!=null&&project.phase==
                ConstructionProject.Phase.WAITING_COMMAND_APPROVAL&&project.commandCount()>0;
            int available=projectPlan?project.commandCount():StandaloneCommandReview.count(player);
            if(available==0){
                approvalResult(player,false,
                    "NO SCRIPTS TO APPROVE. Stage a command while looking at a block "+
                    "using /buildai commands preset day, then approve all. No rebuild needed.");
                return 0;
            }
            java.util.Set<Integer> indices="all".equalsIgnoreCase(selection.trim())
                ?CommandApprovalSelection.all(available)
                :CommandApprovalSelection.parse(selection,available);
            int count;
            if(projectPlan){
                project.approveCommands(player,indices);
                count=indices.size();
            }else count=StandaloneCommandReview.approve(player,indices);
            String numbers=indices.stream().sorted().map(i->Integer.toString(i+1))
                .collect(java.util.stream.Collectors.joining(","));
            approvalResult(player,true,"Approved "+count+"/"+available+
                " command blocks (#"+numbers+"). "+(projectPlan?"Commissioning starts now.":
                "Commands were written to existing world blocks.")+
                " Unselected scripts remain inactive.");
            return 1;
        }catch(Exception ex){
            approvalResult(player,false,"Approval rejected: "+ex.getMessage());return 0;
        }
    }
    @Override public void onInitialize() {
        com.hanco.buildai.build.BuildBlocks.register();
        PayloadTypeRegistry.playC2S().register(BlueprintChunkPayload.ID,BlueprintChunkPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PlacePayload.ID,PlacePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CommandApprovalRequestPayload.ID,
            CommandApprovalRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ClearPreviewPayload.ID, ClearPreviewPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BlueprintReadyPayload.ID,BlueprintReadyPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CinematicExcavationPayload.ID,CinematicExcavationPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PlacementReplyPayload.ID,PlacementReplyPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(com.hanco.buildai.net.ProjectStatusPayload.ID,
            com.hanco.buildai.net.ProjectStatusPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CommandReviewPayload.ID,CommandReviewPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CommandApprovalFeedbackPayload.ID,
            CommandApprovalFeedbackPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CommandApprovalRequestPayload.ID,
            (payload,context)->{
                ServerPlayerEntity player=context.player();
                context.server().execute(()->{
                    // A client packet NEVER bypasses ownership, permission,
                    // phase or coordinate checks in approveSelected().
                    approveSelected(player,payload.selection());
                });
            });

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
                        " ("+blueprint.solidCount()+" blocks). Close F8. Press G to freeze the visible preview anywhere; arrows and wheel nudge XYZ, CTRL+left/right rotates, Enter locks. R retries.");
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
                    if(p.phase==ConstructionProject.Phase.ROLLBACK_CHECK||
                        p.phase==ConstructionProject.Phase.ROLLBACK){
                        reply(player,p.blueprint.name+" | "+p.phaseDescription()+
                            ". The operation remains on the server until completed. "+
                            "Missing loaded chunks may pause progress.");
                    }else{
                        reply(player,p.phase+" | "+p.blueprint.name+" | "+p.placed()+"/"+p.total()+
                            " placed | "+p.excavated()+"/"+p.cells()+" cells inspected | "+p.actuallyRemoved()+
                            " removed | "+p.collisions+" collisions | "+p.normalizedPanes()+
                            " pane/fence/wall links | "+p.normalizedRedstone()+
                            " redstone dust links | "+p.refreshedRedstone()+
                            " redstone components notified.");
                    }
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
                        reply(player,"Refreshing this build in stages: pane/fence/wall joins, then redstone wire links and signal neighbors. Watch /buildai status. CAUTION: active machinery may pulse.");
                        return 1;
                    }
                    if(p!=null&&(p.phase==ConstructionProject.Phase.BUILD||
                        p.phase==ConstructionProject.Phase.EXCAVATE||
                        p.phase==ConstructionProject.Phase.CLEANUP||
                        p.phase==ConstructionProject.Phase.NORMALIZE)){
                        reply(player,"Construction still active; automatic pane/fence/wall AND redstone connection/signal repair runs before command approval.");
                        return 0;
                    }
                    int count=PaneNormalizer.normalizeNearby(player.getServerWorld(),player.getBlockPos());
                    reply(player,"Normalized "+count+" pane/fence/wall/redstone wire connection states and refreshed nearby redstone devices (20-block horizontal radius). Note: live circuits may pulse.");
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
                        String msg="Placement reset. Move normally and press G to freeze the preview (no target required). Nudge with arrows/wheel and press Enter to lock XYZ.";
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
                .then(literal("commands").requires(src->src.hasPermissionLevel(2))
                    .then(literal("recover").then(
                        argument("filename",StringArgumentType.greedyString()).executes(c->
                            recoverSavedCommands(c.getSource().getPlayerOrThrow(),
                                StringArgumentType.getString(c,"filename")))))
                    .then(literal("set").requires(src->src.hasPermissionLevel(2))
                        .then(argument("command",StringArgumentType.greedyString()).executes(c->
                            stageAtCrosshair(c.getSource().getPlayerOrThrow(),
                                StringArgumentType.getString(c,"command")))))
                    .then(literal("preset").requires(src->src.hasPermissionLevel(2))
                        .then(argument("choice",StringArgumentType.word()).executes(c->{
                            ServerPlayerEntity player=c.getSource().getPlayerOrThrow();
                            try{return stageAtCrosshair(player,
                                presetCommand(StringArgumentType.getString(c,"choice")));}
                            catch(Exception ex){reply(player,ex.getMessage());return 0;}
                        })))
                    .then(literal("review").executes(c->{
                        ServerPlayerEntity player=c.getSource().getPlayerOrThrow();
                        ConstructionProject project=ConstructionManager.get(player);
                        try{
                            CommandReviewPayload review=project!=null&&
                                project.phase==ConstructionProject.Phase.WAITING_COMMAND_APPROVAL&&
                                project.commandCount()>0
                                    ?CommandReviewPayload.of(project)
                                    :StandaloneCommandReview.snapshot(player);
                            if(ServerPlayNetworking.canSend(player,CommandReviewPayload.ID)){
                                ServerPlayNetworking.send(player,review);
                                reply(player,"Review sent to your Minecraft screen; close other menus to view it.");
                                return 1;
                            }
                            reply(player,"Client doesn't support command review packet.");return 0;
                        }catch(Exception ex){reply(player,"No review available: "+ex.getMessage());return 0;}
                    }))
                    .then(literal("list").executes(c->{
                        ServerPlayerEntity player=c.getSource().getPlayerOrThrow();
                        ConstructionProject p=ConstructionManager.get(player);
                        if(p==null||p.commandCount()==0){
                            int recover=StandaloneCommandReview.count(player);
                            if(recover>0){
                                for(String line:StandaloneCommandReview.list(player).split("\\n"))
                                    reply(player,line);
                            }else{
                                reply(player,"Command plan: 0 scripts. There are NO commands to approve. "+
                                    "Look at the block and use /buildai commands preset day, "+
                                    "preset sun, preset night, preset rain, or "+
                                    "/buildai commands set <actual command>.");
                            }
                            return 1;
                        }
                        reply(player,"Command plan: "+p.commandCount()+" blocks, phase "+p.phase+
                            ", approved "+p.approvedCommandCount()+". All commands remain inert "+
                            "until YOU choose the approved numbers (OP4).");
                        for(int i=0;i<p.commandCount();i++){
                            var block=p.blueprint.commands().get(i);
                            BlockPos at=p.origin.add(block.x(),block.y(),block.z());
                            reply(player,"#"+(i+1)+" local["+block.x()+","+block.y()+","+block.z()+"]"+
                                " world["+at.toShortString()+"] "+
                                (block.auto()?"automatic":"redstone-driven")+" "+
                                block.command().substring(0,Math.min(90,block.command().length())));
                        }
                        if(p.phase==ConstructionProject.Phase.WAITING_COMMAND_APPROVAL)
                            reply(player,"Choose /buildai commands approve all or "+
                                "/buildai commands approve 1,2,4,7. In-game command review opens automatically.");
                        return 1;
                    }))
                    .then(literal("show")
                        .then(argument("number",com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                            .executes(c->{
                                ServerPlayerEntity player=c.getSource().getPlayerOrThrow();
                                ConstructionProject p=ConstructionManager.get(player);
                                if(p==null||p.commandCount()==0){
                                    reply(player,StandaloneCommandReview.list(player));return 1;
                                }
                                int number=com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"number");
                                if(number>p.commandCount()){
                                    reply(player,"Valid command numbers: 1-"+p.commandCount());return 0;
                                }
                                var block=p.blueprint.commands().get(number-1);
                                BlockPos at=p.origin.add(block.x(),block.y(),block.z());
                                reply(player,"#"+number+" local("+block.x()+","+block.y()+","+
                                    block.z()+") => world("+at.toShortString()+") "+
                                    Blueprint.id(p.blueprint.get(block.x(),block.y(),block.z()))+
                                    (block.auto()?" always-active":" redstone-triggered")+
                                    " : "+block.command());
                                return 1;
                            })))
                    .then(literal("approve").requires(src->src.hasPermissionLevel(2))
                        .executes(c->{
                            reply(c.getSource().getPlayerOrThrow(),"Specify 'approve all' or "+
                                "'approve 1,2,4' after the physical map finishes.");return 0;
                        })
                        .then(literal("all").executes(c->
                            approveSelected(c.getSource().getPlayerOrThrow(),"all")))
                        .then(argument("indices",StringArgumentType.greedyString()).executes(c->
                            approveSelected(c.getSource().getPlayerOrThrow(),
                                StringArgumentType.getString(c,"indices")))))
                    // Short form: /buildai commands 1, 2, 4, 7
                    .then(argument("selection",StringArgumentType.greedyString())
                        .requires(src->src.hasPermissionLevel(2)).executes(c->
                            approveSelected(c.getSource().getPlayerOrThrow(),
                                StringArgumentType.getString(c,"selection")))))
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
