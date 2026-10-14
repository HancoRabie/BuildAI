package com.hanco.buildai.build;

import com.hanco.buildai.blueprint.*;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;

/**
 * Tick-limited server-authoritative construction. No drops from excavation.
 * This first version deliberately refuses excavating block entities and bedrock.
 */
public final class ConstructionProject {
    public enum Phase { SELECT_XZ, SELECT_SURFACE, REVIEW, EXCAVATE, BUILD, WAITING_SUPPLIES, CLEANUP, NORMALIZE, WAITING_COMMAND_APPROVAL, COMMISSION, CINEMATIC_PULL, COMPLETE, CANCELLED, ROLLBACK_CHECK, ROLLBACK, ROLLBACK_REFUND, PAUSED_ERROR }
    public Blueprint blueprint;
    private final Blueprint sourceBlueprint;
    private int rotation=0;
    public final UUID owner;
    public final boolean creative;
    public final int surfaceLayer;
    public final ServerWorld world;
    public Phase phase=Phase.SELECT_XZ;
    public BlockPos origin=BlockPos.ORIGIN;
    private List<BlockPos> order;
    private ConstructionOptions constructionOptions=ConstructionOptions.DEFAULT;
    private int omittedFoundationBlocks=0;
    private String foundationScanSummary="not requested";
    public String foundationScanSummary(){return foundationScanSummary;}
    public int omittedFoundationBlocks(){return omittedFoundationBlocks;}
    private Set<Integer> foundationOmissions=Set.of();
    /** Human-readable audit of the ACTUAL blueprint blocks intentionally
     * withheld by foundation:no, including every selected Y layer. */
    public String foundationOmissionSummary(){
        if(foundationOmissions.isEmpty())return "none recognized; "+foundationScanSummary;
        Map<String,Integer> types=new HashMap<>();
        int minY=Integer.MAX_VALUE,maxY=-1;
        for(int i:foundationOmissions){
            int x=i%blueprint.width,z=(i/blueprint.width)%blueprint.length,
                y=i/(blueprint.width*blueprint.length);
            minY=Math.min(minY,y);maxY=Math.max(maxY,y);
            String id=Blueprint.id(blueprint.get(x,y,z));
            types.merge(id.startsWith("minecraft:")?
                id.substring("minecraft:".length()):id,1,Integer::sum);
        }
        String materials=types.entrySet().stream()
            .sorted(Comparator.<Map.Entry<String,Integer>>comparingInt(
                Map.Entry::getValue).reversed())
            .limit(5).map(e->e.getKey()+" x"+e.getValue())
            .reduce((a,b)->a+", "+b).orElse("");
        return "Y "+minY+".."+maxY+"; "+materials+
            (types.size()>5?" (+"+(types.size()-5)+" more)":"")+
            "; "+foundationScanSummary;
    }
    private BitSet hollowInterior=new BitSet();
    // Every approved blueprint AIR voxel must still be AIR after the shell
    // has been built, not merely after preliminary excavation.
    private int interiorVerifyIndex=0, interiorVerificationClears=0;
    private boolean interiorVerified=true;
    private int finalAirAuditIndex=0,finalAirAuditPass=0;
    private int finalAirAuditMismatches=0,finalAirAuditRepairs=0;
    private int finalSolidAuditIndex=-1,finalSolidAuditRepairs=0;
    public int finalAirAuditRepairs(){return finalAirAuditRepairs;}
    public int finalSolidAuditRepairs(){return finalSolidAuditRepairs;}
    public int hollowInteriorCells(){return hollowInterior.cardinality();}
    public ConstructionOptions constructionOptions(){return constructionOptions;}
    private boolean plannedSupport(int x,int y,int z){
        return !blueprint.isAir(x,y,z) &&
            !foundationOmissions.contains(blueprint.index(x,y,z));
    }
    private int excavationIndex=0,removedCount=0,constructionIndex=0,normalizeIndex=0,normalizedPanes=0;
    private int normalizePass=0,normalizedRedstone=0,refreshedRedstone=0;
    private boolean manualNormalize=false;
    private boolean waterSourceFunded=false;
    private long lastFluidRetry=-100;
    private final Map<BlockPos,BlockState> originals=new LinkedHashMap<>();
    private final Set<BlockPos> modified=new HashSet<>();
    private final Set<BlockPos> supports=new LinkedHashSet<>();
    private final List<BlockPos> excavationSamples=new ArrayList<>(192);
    private final List<String> excavationSampleIds=new ArrayList<>(192);
    private int sampledRemovalCount=0,blackHoleTicks=0;
    private static final int BLACK_HOLE_TICKS=80;
    private java.util.Iterator<BlockPos> cleanupIterator;
    private final Map<String,Integer> consumed=new TreeMap<>();
    private final SupplyLedger supplyLedger=new SupplyLedger();
    private String refundHoldReason="";
    private int refundedSupplies=0;
    private final Map<String,String> substitutions=new LinkedHashMap<>();
    private final List<BlockPos> supplies=new ArrayList<>();
    private final Set<BlockPos> linkedChests=new LinkedHashSet<>();
    private long lastChestScan=0;
    private boolean hasSnapshot=false;
    private List<BlockPos> rollbackOrder=List.of();
    private int rollbackCheckIndex=0,rollbackIndex=0;
    private boolean rollbackOverride=false,rollbackFinished=false;
    public int rollbackProgress(){return rollbackIndex;}
    public int rollbackSize(){return rollbackOrder.size();}
    private boolean commandsApproved=false;
    private Set<Integer> approvedCommandIndices=Set.of();
    private int programmingIndex=0,armingIndex=0;
    private Map<Integer,SignTextPlan.Entry> signLabels=Map.of();
    private void refreshSignLabels(){
        Map<Integer,SignTextPlan.Entry> labels=new HashMap<>();
        for(SignTextPlan.Entry e:blueprint.signs())
            labels.put(blueprint.index(e.x(),e.y(),e.z()),e);
        signLabels=Map.copyOf(labels);
    }
    public int commandCount(){return blueprint.commands().size();}
    public boolean commandsApproved(){return commandsApproved;}
    public int approvedCommandCount(){return approvedCommandIndices.size();}
    public boolean isCommandApproved(int zeroBased){return approvedCommandIndices.contains(zeroBased);}
    /**
     * Recover finished physical builds for which the AI omitted command
     * metadata. This is an explicit, operator-authored map of an existing
     * command-block cell, not a fabricated script or new world placement.
     * Physical project remains intact; normal review/commissioning resumes.
     */
    public int stageExistingCommand(ServerPlayerEntity operator,BlockPos target,String script){
        if(!CommandAuthorization.canApprove(operator,owner))
            throw new IllegalStateException(CommandAuthorization.explanation(operator));
        if(!(phase==Phase.COMPLETE && commandCount()==0) &&
            phase!=Phase.WAITING_COMMAND_APPROVAL)
            throw new IllegalStateException("Configure commands on an existing completed build, "+
                "or while the command review is waiting; current phase: "+phase);
        if(commandsApproved)
            throw new IllegalStateException("This command selection was already approved and commissioned");
        if(!world.isChunkLoaded(target))
            throw new IllegalStateException("Command block chunk is not loaded");
        int x=target.getX()-origin.getX(),y=target.getY()-origin.getY(),z=target.getZ()-origin.getZ();
        if(x<0||y<0||z<0||x>=blueprint.width||y>=blueprint.height||z>=blueprint.length)
            throw new IllegalArgumentException("Look at a command block that BuildAI placed in this project");
        if(!CommandBlockPlan.isCommandBlock(blueprint.get(x,y,z)) ||
            !CommandBlockPlan.isCommandBlock(Registries.BLOCK.getId(world.getBlockState(target).getBlock()).toString()))
            throw new IllegalArgumentException("Target is not a BuildAI-generated command block");
        if(!world.getBlockState(target).equals(BlockStates.parse(blueprint.get(x,y,z))))
            throw new IllegalArgumentException("Command block type/facing changed since the blueprint was built");
        CommandBlockPlan.Entry entry=new CommandBlockPlan.Entry(x,y,z,script.trim(),false,false);
        List<CommandBlockPlan.Entry> next=new ArrayList<>(blueprint.commands());
        next.removeIf(old->old.x()==x&&old.y()==y&&old.z()==z);
        next.add(entry);
        blueprint.setCommands(next); // unique XYZ, deterministic build order
        approvedCommandIndices=Set.of();commandsApproved=false;
        programmingIndex=0;armingIndex=0;
        phase=Phase.WAITING_COMMAND_APPROVAL;
        return blueprint.commands().indexOf(entry)+1;
    }

