package com.hanco.buildai.build;

import com.hanco.buildai.blueprint.CommandBlockPlan;
import com.hanco.buildai.net.CommandReviewPayload;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.CommandBlockBlockEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/**
 * Operator-only recovery for pre-existing world command blocks whose original
 * BuildAI project is no longer in memory (e.g. game restart). There is no
 * destructive replacement and no generated AI code fabricated by this tool.
 * Exact world XYZ and expected block-state identity persist in this session;
 * approved command text then persists naturally in Minecraft's world save.
 */
public final class StandaloneCommandReview {
    private StandaloneCommandReview(){}
    private record Assignment(BlockPos pos,BlockState block,String command){}
    private record Pending(ServerWorld world,List<Assignment> entries){}
    private static final Map<UUID,Pending> STAGED=new HashMap<>();

    private static void requireOperator(ServerPlayerEntity player){
        if(!CommandAuthorization.canApprove(player,player.getUuid()))
            throw new IllegalStateException(CommandAuthorization.explanation(player));
    }
    private static CommandBlockBlockEntity inspect(ServerWorld world,BlockPos pos,BlockState expected){
        if(!world.isChunkLoaded(pos))
            throw new IllegalStateException("Target chunk is not loaded");
        if(!world.getBlockState(pos).equals(expected))
            throw new IllegalStateException("Command block type/orientation changed: "+pos.toShortString());
        if(!(world.getBlockEntity(pos) instanceof CommandBlockBlockEntity tile))
            throw new IllegalStateException("Not a command block at "+pos.toShortString());
        return tile;
    }
    public static synchronized int stage(ServerPlayerEntity player,BlockPos pos,String command){
        requireOperator(player);
        CommandBlockPlan.validateCommand(command);
        ServerWorld world=player.getServerWorld();
        BlockState state=world.getBlockState(pos);
        String id=Registries.BLOCK.getId(state.getBlock()).toString();
        if(!CommandBlockPlan.isCommandBlock(id))
            throw new IllegalArgumentException("Look directly at an existing impulse, chain or repeating command block");
        var tile=inspect(world,pos,state);
        if(!tile.getCommandExecutor().getCommand().isBlank())
            throw new IllegalStateException("This block already contains a command. BuildAI will not overwrite it automatically");
        Pending pending=STAGED.get(player.getUuid());
        List<Assignment> entries=new ArrayList<>();
        if(pending!=null&&pending.world()==world)entries.addAll(pending.entries());
        entries.removeIf(e->e.pos().equals(pos));
        entries.add(new Assignment(pos.toImmutable(),state,command.trim()));
        entries.sort(Comparator.comparingInt((Assignment e)->e.pos().getY())
            .thenComparingInt(e->e.pos().getZ())
            .thenComparingInt(e->e.pos().getX()));
        if(entries.size()>CommandBlockPlan.MAX_COMMANDS)
            throw new IllegalStateException("Maximum "+CommandBlockPlan.MAX_COMMANDS+" staged scripts");
        STAGED.put(player.getUuid(),new Pending(world,List.copyOf(entries)));
        for(int i=0;i<entries.size();i++)
            if(entries.get(i).pos().equals(pos))return i+1;
        throw new IllegalStateException("Unable to record command position");
    }
    /**
     * Recover a WHOLE saved command plan after a restart, without rebuilding.
     * Aim at any command block belonging to that schematic. We test every
     * possible matching local anchor and require one UNIQUE origin for which
     * ALL command-block type/facing states match in loaded world chunks.
     * No commands are programmed during matching.
     */
    public static synchronized int stageBlueprint(ServerPlayerEntity player,
                                                  com.hanco.buildai.blueprint.Blueprint blueprint,
                                                  BlockPos aimedAt) {
        requireOperator(player);
        if(blueprint.commands().isEmpty())
            throw new IllegalArgumentException("Saved schematic contains no command plan to recover");
        ServerWorld world=player.getServerWorld();
        if(!world.isChunkLoaded(aimedAt)||
            !CommandBlockPlan.isCommandBlock(Registries.BLOCK.getId(
                world.getBlockState(aimedAt).getBlock()).toString()))
            throw new IllegalArgumentException("Look at any command block that belongs to this saved build");
        List<List<Assignment>> matches=new ArrayList<>();
        BlockState clicked=world.getBlockState(aimedAt);
        for(var candidate:blueprint.commands()){
            BlockState template=com.hanco.buildai.blueprint.BlockStates.parse(
                blueprint.get(candidate.x(),candidate.y(),candidate.z()));
            if(!clicked.equals(template))continue;
            BlockPos origin=aimedAt.subtract(new BlockPos(candidate.x(),candidate.y(),candidate.z()));
            List<Assignment> assignments=new ArrayList<>();
            boolean valid=true;
            for(var e:blueprint.commands()){
                BlockPos at=origin.add(e.x(),e.y(),e.z());
                if(!world.isChunkLoaded(at) ||
                    !CommandBlockPlan.isCommandBlock(Registries.BLOCK.getId(
                        world.getBlockState(at).getBlock()).toString()) ||
                    !world.getBlockState(at).equals(
                        com.hanco.buildai.blueprint.BlockStates.parse(
                            blueprint.get(e.x(),e.y(),e.z())))){
                    valid=false;break;
                }
                if(!(world.getBlockEntity(at) instanceof CommandBlockBlockEntity tile) ||
                    !tile.getCommandExecutor().getCommand().isBlank()){
                    valid=false;break;
                }
                CommandBlockPlan.validateCommand(e.command());
                assignments.add(new Assignment(at.toImmutable(),world.getBlockState(at),e.command()));
            }
            if(valid)matches.add(assignments);
            if(matches.size()>1)break;
        }
        if(matches.isEmpty())
            throw new IllegalArgumentException(
                "Could not match ALL saved command blocks at the aimed location. "+
                "Stand near the build so all chunks are loaded; make sure it is the "+
                "same unrotated schematic and its command blocks are still empty.");
        if(matches.size()>1)
            throw new IllegalArgumentException(
                "Multiple possible alignments found; aim at a different command block "+
                "whose type/facing makes this build unambiguous.");
        STAGED.put(player.getUuid(),new Pending(world,List.copyOf(matches.get(0))));
        return matches.get(0).size();
    }

