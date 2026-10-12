package com.hanco.buildai.client;

import com.hanco.buildai.blueprint.*;

/**
 * Experimental but real multi-request architect for specified automated farms
 * with 3+ numbered crop floors. One player prompt -> one research synthesis ->
 * architecture, separate floor engineering, shared systems -> ONE combined
 * confirmed blueprint. No external assistant transcript is passed between
 * independent stages; coordinates and interface regions are deterministic.
 *
 * Stage results are validated before assembly, and NOTHING is pasted into
 * Minecraft until ALL model stages successfully finish. A cancelled model
 * cannot replace the player's existing blueprint.
 */
final class StagedFarmArchitect {
    private StagedFarmArchitect(){}

    static AIArchitect.DesignResult generate(String original,String apiKey,
                                              boolean connectedChat,
                                              GenerationProgress progress)
            throws Exception {
        StagedFarmPlan plan=StagedFarmPlan.from(original);
        if(progress!=null&&progress.cancellationRequested())
            throw new java.util.concurrent.CancellationException("Cancelled.");
        if(progress!=null)progress.stagedPlan(plan.stages().size());
        if(progress!=null)progress.stage("Engineering plan created",
            "Divided into "+plan.stages().size()+" independently generated "+
            "modules with one shared "+plan.width()+"x"+plan.height()+
            "x"+plan.length()+" coordinate grid. No design data lost.");

        String token=connectedChat?ChatGPTSignIn.token():apiKey;
        String model=connectedChat?ChatGPTSignIn.model():"gpt-4.1-mini";
        InspirationResearch.Result inspiration=
            InspirationResearch.research(token,model,original,progress);
        StagedFarmAssembler.Assembly merged=new StagedFarmAssembler.Assembly(plan);
        StagedFarmCheckpoints checkpoint=new StagedFarmCheckpoints(
            net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir()
                .resolve("buildai/staged-workspaces"),
            original,model,RedstoneKnowledge.instructions(original,null));
        checkpoint.begin();

        for(int i=0;i<plan.stages().size();i++){
            if(progress!=null&&progress.cancellationRequested())
                throw new java.util.concurrent.CancellationException(
                    "Stopped before stage "+(i+1));
            StagedFarmPlan.Stage stage=plan.stages().get(i);
            if(progress!=null)progress.stagedModule(
                i+1,plan.stages().size(),stage.name());
            String saved=checkpoint.load(i);
            if(saved!=null){
                try{
                    merged.add(stage,saved); // all region/interface rules rechecked
                    if(progress!=null)progress.stage(
                        "Restored validated stage "+(i+1)+"/"+plan.stages().size(),
                        stage.name()+" was saved locally before the earlier "+
                        "generation stopped. No repeat model call needed.");
                    continue;
                }catch(RuntimeException corrupted){
                    checkpoint.discard(i);
                }
            }
            String stagePrompt=plan.stagePrompt(original,stage,i,
                merged.connectionBrief());
            if(progress!=null)progress.stage(
                "Stage "+(i+1)+"/"+plan.stages().size()+": "+stage.name(),
                "Requesting just this subsystem, not the full farm. "+
                merged.completed()+" completed stages remain in memory.");
            AIArchitect.DesignResult piece=connectedChat
                ?AIArchitect.generateStageWithChatGPT(stagePrompt,progress,inspiration)
                :AIArchitect.generateStageWithApi(apiKey,stagePrompt,progress,inspiration);
            merged.add(stage,piece.specification());
            checkpoint.save(i,piece.specification());
            if(progress!=null)progress.stage(
                "Stage "+(i+1)+"/"+plan.stages().size()+" validated",
                stage.name()+" returned "+piece.blueprint().solidCount()+
                " voxel positions and was added to the combined design. "+
                merged.completed()+" of "+merged.total()+" complete.");
        }
        if(progress!=null&&progress.cancellationRequested())
            throw new java.util.concurrent.CancellationException("Stopped before final QA");
        String specification=merged.finish();
        if(progress!=null)progress.stagedFinalReview();
        if(progress!=null)progress.stage("Integrating the full farm",
            "All "+merged.total()+" independent modules returned geometry. "+
            "Checking combined overlaps, block states and redstone plans.");
        ProceduralGenerator.Compilation compiled=
            ProceduralGenerator.compileDesign(specification);
        Blueprint blueprint=compiled.blueprint();
        DoorPlanner.completeSafePairs(blueprint);
        BlockStates.validate(blueprint);
        try {
            blueprint.warnings.addAll(ArchitectureAudit.inspect(
                blueprint,original,compiled.correctedSpecification()));
            blueprint.warnings.addAll(CommandGameAudit.inspect(blueprint,original));
            blueprint.warnings.addAll(RedstoneAudit.inspect(
                blueprint,original,compiled.correctedSpecification()));
            blueprint.warnings.addAll(FluidGateAudit.inspect(
                blueprint,original,compiled.correctedSpecification()));
            blueprint.warnings.addAll(ChainGapAudit.inspect(blueprint));
        }catch(RuntimeException qaError){
            blueprint.warnings.add("Some nonessential staged Design QA "+
                "checks failed; inspect cross-floor control, hopper and "+
                "water connections manually before using this farm.");
        }
        blueprint.warnings.add("STAGED DESIGN: "+merged.total()+
            " independently designed modules share one global coordinate "+
            "system. Physical integration is not proof that any redstone "+
            "circuit works. Test EACH floor, master button, item transport "+
            "and timed water/piston resets in a disposable world.");
        checkpoint.complete();
        if(progress!=null)progress.stage("Staged farm assembled",
            merged.total()+" real engineering modules were combined into "+
            "one "+plan.width()+"x"+plan.height()+"x"+plan.length()+
            " blueprint; ready to inspect and preview.");
        return new AIArchitect.DesignResult(
            blueprint,compiled.correctedSpecification(),inspiration);
    }
}