    /**
     * In no case does build speed advance approval. The finished physical
     * build stops in WAITING_COMMAND_APPROVAL until an OP4 explicitly chooses
     * all or specific command indices. Unselected blocks remain inert.
     */
    public void approveCommands(ServerPlayerEntity administrator,Set<Integer> selected){
        if(phase!=Phase.WAITING_COMMAND_APPROVAL)
            throw new IllegalStateException("Wait until construction finishes and BuildAI opens command review");
        if(!CommandAuthorization.canApprove(administrator,owner))
            throw new IllegalStateException(CommandAuthorization.explanation(administrator));
        if(!world.getServer().areCommandBlocksEnabled())
            throw new IllegalStateException("Command blocks are disabled: set enable-command-block=true in server.properties and restart");
        if(selected==null||selected.isEmpty())
            throw new IllegalArgumentException("Select at least one command or cancel the project");
        for(int i:selected){
            if(i<0||i>=commandCount())throw new IllegalArgumentException("Invalid command index");
            CommandBlockPlan.validate(blueprint,blueprint.commands().get(i));
        }
        // Recheck every block before approving ANY scripts. Fail closed if
        // players have modified the map during their review.
        for(int i:selected){
            var e=blueprint.commands().get(i);
            BlockPos pos=origin.add(e.x(),e.y(),e.z());
            if(!world.isChunkLoaded(pos) ||
               !CommandBlockPlan.isCommandBlock(
                   Registries.BLOCK.getId(world.getBlockState(pos).getBlock()).toString()) ||
               !world.getBlockState(pos).equals(BlockStates.parse(blueprint.get(e.x(),e.y(),e.z()))))
                throw new IllegalStateException("Command #"+(i+1)+" at "+pos.toShortString()+
                    " differs from the approved design (expected "+
                    blueprint.get(e.x(),e.y(),e.z())+", actual "+
                    world.getBlockState(pos)+"). Inspect/rebuild that cell before approval.");
        }
        approvedCommandIndices=Set.copyOf(selected);
        commandsApproved=true;
        programmingIndex=0;armingIndex=0;
        phase=Phase.COMMISSION;
    }
    public int collisions=0;
    public int constructionSpeed=16;
    public boolean cinematic=true;
    public void setCinematic(boolean enabled) {
        if(cinematic!=enabled){
            if(phase==Phase.EXCAVATE && collisions>0)
                excavationCue(enabled
                    ?com.hanco.buildai.net.CinematicExcavationPayload.PREPARE
                    :com.hanco.buildai.net.CinematicExcavationPayload.STOP,0);
            if(phase==Phase.CINEMATIC_PULL && !enabled){
                excavationCue(com.hanco.buildai.net.CinematicExcavationPayload.STOP,0);
                phase=Phase.BUILD;
            }
        }
        cinematic=enabled;
        constructionSpeed=enabled?16:256;
    }
    public void stopCinematicVisuals(){
        excavationCue(com.hanco.buildai.net.CinematicExcavationPayload.STOP,0);
    }