    public static synchronized int count(ServerPlayerEntity player){
        Pending p=STAGED.get(player.getUuid());
        return p!=null&&p.world()==player.getServerWorld()?p.entries().size():0;
    }
    public static synchronized String list(ServerPlayerEntity player){
        Pending p=STAGED.get(player.getUuid());
        if(p==null||p.world()!=player.getServerWorld()||p.entries().isEmpty())
            return "No command instructions are staged. Look at a block and use /buildai commands preset day, or /buildai commands set <command>.";
        StringBuilder out=new StringBuilder("RECOVERY COMMAND PLAN: "+p.entries().size()+" script(s) awaiting OP4 approval.\n");
        for(int i=0;i<p.entries().size();i++){
            var e=p.entries().get(i);
            out.append("#").append(i+1).append(" at ").append(e.pos().toShortString())
                .append(" -> ").append(e.command()).append("\n");
        }
        out.append("Approve all or selected #s. They do NOT run before approval.");
        return out.toString();
    }
    public static synchronized CommandReviewPayload snapshot(ServerPlayerEntity player){
        Pending p=STAGED.get(player.getUuid());
        if(p==null||p.world()!=player.getServerWorld()||p.entries().isEmpty())
            throw new IllegalStateException("No staged scripts to review");
        BlockPos first=p.entries().get(0).pos();
        List<CommandReviewPayload.Item> values=new ArrayList<>();
        for(int i=0;i<p.entries().size();i++){
            var entry=p.entries().get(i);
            values.add(new CommandReviewPayload.Item(i+1,entry.pos().subtract(first),
                Registries.BLOCK.getId(entry.block().getBlock()).toString(),
                entry.command(),false,false));
        }
        return new CommandReviewPayload("Existing Command Blocks — Recovery",first,values);
    }
    public static synchronized int approve(ServerPlayerEntity player,Set<Integer> selected){
        requireOperator(player);
        Pending p=STAGED.get(player.getUuid());
        if(p==null||p.world()!=player.getServerWorld()||p.entries().isEmpty())
            throw new IllegalStateException("No staged scripts exist");
        if(!p.world().getServer().areCommandBlocksEnabled())
            throw new IllegalStateException("Enable command blocks in server.properties and restart the server");
        if(selected==null||selected.isEmpty())throw new IllegalArgumentException("Select at least one command");
        // Preflight everything before changing any command block.
        for(int i:selected){
            if(i<0||i>=p.entries().size())throw new IllegalArgumentException("Invalid command #"+(i+1));
            Assignment entry=p.entries().get(i);
            var tile=inspect(p.world(),entry.pos(),entry.block());
            if(!tile.getCommandExecutor().getCommand().isBlank())
                throw new IllegalStateException("Command block changed at "+entry.pos().toShortString());
        }
        int completed=0;
        for(int i:selected){
            Assignment e=p.entries().get(i);
            var tile=inspect(p.world(),e.pos(),e.block());
            tile.setAuto(false); // safe default, buttons/redstone required
            tile.getCommandExecutor().setCommand(e.command());
            tile.getCommandExecutor().setTrackOutput(false);
            tile.markDirty();
            p.world().updateListeners(e.pos(),e.block(),e.block(),Block.NOTIFY_LISTENERS);
            completed++;
        }
        STAGED.remove(player.getUuid());
        return completed;
    }
}
