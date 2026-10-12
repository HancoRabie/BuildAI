package com.hanco.buildai.client;

import com.hanco.buildai.blueprint.*;

/**
 * One bounded model-assisted post-generation repair for measurable farm defects.
 * Never edits the Minecraft world, never loops indefinitely, and never accepts
 * a model's replacement simply because the model claims it is functional.
 */
final class FarmFunctionRepair {
    private FarmFunctionRepair() {}
    private static final int MAX_REPAIR_SPEC=100_000;

    static AIArchitect.DesignResult review(String prompt,AIArchitect.DesignResult original,
                                           String key,boolean connectedChat,
                                           GenerationProgress progress) throws Exception {
        FarmFunctionAudit.Report first=FarmFunctionAudit.inspect(
            original.blueprint(),prompt,original.specification());
        if(first.warnings().isEmpty())return original;
        if(!first.needsRepair()){
            original.blueprint().warnings.addAll(first.warnings());
            return original;
        }
        if(original.specification().length()>MAX_REPAIR_SPEC) {
            original.blueprint().warnings.addAll(first.warnings());
            original.blueprint().warnings.add("FARM REPAIR: Automatic model correction "+
                "skipped: this design exceeds the safe 100K-character single-revision "+
                "limit. Blueprint preserved; request a targeted manual revision.");
            return original;
        }
        // The ORIGINAL player/stage prompt is submitted unchanged. Repair
        // findings are additional developer guidance, not appended to the
        // 4K player input limit or substituted for its constraints.
        StringBuilder instruction=new StringBuilder();
        instruction.append("BUILDAI FUNCTION REPAIR — revise supplied "+
            "design with the SAME original request and coordinate scope.\n");
        instruction.append("Return a COMPLETE corrected JSON blueprint, not a diff. "+
            "Preserve the original dimensions, crop varieties/counts, architecture, "+
            "required ports, controls, and all unrelated geometry. Fix actual "+
            "machine geometry and wiring; do not just rewrite the redstone_plan "+
            "or claim a test passed. Do not use bone meal dispensers as a harvest "+
            "mechanism. Empty water-bucket dispensers cannot flush crops without "+
            "inventory; favor a genuine piston-operated retained water source, "+
            "controlled open/close gate and sufficient timer with real physical "+
            "crop-level flow to a collection channel and hoppers. Soil irrigation "+
            "water does not harvest. For carrots/potatoes/wheat/beetroot document "+
            "that redstone cannot automatically replant.\n");
        instruction.append("AUDIT FAILURES (repair each actual defect):\n");
        for(String issue:first.blockers())instruction.append("- ").append(issue).append('\n');
        if(progress!=null)progress.stage("Farm function audit: attempting one repair",
            first.blockers().size()+" likely farm functionality blocker(s). "+
            "Trying one model revision; original blueprint remains protected.");
        AIArchitect.DesignResult candidate;
        try{
            candidate=AIArchitect.repairWithCachedResearch(
                prompt,original.specification(),key,connectedChat,progress,
                original.inspiration(),instruction.toString());
        }catch(Exception problem){
            if(progress!=null&&progress.cancellationRequested())throw problem;
            original.blueprint().warnings.addAll(first.warnings());
            original.blueprint().warnings.add("FARM REPAIR: Model correction failed; "+
                "kept original geometry. Inspect/repair the crop collection by revision. "+
                "Reason: "+safeMessage(problem));
            return original;
        }
        FarmFunctionAudit.Report second=FarmFunctionAudit.inspect(
            candidate.blueprint(),prompt,candidate.specification());
        if(!FarmFunctionAudit.improves(first,second,original.blueprint(),
                                      candidate.blueprint())){
            original.blueprint().warnings.addAll(first.warnings());
            original.blueprint().warnings.add("FARM REPAIR: Rejected AI correction "+
                "because measurable defects did not improve or crops, dimensions, "+
                "or too much existing geometry were removed. Original preserved.");
            return original;
        }
        candidate.blueprint().warnings.addAll(second.warnings());
        candidate.blueprint().warnings.add("FARM REPAIR: Adopted one revised "+
            "blueprint because static farm blockers decreased from "+
            first.blockers().size()+" to "+second.blockers().size()+
            ". Not a verified redstone/water simulation; test in Minecraft.");
        if(progress!=null)progress.stage("Farm function correction accepted",
            "Accepted one safer revision after geometry checks; no world "+
            "blocks have been changed.");
        return candidate;
    }

    private static String safeMessage(Throwable ex){
        Throwable root=ex;while(root.getCause()!=null)root=root.getCause();
        String result=root.getMessage();
        if(result==null)return root.getClass().getSimpleName();
        result=result.replaceAll("[\\r\\n\\t]+"," ").trim();
        return result.length()>130?result.substring(0,130):result;
    }
}