    public ConstructionProject(ServerPlayerEntity player,Blueprint blueprint,int surfaceLayer) {
        this.blueprint=blueprint;this.sourceBlueprint=blueprint;this.owner=player.getUuid();this.world=player.getServerWorld();
        this.creative=player.isCreative();
        this.surfaceLayer=surfaceLayer;
        this.order=blueprint.ordered();
        refreshSignLabels();
    }
    public void retryPlacement() {
        if (phase!=Phase.SELECT_XZ && phase!=Phase.SELECT_SURFACE && phase!=Phase.REVIEW)
            throw new IllegalStateException("Cannot reposition after excavation has begun");
        blueprint=sourceBlueprint;order=sourceBlueprint.ordered();rotation=0;
        refreshSignLabels();
        origin=BlockPos.ORIGIN;collisions=0;phase=Phase.SELECT_XZ;
        constructionOptions=ConstructionOptions.DEFAULT;
        foundationOmissions=Set.of();omittedFoundationBlocks=0;
        foundationScanSummary="not requested";
        hollowInterior=new BitSet();
        interiorVerifyIndex=0; interiorVerificationClears=0; interiorVerified=true;
        finalAirAuditIndex=0;finalAirAuditPass=0;
        finalAirAuditMismatches=0;finalAirAuditRepairs=0;
        finalSolidAuditIndex=-1;finalSolidAuditRepairs=0;
        commandsApproved=false;approvedCommandIndices=Set.of();programmingIndex=0;armingIndex=0;
    }
    /** Preview is free to move client-side; only this method locks a server-approved XYZ origin. */
    public void selectOrigin(BlockPos proposal,int quarterTurns) {
        if(phase!=Phase.SELECT_XZ && phase!=Phase.SELECT_SURFACE && phase!=Phase.REVIEW)
            throw new IllegalStateException("Placement cannot change once construction begins");
        if(quarterTurns<0||quarterTurns>3)
            throw new IllegalArgumentException("Invalid 90-degree rotation");
        // Build a separate candidate so a failed lock never mutates the active
        // blueprint, even if the intended footprint changes from W×L to L×W.
        Blueprint candidate=BlueprintRotator.rotate(sourceBlueprint,quarterTurns);
        commandsApproved=false;approvedCommandIndices=Set.of();
        int count=0;
        // Validate all cells first. A failed selection must never overwrite the prior approved preview.
        for(int y=0;y<candidate.height;y++)for(int z=0;z<candidate.length;z++)for(int x=0;x<candidate.width;x++){
            BlockPos pos=proposal.add(x,y,z);
            if(!world.isInBuildLimit(pos)||!world.isChunkLoaded(pos))
                throw new IllegalArgumentException("Entire blueprint must be inside loaded world limits");
            BlockState previous=world.getBlockState(pos);
            if(!previous.isAir())count++;
            // This is a PREVIEW: confirmation options are chosen later.
            // Overlay mode may preserve untouched terrain (including a chest
            // in a blueprint AIR cell). The selected mode is safety-scanned
            // again at CONFIRM, before any actual mutation occurs.
        }
        blueprint=candidate;
        refreshSignLabels();
        order=candidate.ordered();
        rotation=quarterTurns;
        origin=proposal.toImmutable();
        collisions=count;
        phase=Phase.REVIEW;
    }
    public void confirm(){
        confirm(ConstructionOptions.DEFAULT);
    }
    /**
     * Preflight the ACTUAL write-set before starting. Full excavation clears
     * every cell in the selected volume. Overlay writes only actual
     * non-air blueprint cells AFTER sanitizing selected foundation positions
     * from the placement order. In overlay mode, foundation:no does NOT
     * excavate existing world blocks. Both modes must refuse protected
     * inventory/bedrock ONLY at positions that they actually modify.
     */
    public void confirm(ConstructionOptions requested){
        if(phase!=Phase.REVIEW)throw new IllegalStateException(
            "Placement is not locked. Close F8, freeze/adjust the preview, "+
            "press ENTER, then /buildai confirm foundation:no excavate:no.");
        Objects.requireNonNull(requested,"Confirmation flags");
        // Calculate the foundation omissions FIRST. Suppressed podium
        // blocks must be invisible to ALL interior ray scans (X, Y, Z).
        // The actual original schematic remains unmodified; only original
        // AIR positions may enter the hollow excavation/verification mask.
        FoundationPlan.Analysis foundationAnalysis=requested.foundation()
            ?null:FoundationPlan.analyze(blueprint,requested.foundationMaterials());
        Set<Integer> skip=foundationAnalysis==null?Set.of():foundationAnalysis.omissions();
        if(foundationAnalysis!=null&&skip.isEmpty())
            throw new IllegalStateException(
                "foundation:no could not recognize any qualifying foundation "+
                "blocks; construction NOT started. "+
                foundationAnalysis.summary()+". "+
                "Check the actual bottom schematic layers and block IDs. "+
                "Only >=90%-occupied connected footprint layers with one eligible block ID >=51% "+
                "qualify. Rebuild with foundation:yes if you want to keep it.");
        BitSet interior=requested.hollow()
            ?InteriorHollowPlan.enclosedAir(blueprint,skip):new BitSet();
        if(requested.hollow()&&interior.isEmpty())
            throw new IllegalStateException(
                "hollow:yes found no reliably bounded interior. No world "+
                "blocks were modified. Add structural enclosure, inspect "+
                "the schematic air volume, or choose an explicit mode.");
        List<BlockPos> selectedOrder=new ArrayList<>();
        for(BlockPos local:blueprint.ordered())
            if(!skip.contains(blueprint.index(local.getX(),local.getY(),local.getZ())))
                selectedOrder.add(local);
        if(selectedOrder.isEmpty())
            throw new IllegalStateException("No blueprint blocks remain after "+
                "foundation selection. Construction was not started.");

        if(requested.excavate()){
            // Only full clearing requires checking blueprint AIR voxels too.
            for(int y=0;y<blueprint.height;y++)for(int z=0;z<blueprint.length;z++)
                for(int x=0;x<blueprint.width;x++){
                    BlockPos at=origin.add(x,y,z);
                    if(!world.isChunkLoaded(at))
                        throw new IllegalStateException("Site contains unloaded chunks");
                    BlockState previous=world.getBlockState(at);
                    if(previous.hasBlockEntity()||previous.isOf(Blocks.BEDROCK))
                        throw new IllegalStateException("Protected block in excavation "+
                            "volume at "+at.toShortString()+
                            ". Use excavate:no if that cell is outside the painted blocks.");
                }
        }else{
            // Hollow mode preflights BOTH independent write sets, BEFORE
            // placing or excavating anything. A protected interior inventory
            // must reject the ENTIRE job instead of disappearing mid-build.
            if(requested.hollow())for(int i=interior.nextSetBit(0);i>=0;
                                      i=interior.nextSetBit(i+1)){
                int x=i%blueprint.width,z=(i/blueprint.width)%blueprint.length,
                    y=i/(blueprint.width*blueprint.length);
                BlockPos at=origin.add(x,y,z);
                if(!world.isChunkLoaded(at))
                    throw new IllegalStateException("Interior contains unloaded chunks");
                BlockState previous=world.getBlockState(at);
                if(previous.hasBlockEntity()||previous.isOf(Blocks.BEDROCK))
                    throw new IllegalStateException("Protected block inside the "+
                        "planned hollow interior at "+at.toShortString()+
                        ". No excavation started.");
            }
            for(BlockPos local:selectedOrder){
                BlockPos at=origin.add(local);
                if(!world.isChunkLoaded(at))
                    throw new IllegalStateException("Site contains unloaded chunks");
                BlockState previous=world.getBlockState(at);
                if(previous.hasBlockEntity()||previous.isOf(Blocks.BEDROCK))
                    throw new IllegalStateException("Protected block overlaps "+
                        "painted blueprint at "+at.toShortString()+
                        ". Overlay/hollow mode cannot replace protected blocks.");
            }
        }
        constructionOptions=requested;
        foundationOmissions=skip;
        hollowInterior=interior;
        interiorVerifyIndex=0; interiorVerificationClears=0;
        finalAirAuditIndex=0;finalAirAuditPass=0;
        finalAirAuditMismatches=0;finalAirAuditRepairs=0;
        finalSolidAuditIndex=-1;finalSolidAuditRepairs=0;
        interiorVerified=!requested.hollow();
        omittedFoundationBlocks=skip.size();
        foundationScanSummary=foundationAnalysis==null?
            "foundation kept":foundationAnalysis.summary();
        order=List.copyOf(selectedOrder);
        // Command approval is intentionally AFTER physical construction,
        // never a pre-confirm race for small, fast-build projects.
        // Original states are recorded lazily when ACTUALLY modified.
        hasSnapshot=true;
        excavationSamples.clear();excavationSampleIds.clear();
        sampledRemovalCount=0;removedCount=0;blackHoleTicks=0;
        excavationIndex=0;
        // foundation:no modifies ONLY which schematic blocks are placed,
        // never the pre-existing world. Excavation requires excavate:yes
        // or an EXPLICIT hollow:yes request.
        if(requested.excavate()||requested.hollow()){
            phase=Phase.EXCAVATE;
            // Never use the full-site excavation cinematic in hollow mode:
            // exterior terrain is supposed to remain visible and intact.
            if(cinematic&&!requested.hollow()&&collisions>0)excavationCue(
                com.hanco.buildai.net.CinematicExcavationPayload.PREPARE,0);
        }else phase=Phase.BUILD;
    }
    private void excavationCue(int stage,int duration){
        net.minecraft.server.network.ServerPlayerEntity player=
            world.getServer().getPlayerManager().getPlayer(owner);
        if(player!=null&&net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(
            player,com.hanco.buildai.net.CinematicExcavationPayload.ID)){
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new com.hanco.buildai.net.CinematicExcavationPayload(
                    stage,origin,blueprint.width,blueprint.height,blueprint.length,
                    stage==com.hanco.buildai.net.CinematicExcavationPayload.PULL?
                        List.copyOf(excavationSamples):List.of(),
                    stage==com.hanco.buildai.net.CinematicExcavationPayload.PULL?
                        List.copyOf(excavationSampleIds):List.of(),duration));
        }
    }
    private void finishRollback(){
        rollbackFinished=true;
        phase=Phase.CANCELLED;
        modified.clear();originals.clear();rollbackOrder=List.of();
        hasSnapshot=false;
        consumed.clear();
    }
    private void recordOriginal(BlockPos position) {
        BlockPos key=position.toImmutable();
        BlockState previous=world.getBlockState(position);
        // Every attempted mutation is checked, including cells excavated earlier
        // and revisited during placement. Only accepted edits enter the undo set.
        if(previous.hasBlockEntity()||previous.isOf(Blocks.BEDROCK))
            throw new IllegalStateException("Protected block appeared during construction at "+
                position.toShortString()+". Project halted before altering it.");
        if(!modified.add(key))return;
        if(!previous.isAir())originals.put(key,previous);
    }
    /**
     * Block IDs are always authoritative. Also require the schematic's
     * stable geometry/orientation properties; ignore transient redstone
     * powers and dynamic connection shapes updated by Minecraft itself.
     * In particular, a TOP slab is not AIR above a bottom slab: it is a
     * single block with an exact slab-type state.
     */
    private static boolean essentialPropertiesMatch(BlockState actual,BlockState intended){
        if(actual.getBlock()!=intended.getBlock())return false;
        for(var property:intended.getProperties()){
            String key=property.getName();
            // Slab type is exact geometry. Chest "type" (single/left/
            // right) is a dynamic neighbor connection and must not be
            // forced back to a standalone chest on each audit.
            boolean stable=(key.equals("type")&&
                intended.getBlock() instanceof SlabBlock)||
                key.equals("half")||key.equals("facing")||
                key.equals("axis")||key.equals("vertical_direction")||
                key.equals("hinge")||key.equals("rotation")||key.equals("part");
            if(stable){
                if(!Objects.equals(actual.get(property),intended.get(property)))
                    return false;
            }
        }
        return true;
    }
    private void beginNormalization(boolean manualRepair) {
        normalizeIndex=0;
        normalizePass=0;
        interiorVerifyIndex=0;
        interiorVerified=!constructionOptions.hollow()||manualRepair;
        finalAirAuditIndex=0;finalAirAuditPass=0;
        finalAirAuditMismatches=0;finalAirAuditRepairs=0;
        finalSolidAuditIndex=-1;finalSolidAuditRepairs=0;
        normalizedPanes=0;
        normalizedRedstone=0;
        refreshedRedstone=0;
        manualNormalize=manualRepair;
        phase=Phase.NORMALIZE;
    }

    /** Re-run cosmetic and redstone normalization on the finished footprint.
     * Crucially, repairing an already commissioned project must NOT reopen
     * command approval or silently clear its approved command set.
     */
    public boolean requestPaneRepair() {
        if(phase!=Phase.COMPLETE)return false;
        beginNormalization(true);
        return true;
    }
    public int normalizedPanes() { return normalizedPanes; }
    public int normalizedRedstone() { return normalizedRedstone; }
    public int refreshedRedstone() { return refreshedRedstone; }
    public void tick() {
        // Construction may pause while obtaining the one-time renewable
        // water charge or a lava bucket. Resume the FLUID normalization pass,
        // never restart BUILD/CLEANUP or bill the same fluid twice.
        if(phase==Phase.WAITING_SUPPLIES && normalizePass==2 &&
           constructionIndex>=order.size()){
            if(world.getTime()-lastFluidRetry<20)return;
            lastFluidRetry=world.getTime();
            phase=Phase.NORMALIZE;
        }
        if(phase==Phase.ROLLBACK_CHECK){
            int scanned=0;
            while(rollbackCheckIndex<rollbackOrder.size()&&scanned++<4096){
                BlockPos pos=rollbackOrder.get(rollbackCheckIndex);
                if(!world.isChunkLoaded(pos))return;
                verifyUndoTarget(pos,rollbackOverride);
                rollbackCheckIndex++;
            }
            if(rollbackCheckIndex>=rollbackOrder.size())phase=Phase.ROLLBACK;
            return;
        }
        if(phase==Phase.ROLLBACK){
            int changed=0;
            while(rollbackIndex<rollbackOrder.size()&&changed++<2048){
                BlockPos pos=rollbackOrder.get(rollbackIndex);
                if(!world.isChunkLoaded(pos))return;
                // Repeat safety check: players may interact with the site
                // while a large project is being reversed.
                verifyUndoTarget(pos,rollbackOverride);
                BlockState previous=originals.getOrDefault(pos,Blocks.AIR.getDefaultState());
                world.setBlockState(pos,previous,Block.NOTIFY_ALL|Block.FORCE_STATE);
                rollbackIndex++;
            }
            if(rollbackIndex>=rollbackOrder.size()){
                // The physical world is restored before a supply refund.
                // The receipt is NOT erased until all original chests have
                // room for their complete, exact-source refund.
                if(!creative&&!supplyLedger.isEmpty()){
                    phase=Phase.ROLLBACK_REFUND;
                }else finishRollback();
            }
            return;
        }
        if(phase==Phase.ROLLBACK_REFUND){
            if(world.getTime()%20!=0)return;
            SupplyLedger.Refund result=supplyLedger.refund(pos->
                world.isChunkLoaded(pos)&&
                world.getBlockEntity(pos) instanceof Inventory inv ? inv:null);
            if(!result.success()){
                refundHoldReason=result.message();
                return;
            }
            refundedSupplies+=result.returned();
            refundHoldReason="";
            finishRollback();
            return;
        }
        if(phase==Phase.CINEMATIC_PULL){
            // Absolutely no construction before the cleared-site cinematic ends.
            if(++blackHoleTicks>=BLACK_HOLE_TICKS)phase=Phase.BUILD;
            return;
        }
        if(phase==Phase.NORMALIZE) {
            // FINAL TWO-SIDED RECONCILIATION:
            // (A) expected schematic interior AIR is really AIR, and
            // (B) EVERY non-omitted schematic building block still exists.
            // The former handles jungle terrain; the latter catches missing
            // stalagmites, slabs, pots, pistons and decorations. Each pass
            // visits both complete sets before deciding whether to retry.
            if(normalizePass>=4&&!manualNormalize&&constructionOptions.hollow()){
                int inspected=0;
                if(finalSolidAuditIndex<0){
                    while(inspected++<4096){
                        int i=hollowInterior.nextSetBit(finalAirAuditIndex);
                        if(i<0){finalSolidAuditIndex=0;break;}
                        int x=i%blueprint.width,z=(i/blueprint.width)%blueprint.length;
                        int y=i/(blueprint.width*blueprint.length);
                        if(!blueprint.isAir(x,y,z))
                            throw new IllegalStateException(
                                "Interior AIR mask includes a schematic design block at "+
                                x+","+y+","+z);
                        BlockPos at=origin.add(x,y,z);
                        if(!world.isChunkLoaded(at))return;
                        if(!world.getBlockState(at).isAir()){
                            finalAirAuditMismatches++;
                            recordOriginal(at);
                            world.setBlockState(at,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
                            finalAirAuditRepairs++;
                        }
                        finalAirAuditIndex=i+1;
                    }
                    return;
                }
                while(finalSolidAuditIndex<order.size()&&inspected++<4096){
                    BlockPos local=order.get(finalSolidAuditIndex);
                    BlockPos at=origin.add(local);
                    if(!world.isChunkLoaded(at))return;
                    // Includes every schematic item, whether or not it was
                    // deemed structural by the AIR-classification heuristic.
                    BlockState desired=BlockStates.parse(effective(local));
                    BlockState actual=world.getBlockState(at);
                    if(!essentialPropertiesMatch(actual,desired)){
                        finalAirAuditMismatches++;
                        recordOriginal(at);
                        world.setBlockState(at,desired,Block.NOTIFY_ALL|Block.FORCE_STATE);
                        finalSolidAuditRepairs++;
                    }
                    finalSolidAuditIndex++;
                }
                if(finalSolidAuditIndex<order.size())return;
                if(finalAirAuditMismatches!=0){
                    if(finalAirAuditPass>=2)
                        throw new IllegalStateException(
                            "World differs from expected schematic after 3 "+
                            "repair passes. Last pass repaired "+
                            finalAirAuditMismatches+" AIR/structure cells. "+
                            "Check unsupported dripstone, plants, slabs and "+
                            "foundation:no support removal. Project paused.");
                    finalAirAuditPass++;
                    finalAirAuditMismatches=0;
                    finalAirAuditIndex=0;
                    finalSolidAuditIndex=-1;
                    return;
                }
                phase=commandCount()>0?
                    Phase.WAITING_COMMAND_APPROVAL:Phase.COMPLETE;
                return;
            }
            // The blueprint's intended ROOM/CORRIDOR AIR is a real end-state,
            // not merely a preliminary excavation attempt. In particular,
            // no terrain, fallen foliage or construction-side updates may
            // remain inside after the physical shell is placed.
            // These are *exactly* the preflight-approved interior AIR voxels.
            if(!interiorVerified){
                int scanned=0, volume=blueprint.width*blueprint.length*blueprint.height;
                while(scanned++<4096){
                    int i=hollowInterior.nextSetBit(interiorVerifyIndex);
                    if(i<0){interiorVerified=true;break;}
                    int x=i%blueprint.width,z=(i/blueprint.width)%blueprint.length;
                    int y=i/(blueprint.width*blueprint.length);
                    if(!blueprint.isAir(x,y,z))
                        throw new IllegalStateException("Hollow mask attempts to clear actual "+
                            "blueprint block at "+x+","+y+","+z);
                    BlockPos at=origin.add(x,y,z);
                    if(!world.isChunkLoaded(at))return;
                    BlockState existing=world.getBlockState(at);
                    if(!existing.isAir()){
                        // Respect protected containers/bedrock even if someone
                        // modifies the site after the initial preflight.
                        recordOriginal(at);
                        world.setBlockState(at,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
                        interiorVerificationClears++;
                    }
                    interiorVerifyIndex=i+1;
                }
                if(!interiorVerified)return;
                // Done. No second unbounded pass or model request required.
            }
            int scanned=0;
            // Four bounded passes: decorative connections, redstone dust,
            // FLUIDS (with survival material charge), then power signals.
            // Manual repair never consumes buckets or re-places fluid.
            while(normalizeIndex<order.size() && scanned++<256) {
                BlockPos local=order.get(normalizeIndex);
                BlockPos pos=origin.add(local);
                if(!world.isChunkLoaded(pos))return;
                if(normalizePass==0){
                    if(PaneNormalizer.normalize(world,pos))normalizedPanes++;
                }else if(normalizePass==1){
                    BlockState state=world.getBlockState(pos);
                    if(RedstoneNormalizer.isDust(state)){
                        if(!RedstoneNormalizer.neighborsLoaded(world,pos))return;
                        if(RedstoneNormalizer.normalizeDust(world,pos))normalizedRedstone++;
                    }
                }else if(normalizePass==2){
                    String source=effective(local);
                    if(!manualNormalize && BuildFluidRules.isFluidSource(source)){
                        // Do NOT consume or place unless all required water
                        // starter buckets are present. Single survival water
                        // charge gives access to renewable source water; lava
                        // is explicitly charged once PER placed lava source.
                        if(!creative && BuildFluidRules.isWater(source) && !waterSourceFunded){
                            if(!consumeWaterStarterBuckets()){
                                phase=Phase.WAITING_SUPPLIES;return;
                            }
                            waterSourceFunded=true;
                        }else if(!creative && BuildFluidRules.isLava(source)){
                            if(!consumeFluidBucket(Items.LAVA_BUCKET)){
                                phase=Phase.WAITING_SUPPLIES;return;
                            }
                        }
                        BlockState fluid=BlockStates.parse(source);
                        recordOriginal(pos);
                        world.setBlockState(pos,fluid,Block.NOTIFY_ALL|Block.FORCE_STATE);
                    }
                }else{
                    BlockState state=world.getBlockState(pos);
                    if(RedstoneNormalizer.isSignalDevice(state)){
                        if(!RedstoneNormalizer.neighborsLoaded(world,pos))return;
                        if(RedstoneNormalizer.refreshSignal(world,pos))refreshedRedstone++;
                    }
                    // Programming labels is a separate inert post-placement
                    // step, not a geometry blockstate or command execution.
                    // Skip manual redstone renormalization so player-edited
                    // sign text is not overwritten by maintenance actions.
                    SignTextPlan.Entry label=signLabels.get(
                        blueprint.index(local.getX(),local.getY(),local.getZ()));
                    if(label!=null&&!manualNormalize&&
                       !SignInstaller.install(world,pos,label))
                        throw new IllegalStateException("Planned sign at "+
                            pos.toShortString()+" is missing or is not a sign");
                }
                normalizeIndex++;
            }
            if(normalizeIndex>=order.size()){
                normalizeIndex=0;
                normalizePass++;
                if(normalizePass>=4){
                    if(manualNormalize||!constructionOptions.hollow())
                        phase=manualNormalize?Phase.COMPLETE:
                            (commandCount()>0?Phase.WAITING_COMMAND_APPROVAL:Phase.COMPLETE);
                    manualNormalize=false;
                }
            }
            return;
        }
        if(phase==Phase.WAITING_COMMAND_APPROVAL){
            // No timeout; no automatic approval and no command programming.
            return;
        }
        if(phase==Phase.COMMISSION){
            if(!commandsApproved||!world.getServer().areCommandBlocksEnabled())
                throw new IllegalStateException("Command installation not authorized or disabled in server settings");
            int work=0;
            List<CommandBlockPlan.Entry> programs=blueprint.commands();
            while(programmingIndex<programs.size()&&work++<12){
                CommandBlockPlan.Entry e=programs.get(programmingIndex);
                // The immutable local XYZ -> world XYZ translation, and block
                // type/facing, are rechecked before programming each ordinal.
                if(approvedCommandIndices.contains(programmingIndex))
                    CommandBlockInstaller.program(world,origin.add(e.x(),e.y(),e.z()),e,
                        blueprint.get(e.x(),e.y(),e.z()));
                else
                    CommandBlockInstaller.keepInert(world,origin.add(e.x(),e.y(),e.z()),
                        blueprint.get(e.x(),e.y(),e.z()));
                programmingIndex++;
            }
            // Do not arm ANY blocks until all command text has been installed.
            if(programmingIndex<programs.size())return;
            work=0;
            while(armingIndex<programs.size()&&work++<12){
                CommandBlockPlan.Entry e=programs.get(armingIndex);
                if(approvedCommandIndices.contains(armingIndex))
                    CommandBlockInstaller.arm(world,origin.add(e.x(),e.y(),e.z()),e);
                armingIndex++;
            }
            if(armingIndex>=programs.size())phase=Phase.COMPLETE;
            return;
        }
        if(phase==Phase.CLEANUP){
            if(cleanupIterator==null)cleanupIterator=supports.iterator();
            int n=0;
            while(cleanupIterator.hasNext()&&n++<128){
                BlockPos p=cleanupIterator.next();
                BlockState s=world.getBlockState(p);
                if(s.isOf(BuildBlocks.CONSTRUCTION_BLOCK)||s.isOf(BuildBlocks.CONSTRUCTION_SLAB))
                    world.setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
                cleanupIterator.remove();
            }
            if(!cleanupIterator.hasNext()){
                beginNormalization(false);
            }
            return;
        }
        if(phase!=Phase.EXCAVATE&&phase!=Phase.BUILD&&phase!=Phase.WAITING_SUPPLIES)return;
        if(phase==Phase.EXCAVATE) {
            // Cinematic is one VISUAL removal event, not a 4-million-cell server
            // tick. The client shields the site during this bounded preparatory pass.
            // World edits use limited chunks to avoid hangs, drops and TPS collapse.
            int scanned=0,removed=0;
            int total=blueprint.width*blueprint.height*blueprint.length;
            final int maxScanned=cinematic?12000:4096;
            final int maxRemoved=cinematic?2048:2048;
            int sampleStride=Math.max(1,collisions/192);
            while(excavationIndex<total && scanned<maxScanned && removed<maxRemoved) {
                int i=constructionOptions.hollow()
                    ?hollowInterior.nextSetBit(excavationIndex)
                    :excavationIndex;
                if(i<0){excavationIndex=total;break;}
                excavationIndex=i+1;
                int x=i%blueprint.width;
                int z=(i/blueprint.width)%blueprint.length;
                int y=i/(blueprint.width*blueprint.length);
                BlockPos p=origin.add(x,y,z);
                if(!world.isChunkLoaded(p)) {excavationIndex=i;break;}
                scanned++;
                BlockState removedState=world.getBlockState(p);
                // Already-air world cells are no-ops. Only the blueprint's
                // positioned Y footprint is inspected; NEVER the world's max height.
                if(!removedState.isAir()) {
                    recordOriginal(p);
                    world.setBlockState(p,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
                    removed++;removedCount++;
                    if(cinematic && excavationSamples.size()<192 &&
                        sampledRemovalCount++%sampleStride==0){
                        excavationSamples.add(p.toImmutable());
                        excavationSampleIds.add(Registries.BLOCK.getId(removedState.getBlock()).toString());
                    }
                }
            }
            if(excavationIndex>=total||
                (constructionOptions.hollow()&&
                 hollowInterior.nextSetBit(excavationIndex)<0)){
                excavationIndex=total;
                if(cinematic&&!constructionOptions.hollow()&&collisions>0){
                    // The client reveals the newly empty site ALL AT ONCE,
                    // then draws sample fragments spiralling into the center.
                    excavationCue(com.hanco.buildai.net.CinematicExcavationPayload.PULL,
                        BLACK_HOLE_TICKS);
                    phase=Phase.CINEMATIC_PULL;
                }else phase=Phase.BUILD;
            }
            return;
        }
        if(phase==Phase.WAITING_SUPPLIES)phase=Phase.BUILD;
        int n=0;
        while(constructionIndex<order.size() && n++<constructionSpeed) {
            BlockPos local=order.get(constructionIndex);
            BlockPos pos=origin.add(local);
            if(!world.isChunkLoaded(pos))return;
            String stateText=effective(local);
            // Source fluids are intentionally deferred until NORMALIZE,
            // after retaining walls, solid supports and redstone dust exist.
            if(BuildFluidRules.isFluidSource(stateText)){
                constructionIndex++;
                continue;
            }
            BlockState target=BlockStates.parse(stateText);
            // Command blocks cannot be crafted or bought in Survival.
            // Administrative map machinery is placed under explicit op-4
            // approval, not charged as an unobtainable player supply item.
            // BuildAI installs literal water/lava SOURCE blockstates itself,
            // AFTER constructing the retaining solid walls/floors. A fluid
            // source has no obtainable block Item and must never pause the
            // job waiting for an imaginary water/lava "block" in a chest.
            if(!creative && !BuildFluidRules.isFluidSource(stateText)
                && !CommandBlockPlan.isCommandBlock(stateText)
                && !blueprint.secondHalf(stateText)) {
                Item item=target.getBlock().asItem();
                if(item==Items.AIR) {phase=Phase.WAITING_SUPPLIES;return;}
                if(!consume(item)) {phase=Phase.WAITING_SUPPLIES;return;}
                consumed.merge(Registries.ITEM.getId(item).toString(),1,Integer::sum);
            }
            // Cosmetic construction scaffolding under overhanging slabs/stairs; not in manifest.
            if(local.getY()>0 &&
                !plannedSupport(local.getX(),local.getY()-1,local.getZ()) &&
                (target.getBlock() instanceof SlabBlock || target.getBlock() instanceof StairsBlock)) {
                BlockPos under=pos.down();
                if(world.getBlockState(under).isAir()){
                    BlockState staging=target.getBlock() instanceof SlabBlock
                        ? BuildBlocks.CONSTRUCTION_SLAB.getDefaultState()
                        : BuildBlocks.CONSTRUCTION_BLOCK.getDefaultState();
                    recordOriginal(under);
                    world.setBlockState(under,staging,Block.NOTIFY_ALL);
                    supports.add(under.toImmutable());
                }
            }
            recordOriginal(pos);
            world.setBlockState(pos,target,Block.NOTIFY_ALL|Block.FORCE_STATE);
            if(cinematic)world.spawnParticles(net.minecraft.particle.ParticleTypes.END_ROD,
                pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,1,0.2,0.3,0.2,0.01);
            constructionIndex++;
        }
        if(constructionIndex==order.size())phase=Phase.CLEANUP;
    }
    public Map<String,Integer> consumed(){return Collections.unmodifiableMap(consumed);}
    public BlockPos statusChest(){
        ensureSupplyChests();
        return supplies.isEmpty()?new BlockPos(0,-2048,0):supplies.get(0);
    }
    public com.hanco.buildai.net.ProjectStatusPayload status(){
        Map<String,Integer> current=stored(),required=requirements();
        StringBuilder lines=new StringBuilder();
        int suppliesTotal=0,needTotal=0;
        for(var e:required.entrySet()){
            int used=consumed.getOrDefault(e.getKey(),0);
            int available=current.getOrDefault(e.getKey(),0);
            needTotal+=e.getValue();
            suppliesTotal+=Math.min(e.getValue(),used+available);
            if(lines.length()<1750)lines.append(e.getKey().replace("minecraft:",""))
                .append(": ").append(used+available).append("/").append(e.getValue()).append("\n");
        }
        return new com.hanco.buildai.net.ProjectStatusPayload(blueprint.name,phase.toString(),
            placed(),total(),suppliesTotal,needTotal,lines.toString(),statusChest(),
            excavationIndex,cells(),removedCount);
    }
    public void substitute(String from,String to) {
        if(phase!=Phase.SELECT_XZ && phase!=Phase.SELECT_SURFACE && phase!=Phase.REVIEW)
            throw new IllegalStateException("Substitutions locked after construction begins");
        if(!blueprint.materials().containsKey(from))throw new IllegalArgumentException("Blueprint does not use "+from);
        SubstitutionPolicy.validateMapping(from,to,world);
        Map<String,String> candidate=new LinkedHashMap<>(substitutions);
        candidate.put(from,to);
        for(int y=0;y<blueprint.height;y++)for(int z=0;z<blueprint.length;z++)for(int x=0;x<blueprint.width;x++)
            BlockStates.parse(SubstitutionPolicy.apply(blueprint,x,y,z,candidate));
        substitutions.put(from,to);
    }
    private String effective(net.minecraft.util.math.BlockPos local) {
        return SubstitutionPolicy.apply(blueprint,local.getX(),local.getY(),local.getZ(),substitutions);
    }
    public int placed(){return constructionIndex;}
    public int total(){return order.size();}
    /** Includes unchanged air cells inspected, never implies a block was mined. */
    public int excavated(){return excavationIndex;}
    public int actuallyRemoved(){return removedCount;}
    public String phaseDescription(){
        return switch(phase){
            case EXCAVATE->constructionOptions.hollow()
                ?"Hollowing only enclosed empty blueprint interior cells ("+
                    hollowInterior.cardinality()+" candidates); all design "+
                    "blocks and outside terrain are preserved"
                :"Excavating the blueprint volume; existing air is untouched";
            case CINEMATIC_PULL->"Excavation complete; terrain spirals into black hole";
            case BUILD->constructionOptions.excavate()
                ?"Building blueprint in progressive layers"
                :constructionOptions.hollow()
                    ?"Building after interior-only clearing; preserving blueprint decorations"
                    :"Overlaying specified blueprint blocks only; preserving empty/unused terrain";
            case WAITING_SUPPLIES->normalizePass==2 && constructionIndex>=order.size()
                ? "Waiting for fluid supplies: 2 water buckets total (renewable) "+
                  "and 1 lava bucket per source, then fluid normalization resumes"
                : "Waiting for supply chests to receive materials";
            case CLEANUP->"Removing temporary construction supports";
            case NORMALIZE->!interiorVerified
                ?"Verifying intended interior schematic AIR ("+
                    interiorVerifyIndex+"/"+cells()+", recleared "+interiorVerificationClears+")"
                :normalizePass>=4&&!manualNormalize&&constructionOptions.hollow()
                ?"Comparing AIR and all schematic building blocks: pass "+
                    (finalAirAuditPass+1)+", "+
                    (finalSolidAuditIndex<0?"interior AIR "+finalAirAuditIndex+
                    "/"+cells():"design blocks "+finalSolidAuditIndex+"/"+order.size())+
                    "; AIR repaired "+finalAirAuditRepairs+
                    ", build blocks repaired "+finalSolidAuditRepairs
                :switch(normalizePass){
                case 0->"Normalizing glass panes, fences, walls and iron bars ("+
                    normalizeIndex+"/"+order.size()+")";
                case 1->"Connecting redstone dust using vanilla 1.21.1 wire shapes ("+
                    normalizeIndex+"/"+order.size()+", repaired "+normalizedRedstone+")";
                case 2->"Placing contained water/lava source blocks after construction ("+
                    normalizeIndex+"/"+order.size()+")";
                default->"Refreshing redstone signal neighbors after fluids and wiring ("+
                    normalizeIndex+"/"+order.size()+", devices "+refreshedRedstone+")";
            };
            case WAITING_COMMAND_APPROVAL->"Build physically complete. COMMAND REVIEW REQUIRED: "+
                commandCount()+" scripts remain inactive. Review in the in-game BuildAI screen. "+
                "Then /buildai commands approve all OR /buildai commands approve 1,2,4 (OP4). "+
                "BuildAI will wait as long as necessary.";
            case COMMISSION->"Commissioning "+approvedCommandIndices.size()+" approved of "+
                commandCount()+" command scripts; unapproved blocks remain inert";
            case ROLLBACK_CHECK->"Undo safety scan: checking "+rollbackCheckIndex+"/"+rollbackOrder.size()+" affected positions";
            case ROLLBACK->"Restoring original block states: "+rollbackIndex+"/"+rollbackOrder.size();
            case ROLLBACK_REFUND->"World restored. Returning "+supplyLedger.pending()+
                " consumed items to original BuildAI chests"+
                (refundHoldReason.isBlank()?"":": "+refundHoldReason);
            case COMPLETE->"Construction complete";
            case PAUSED_ERROR->"Construction paused due to a safety issue";
            case CANCELLED->rollbackFinished
                ?"Undo complete: original world blocks restored; "+refundedSupplies+
                    " Survival supplies returned to source chests"
                :"Project cancelled";
            case REVIEW->"Placement locked; awaiting confirmation";
            default->"Positioning blueprint";
        };
    }
    public int cells(){return blueprint.width*blueprint.height*blueprint.length;}
    public Map<String,Integer> requirements(){
        // Creative never requires supply-chest materials, including fluids.
        if(creative)return Map.of();
        Map<String,Integer> result=new TreeMap<>();
        int water=0,lava=0;
        for(BlockPos local:order){
            String value=effective(local);
            if(BuildFluidRules.isWater(value)){water++;continue;}
            if(BuildFluidRules.isLava(value)){lava++;continue;}
            if(!blueprint.secondHalf(value)&&!CommandBlockPlan.isCommandBlock(value)){
                Item item=BlockStates.parse(value).getBlock().asItem();
                String key=item==Items.AIR?Blueprint.id(value):
                    Registries.ITEM.getId(item).toString();
                result.merge(key,1,Integer::sum);
            }
        }
        if(water>0)result.merge("minecraft:water_bucket",
            BuildFluidRules.requiredWaterBuckets(water),Integer::sum);
        if(lava>0)result.merge("minecraft:lava_bucket",
            BuildFluidRules.requiredLavaBuckets(lava),Integer::sum);
        return result;
    }

    /** Fluid buckets return to their marked supply-chest slots empty.
     * Preflight ensures the BOTH starter water buckets are present before
     * changing inventory or placing the first source.
     */
    private int availableFluidBuckets(Item fluidItem){
        ensureSupplyChests();
        int total=0;
        for(BlockPos chest:supplies){
            if(!(world.getBlockEntity(chest) instanceof Inventory inv))continue;
            for(int i=0;i<inv.size();i++){
                ItemStack stack=inv.getStack(i);
                if(stack.isOf(fluidItem))total+=stack.getCount();
            }
        }
        return total;
    }
    private boolean consumeWaterStarterBuckets(){
        if(availableFluidBuckets(Items.WATER_BUCKET)<2)return false;
        // Non-stackable buckets each occupy an inventory slot; swapping
        // filled for empty returns two physical reusable buckets.
        return consumeFluidBucket(Items.WATER_BUCKET) &&
            consumeFluidBucket(Items.WATER_BUCKET);
    }
    private boolean consumeFluidBucket(Item fluidItem){
        if(availableFluidBuckets(fluidItem)<1)return false;
        for(BlockPos chest:supplies){
            if(!(world.getBlockEntity(chest) instanceof Inventory inv))continue;
            for(int i=0;i<inv.size();i++){
                ItemStack stack=inv.getStack(i);
                if(!stack.isOf(fluidItem)||stack.isEmpty())continue;
                // Vanilla water/lava buckets are unstackable. Fail safely
                // instead of overwriting unrelated stacked/injected items.
                if(stack.getCount()!=1)
                    throw new IllegalStateException("Fluid bucket stack has invalid count at "+
                        chest.toShortString());
                ItemStack receipt=stack.copyWithCount(1);
                inv.setStack(i,new ItemStack(Items.BUCKET));
                inv.markDirty();
                supplyLedger.record(chest,i,receipt,true);
                consumed.merge(Registries.ITEM.getId(fluidItem).toString(),1,Integer::sum);
                return true;
            }
        }
        return false;
    }

    private boolean consume(Item item) {
        ensureSupplyChests();
        for(BlockPos chest:supplies) {
            if(!(world.getBlockEntity(chest) instanceof Inventory inv))continue;
            for(int i=0;i<inv.size();i++) {
                ItemStack stack=inv.getStack(i);
                if(stack.isOf(item)&&!stack.isEmpty()) {
                    ItemStack receipt=stack.copyWithCount(1);
                    inv.removeStack(i,1);inv.markDirty();
                    supplyLedger.record(chest,i,receipt,false);
                    return true;
                }
            }
        }
        return false;
    }
    private void ensureSupplyChests(){
        // Avoid re-scanning thousands of world cells for every HUD update.
        if(lastChestScan==0||world.getTime()-lastChestScan>=40)
            refreshChests();
    }
    private boolean projectSupplyArea(BlockPos pos){
        // A player may put the chest at ANY face/side of a large building,
        // not just within 12 blocks of one placement corner.
        return SupplyChestDiscovery.inProjectArea(
            pos.getX(),pos.getY(),pos.getZ(),
            origin.getX(),origin.getY(),origin.getZ(),
            blueprint.width,blueprint.height,blueprint.length);
    }
    private void discoverSignedChestsNear(BlockPos focus,int radius,int yRange){
        for(int dx=-radius;dx<=radius;dx++)
            for(int dz=-radius;dz<=radius;dz++)
                for(int dy=-yRange;dy<=yRange;dy++){
                    BlockPos candidate=focus.add(dx,dy,dz);
                    if(!projectSupplyArea(candidate)||!world.isChunkLoaded(candidate))continue;
                    if(!(world.getBlockEntity(candidate) instanceof Inventory))continue;
                    if(marked(candidate)&&!supplies.contains(candidate))
                        supplies.add(candidate.toImmutable());
                }
    }
    private void refreshChests() {
        supplies.clear();
        lastChestScan=world.getTime();
        for(BlockPos linked:linkedChests){
            if(world.isChunkLoaded(linked)&&
                world.getBlockEntity(linked) instanceof Inventory&&marked(linked))
                supplies.add(linked);
        }
        // A SIGN reading BuildAI next to a chest is enough. No sneak-click
        // or manual association is required. Manual linking remains optional.
        discoverSignedChestsNear(origin.up(surfaceLayer),12,4);
        var player=world.getServer().getPlayerManager().getPlayer(owner);
        if(player!=null&&player.getServerWorld()==world)
            discoverSignedChestsNear(player.getBlockPos(),16,4);
    }
    private boolean marked(BlockPos chest) {
        for(Direction direction:Direction.values()){
            BlockPos candidate=chest.offset(direction);
            if(world.getBlockEntity(candidate) instanceof SignBlockEntity sign) {
                for(int line=0;line<4;line++) {
                    if(SupplyChestDiscovery.marked(sign.getFrontText().getMessage(line,false).getString())||
                       SupplyChestDiscovery.marked(sign.getBackText().getMessage(line,false).getString()))
                        return true;
                }
            }
        }
        return false;
    }
    public boolean protectedChest(BlockPos pos) {
        // Even freshly signed chests (before the next auto-discovery tick)
        // must not be broken by accident during active construction.
        return linkedChests.contains(pos)||supplies.contains(pos)||
            (projectSupplyArea(pos)&&
                world.getBlockEntity(pos) instanceof Inventory&&marked(pos));
    }
    public void linkChest(BlockPos pos) {
        if(!(world.getBlockEntity(pos) instanceof Inventory))throw new IllegalArgumentException("Select a chest or other inventory block");
        if(!marked(pos))throw new IllegalArgumentException("Put a sign saying BuildAI beside the chest first");
        if(!projectSupplyArea(pos))
            throw new IllegalArgumentException("Supply chest must be at or near your construction site");
        linkedChests.add(pos.toImmutable());refreshChests();
    }
    public Map<String,Integer> stored(){
        ensureSupplyChests();
        Map<String,Integer> map=new TreeMap<>();
        for(BlockPos pos:supplies)if(world.getBlockEntity(pos) instanceof Inventory inv)
            for(int slot=0;slot<inv.size();slot++){
                ItemStack stack=inv.getStack(slot);
                if(stack.isEmpty())continue;
                map.merge(Registries.ITEM.getId(stack.getItem()).toString(),stack.getCount(),Integer::sum);
            }
        return map;
    }
    /** Fail-closed: don't leave partially commissioned minigame loops running. */
    public void disarmCommands(){
        if(!commandsApproved)return;
        for(CommandBlockPlan.Entry entry:blueprint.commands()){
            BlockPos pos=origin.add(entry.x(),entry.y(),entry.z());
            if(!world.isChunkLoaded(pos))continue;
            if(world.getBlockEntity(pos) instanceof CommandBlockBlockEntity tile &&
                tile.getCommandExecutor().getCommand().equals(entry.command())){
                tile.setAuto(false);
                tile.markDirty();
            }
        }
    }

    private void verifyUndoTarget(BlockPos pos,boolean override) {
        BlockState state=world.getBlockState(pos);
        if(state.isOf(Blocks.BEDROCK))
            throw new IllegalStateException("Undo will not overwrite bedrock at "+pos.toShortString());
        if(!state.hasBlockEntity())return;
        if(override)return;
        // Command blocks placed by BuildAI are safe to reverse IF their
        // executable contents still match our approved plan (or are empty).
        // Player-edited scripts or unrelated inventories require explicit
        // force and cannot be silently destroyed by ordinary undo.
        if(CommandBlockPlan.isCommandBlock(Registries.BLOCK.getId(state.getBlock()).toString()) &&
            world.getBlockEntity(pos) instanceof CommandBlockBlockEntity tile) {
            int x=pos.getX()-origin.getX(),y=pos.getY()-origin.getY(),z=pos.getZ()-origin.getZ();
            if(x>=0&&y>=0&&z>=0&&x<blueprint.width&&y<blueprint.height&&z<blueprint.length&&
                CommandBlockPlan.isCommandBlock(blueprint.get(x,y,z))) {
                String current=tile.getCommandExecutor().getCommand();
                String expected="";
                for(CommandBlockPlan.Entry e:blueprint.commands())
                    if(e.x()==x&&e.y()==y&&e.z()==z){expected=e.command();break;}
                if(current.isEmpty()||current.equals(expected))return;
            }
        }
        throw new IllegalStateException("Undo blocked by a changed block entity at "+
            pos.toShortString()+". Ordinary rollback preserves it. "+
            "A trusted operator can deliberately use /buildai undo force to discard its data.");
    }

    /**
     * Undo runs in bounded tick batches, including a full non-mutating safety
     * preflight. It is available after COMPLETE, PAUSED_ERROR or earlier
     * cancellation; no state is claimed restored until ROLLBACK actually ends.
     */
    public int requestUndo(boolean override) {
        if(phase==Phase.ROLLBACK||phase==Phase.ROLLBACK_CHECK||
            phase==Phase.ROLLBACK_REFUND)
            throw new IllegalStateException("Undo already in progress: "+phaseDescription());
        if(!hasSnapshot||modified.isEmpty())
            throw new IllegalStateException("No in-memory construction changes to undo. "+
                "Undo history does not currently survive a server restart or replacement project.");
        disarmCommands();
        excavationCue(com.hanco.buildai.net.CinematicExcavationPayload.STOP,0);
        rollbackOrder=new ArrayList<>(modified);
        rollbackOrder.sort(Comparator.comparingInt(BlockPos::getY)
            .thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX).reversed());
        rollbackOverride=override;rollbackFinished=false;
        refundedSupplies=0;refundHoldReason="";
        rollbackIndex=0;rollbackCheckIndex=0;
        phase=Phase.ROLLBACK_CHECK;
        return rollbackOrder.size();
    }

    public void cancel(boolean undo) {
        if(undo){requestUndo(false);return;}
        if(phase==Phase.ROLLBACK||phase==Phase.ROLLBACK_CHECK||
            phase==Phase.ROLLBACK_REFUND)
            throw new IllegalStateException("Undo is in progress; wait for rollback to finish");
        disarmCommands();
        excavationCue(com.hanco.buildai.net.CinematicExcavationPayload.STOP,0);
        for(BlockPos pos:new ArrayList<>(supports)){
            BlockState state=world.getBlockState(pos);
            if(state.isOf(BuildBlocks.CONSTRUCTION_BLOCK)||state.isOf(BuildBlocks.CONSTRUCTION_SLAB))
                world.setBlockState(pos,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
        }
        supports.clear();
        rollbackFinished=false;
        phase=Phase.CANCELLED;
    }
}
