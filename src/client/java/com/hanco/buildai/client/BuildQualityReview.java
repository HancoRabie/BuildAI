package com.hanco.buildai.client;

import com.hanco.buildai.blueprint.*;

/**
 * General-purpose, bounded post-generation acceptance/optimization review.
 * No domain (farm/castle/minigame/roller coaster) gets privileged routing.
 * The original request wins; model suggestions are subordinate to measured
 * block data. There is NO imaginary redstone/fluid/game simulation.
 */
final class BuildQualityReview {
    private BuildQualityReview(){}
    // Two total bounded improvements at most; no unbounded token/time loop.
    private static final int MAX_CORRECTIONS=2;
    private static final int MAX_REVIEW_SPEC=100_000;

    static AIArchitect.DesignResult review(String originalRequest,
                      AIArchitect.DesignResult initial,String apiKey,
                      boolean connectedChat,GenerationProgress progress)
                      throws Exception {
        AIArchitect.DesignResult best=initial;
        BuildRequirementAudit.Report baseline=BuildRequirementAudit.inspect(
            best.blueprint(),originalRequest,best.specification());
        if(baseline.blockers().isEmpty()){
            best.blueprint().warnings.addAll(baseline.warnings());
            best.blueprint().warnings.add(
                "QUALITY REVIEW: No measurable static blockers detected. "+
                "This is NOT proof every requested feature functions.");
            return best;
        }
        // Rewriting a fully integrated multi-stage project as one giant model
        // answer risks removing stage ports/checkpoints and defeating staged
        // generation. Do not pretend the generic review can repair that.
        if(best.specification().contains("\"staged_modules\"")){
            best.blueprint().warnings.addAll(baseline.warnings());
            best.blueprint().warnings.add("QUALITY REVIEW: Multi-stage project "+
                "has unresolved requirements. Automatic whole-project rewrite "+
                "is disabled to protect stage interfaces; revise the affected "+
                "module in a future stage-local repair flow.");
            return best;
        }
        int attempts=0;
        while(attempts<MAX_CORRECTIONS && !baseline.blockers().isEmpty()){
            if(progress!=null&&progress.cancellationRequested())
                throw new java.util.concurrent.CancellationException("Review cancelled.");
            if(best.specification().length()>MAX_REVIEW_SPEC){
                best.blueprint().warnings.add("QUALITY REVIEW: Full AI correction "+
                    "skipped: blueprint specification exceeds 100K characters. "+
                    "Original design preserved; use a targeted section revision.");
                break;
            }
            String guidance=guidance(originalRequest,baseline,attempts);
            if(progress!=null)progress.stage(
                "Acceptance review "+(attempts+1)+"/"+MAX_CORRECTIONS,
                baseline.blockers().size()+" measurable requirement "+
                "defect(s). Trying a bounded corrective revision with "+
                "the previous blueprint protected.");
            AIArchitect.DesignResult candidate;
            try{
                candidate=AIArchitect.repairWithCachedResearch(
                    originalRequest,best.specification(),apiKey,
                    connectedChat,progress,best.inspiration(),guidance);
            }catch(Exception e){
                if(progress!=null&&progress.cancellationRequested())throw e;
                best.blueprint().warnings.add(
                    "QUALITY REVIEW: Correction request failed; retained "+
                    "the earlier blueprint. "+reason(e));
                break;
            }
            attempts++;
            BuildRequirementAudit.Report revised=BuildRequirementAudit.inspect(
                candidate.blueprint(),originalRequest,candidate.specification());
            if(!BuildRequirementAudit.improves(
                baseline,revised,best.blueprint(),candidate.blueprint())){
                best.blueprint().warnings.add("QUALITY REVIEW: Rejected "+
                    "revision "+attempts+"; it did not measurably reduce "+
                    "unmet requirements or it lost significant geometry. "+
                    "Original design preserved.");
                break;
            }
            best=candidate;
            baseline=revised;
            if(progress!=null)progress.stage("Acceptance improved",
                "Verified a lower count of measurable requirement blockers. "+
                "This is static QA, not evidence the mechanism was run.");
        }
        best.blueprint().warnings.addAll(baseline.warnings());
        if(baseline.blockers().isEmpty())
            best.blueprint().warnings.add("QUALITY REVIEW: Static requirement "+
                "checks passed; functionality and gameplay still require "+
                "real Minecraft testing. No simulator was run.");
        else
            best.blueprint().warnings.add("QUALITY REVIEW: "+baseline.blockers().size()+
                " measurable requirement blocker(s) remain after "+attempts+
                " revision attempt(s). Not certified as fully working.");
        return best;
    }

    private static String guidance(String prompt,BuildRequirementAudit.Report report,int pass){
        StringBuilder sb=new StringBuilder(
            "BUILDAI GENERAL ACCEPTANCE REVIEW (revision pass "+(pass+1)+
            "). Return a COMPLETE replacement JSON design preserving "+
            "the original player's requested scale, rooms, mechanisms, "+
            "purpose, and existing functional features. Not a diff. "+
            "Only fix measurable failures; do not invent unrelated "+
            "structures, workers, mods, mechanics, or unrequested "+
            "features. Preserve coordinate bounds and original block "+
            "placement wherever already correct. Optimize away genuinely "+
            "redundant machinery only when it cannot break requirements. "+
            "Provide real Minecraft 1.21.1 block states, controls, "+
            "item routes, and an honest test plan. Do not claim to "+
            "simulate game ticks, water, inventory movement or physics. "+
            "Model-authored status claims are not validation. "+
            "Review the WHOLE building against the original prompt. "+
            "Defects to correct in actual geometry:\n");
        for(String issue:report.blockers()) {
            if(sb.length()>3100)break;
            sb.append("- ").append(issue).append('\n');
        }
        int count=0;
        for(String issue:report.suggestions()) {
            if(sb.length()>3500||count++>=3)break;
            sb.append("Optional improvement: ").append(issue).append('\n');
        }
        return sb.toString();
    }
    private static String reason(Throwable t){
        while(t.getCause()!=null)t=t.getCause();
        String s=t.getMessage();
        if(s==null)return t.getClass().getSimpleName();
        s=s.replaceAll("[\\r\\n\\t]+"," ");
        return s.length()>180?s.substring(0,180):s;
    }
}
