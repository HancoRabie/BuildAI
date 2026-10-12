package com.hanco.buildai.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Thread-safe, observable progress of a single BuildAI generation.
 * Reports only measured events. No simulated completion percentage is emitted.
 */
public final class GenerationProgress {
    public record Snapshot(String stage, String detail, List<String> milestones,
                           long elapsedSeconds, long secondsSinceActivity,
                           int receivedCharacters, boolean finished, boolean failed,
                           double charactersPerSecond, String workflow) {}

    private final String category;
    private final String originalPrompt;
    // Persistent local workflow metadata, independent of generic model events.
    private final boolean stagedEligible;
    private int stagedTotal=0, stagedCurrent=0;
    private String stagedModule="";
    private boolean stagedFinalReview=false;
    private final long startedNanos=System.nanoTime();
    private long lastStreamEventNanos=0;
    private String lastStreamEvent="None yet";
    private boolean responseAccepted=false;
    private volatile boolean revising=false;
    public void revisionMode(boolean value){revising=value;}
    private String modelLabel="Unknown";
    private String probeResult="";
    private boolean checking=false;
    private long firstCharactersNanos=0L;

    public GenerationProgress(){this(null);}
    public GenerationProgress(String prompt){
        originalPrompt=prompt==null?"":prompt;
        stagedEligible=com.hanco.buildai.blueprint.StagedFarmPlan.applies(originalPrompt);
        category=GenerationHistory.category(prompt);
    }
    public String category(){return category;}
    public String originalPrompt(){return originalPrompt;}

    /** Module counters are local workflow facts, never model-predicted. */
    public synchronized void stagedPlan(int total){
        if(finished||total<2)throw new IllegalArgumentException("Invalid staged module count");
        stagedTotal=total;
        stagedCurrent=0;
        stagedModule="Planning / inspiration";
        stagedFinalReview=false;
    }
    public synchronized void stagedModule(int module,int total,String name){
        if(finished||total!=stagedTotal||module<1||module>total)
            throw new IllegalArgumentException("Invalid staged module update");
        stagedCurrent=module;
        stagedModule=name==null?"Unnamed module":name;
    }
    public synchronized void stagedFinalReview(){
        if(stagedTotal>0){stagedCurrent=stagedTotal;stagedFinalReview=true;}
    }
    public synchronized String workflow(){
        if(stagedTotal>0){
            if(stagedFinalReview)return "STAGED FARM | All "+stagedTotal+" modules returned | Final QA";
            if(stagedCurrent>0)return "STAGED FARM | "+stagedCurrent+"/"+stagedTotal+
                " | "+stagedModule;
            return "STAGED FARM | "+stagedTotal+" modules | "+stagedModule;
        }
        if(revising)return "REVISION | Single-request pipeline";
        return stagedEligible
            ?"STAGED FARM ELIGIBLE | Awaiting module planning"
            :"SINGLE REQUEST | Staged farm trigger not matched";
    }

    public synchronized void requestSent(String model){
        modelLabel=model==null||model.isBlank()?"Unknown":model;
    }
    public synchronized void responseAccepted(){
        responseAccepted=true;
        lastStreamEventNanos=System.nanoTime();
        lastStreamEvent="HTTP 200 / stream opened";
    }
    public synchronized void streamEvent(String type){
        lastStreamEventNanos=System.nanoTime();
        lastStreamEvent=type==null?"Unknown":type;
    }
    public synchronized String localDiagnostic(){
        long now=System.nanoTime();
        long age=(now-startedNanos)/1_000_000_000L;
        String connection=responseAccepted?
            "Request accepted (HTTP 200)":"Awaiting model connection";
        String event=lastStreamEventNanos==0?"No stream event yet":
            "Last event "+lastStreamEvent+" "+
                ((now-lastStreamEventNanos)/1_000_000_000L)+"s ago";
        return (revising?"REVISION (previous blueprint supplied)":
            "GENERATE NEW (no previous blueprint supplied)")+
            " | Auto-stop "+(autoStopEnabled?"ON":"OFF")+
            " | "+workflow()+" | Model "+modelLabel+" | "+connection+" | "+
            receivedCharacters+" blueprint characters | "+event+
            " | elapsed "+age+"s. "+
            "A successful connection check cannot verify that "+
            "THIS model response is still processing.";
    }
    public synchronized boolean beginCheck(){
        if(checking)return false;
        checking=true;
        probeResult="Checking OpenAI independently. Active generation is unchanged.";
        return true;
    }
    public synchronized void completeCheck(String status){
        probeResult=status==null?"No health report available":status;
        checking=false;
    }
    public synchronized String checkStatus(){return probeResult;}
    /** Concise health display kept inside the always-visible header area. */
    public synchronized String briefCheckStatus(){
        if(probeResult.isBlank())return "";
        if(checking)return "Independent API reachability check running; active build unchanged.";
        if(probeResult.startsWith("OpenAI reachable"))
            return "API reachable (HTTP 200). This does NOT prove active model progress.";
        if(probeResult.startsWith("OpenAI probe returned HTTP ")){
            int end=probeResult.indexOf('.',0);
            return (end>0?probeResult.substring(0,end):probeResult)+
                ". Active model state is unknown.";
        }
        if(probeResult.startsWith("Connection check unavailable"))
            return "API check unavailable (no current credentials). Active build unchanged.";
        return probeResult.length()>140?probeResult.substring(0,140):probeResult;
    }
    public synchronized String lastStreamSummary(){
        String noun=finished?"Last request: ":"Active request: ";
        if(lastStreamEventNanos==0)
            return noun+(responseAccepted?"HTTP 200":"awaiting HTTP response")+
                " | No SSE stream event received yet";
        return noun+(responseAccepted?"HTTP 200":"connecting")+
            " | Last SSE: "+lastStreamEvent+" "+
            ((System.nanoTime()-lastStreamEventNanos)/1_000_000_000L)+"s ago";
    }
    public synchronized boolean checking(){return checking;}
    private long lastActivityNanos=startedNanos;
    private String stage="Preparing request";
    private String detail="Connecting to the model service.";
    private final List<String> milestones=new ArrayList<>();
    private int receivedCharacters=0;
    private boolean finished=false;
    private boolean failed=false;
    private volatile boolean cancelRequested=false;
    // Per-request operator preference; changing this does not reissue the
    // OpenAI request, reset the submitted prompt or mutate the model stream.
    private volatile boolean autoStopEnabled=true;
    private volatile long autoStopRearmedAtNanos=0L;
    public boolean autoStopEnabled(){return autoStopEnabled;}
    public long autoStopRearmedAtNanos(){return autoStopRearmedAtNanos;}
    public synchronized void setAutoStopEnabled(boolean enabled){
        if(finished||autoStopEnabled==enabled)return;
        autoStopEnabled=enabled;
        if(enabled){
            // A user who waited past the old deadline with auto-stop OFF
            // must get a brand-new full grace period when enabling again.
            autoStopRearmedAtNanos=System.nanoTime();
        }
        // The button displays this preference; never overwrite the actual
        // generation stage or reset model-activity telemetry for a toggle.
    }
    private String failureExplanation="";
    /** Make the evidence accessible after the modal is closed, and explicitly
     * separate observed protocol failure from unprovable upstream causes. */
    public synchronized String failureSummary(){
        if(failureExplanation.isBlank())return "";
        String lower=failureExplanation.toLowerCase(java.util.Locale.ROOT);
        if(lower.contains("without response.completed")||
           lower.contains("partial design is not valid"))
            return "Stream ended before OpenAI confirmed completion; partial JSON "+
                "was not accepted. Exact upstream cause is unknown.";
        if(lower.contains("response.incomplete"))
            return "OpenAI reported an incomplete response; inspect the reason "+
                "code in Copy Failure Report.";
        if(lower.contains("response.failed"))
            return "OpenAI reported a failed response; inspect its error details.";
        if(lower.contains("chatgpt generation http"))
            return "The generation endpoint returned a non-success HTTP status.";
        if(lower.contains("3 minutes")||lower.contains("8 minutes")||
           lower.contains("stalled"))
            return "BuildAI's silence watchdog ended the stalled request.";
        if(lower.contains("compilation")||lower.contains("invalid")||
           lower.contains("parse")||lower.contains("json"))
            return "Model output failed blueprint decoding or validation.";
        if(lower.contains("stream")||lower.contains("connection")||
           lower.contains("i/o")||lower.contains("socket"))
            return "A stream/network read error ended generation. Cause is not confirmed.";
        return "Generation stopped with an error; Copy Failure Report for details.";
    }
    public synchronized String failureReport(){
        long age=(System.nanoTime()-startedNanos)/1_000_000_000L;
        long eventAge=lastStreamEventNanos==0?-1:
            (System.nanoTime()-lastStreamEventNanos)/1_000_000_000L;
        return "BuildAI Generation Failure Report\\n"+
            "Status: "+(finished?(failed?"FAILED":"COMPLETE"):"ACTIVE")+"\\n"+
            "Observed endpoint status: "+(responseAccepted?"HTTP 200 / stream opened":
                "No successful HTTP 200 response recorded")+"\\n"+
            "Last model SSE event: "+lastStreamEvent+"\\n"+
            "Seconds since that event: "+(eventAge<0?"no event":eventAge)+"\\n"+
            "Elapsed seconds: "+age+"\\n"+
            "Text characters received: "+receivedCharacters+"\\n"+
            "Auto-stop enabled: "+autoStopEnabled+"\\n"+
            "Cancellation requested: "+cancelRequested+"\\n"+
            "Diagnosis: "+failureSummary()+"\\n"+
            "Error and recovery location: "+failureExplanation+"\\n"+
            "Note: HTTP 200 only confirms request acceptance; missing "+
            "response.completed cannot identify which upstream component "+
            "interrupted the stream.\\n";
    }
    private final java.util.concurrent.atomic.AtomicReference<Thread> worker=
        new java.util.concurrent.atomic.AtomicReference<>();
    private volatile String lastModelLimitation="";
    public void modelLimitation(String message){
        // Keep only a short diagnostic for the user to refine later; do not
        // carry the previous response into a new model conversation.
        String value=message==null?"":message.replaceAll("[\\r\\n\\t]+"," ").trim();
        lastModelLimitation=value.length()>400?value.substring(0,400):value;
    }
    public String lastModelLimitation(){return lastModelLimitation;}
    public void bindWorker(){
        worker.set(Thread.currentThread());
        if(cancelRequested)throw new java.util.concurrent.CancellationException(
            "Request was cancelled before the network operation began.");
    }
    public void releaseWorker(){worker.compareAndSet(Thread.currentThread(),null);}
    public void requestCancellation(){
        cancelRequested=true;
        stage("Cancellation requested","Interrupting the previous model request. "+
            "Your original prompt and saved blueprint remain unchanged.");
        Thread thread=worker.get();
        if(thread!=null)thread.interrupt();
    }
    public boolean cancellationRequested(){return cancelRequested;}
    public synchronized void cancelled(){
        if(finished)return;
        stage="Request cancelled";
        detail="Old request stopped. Your prompt is saved; clarify it and "+
            "start a fresh independent generation.";
        milestones.add(stage);
        if(milestones.size()>5)milestones.remove(0);
        finished=true;failed=true;
        lastActivityNanos=System.nanoTime();
    }

    public synchronized void stage(String name,String explanation) {
        if(finished)return;
        if(!stage.equals(name)) {
            milestones.add(name);
            if(milestones.size()>5)milestones.remove(0);
        }
        stage=name;
        detail=explanation;
        lastActivityNanos=System.nanoTime();
    }
    public synchronized void receivedCharacters(int count) {
        if(finished || count<=0)return;
        long now=System.nanoTime();
        if(firstCharactersNanos==0)firstCharactersNanos=now;
        receivedCharacters+=count;
        lastActivityNanos=now;
    }
    public synchronized void success(String explanation) {
        stage="Blueprint ready";
        detail=explanation;
        milestones.add(stage);
        if(milestones.size()>5)milestones.remove(0);
        finished=true;
        lastActivityNanos=System.nanoTime();
    }
    public synchronized void failure(String explanation) {
        failureExplanation=explanation==null?"Unknown generation failure":explanation;
        stage="Generation failed";
        detail=failureSummary();
        milestones.add(stage);
        if(milestones.size()>5)milestones.remove(0);
        finished=true;
        failed=true;
        lastActivityNanos=System.nanoTime();
    }
    public synchronized Snapshot snapshot() {
        long now=System.nanoTime();
        double rate=firstCharactersNanos==0?0.0:
            receivedCharacters/Math.max(1.0,(now-firstCharactersNanos)/1_000_000_000.0);
        return new Snapshot(stage,detail,List.copyOf(milestones),
            Math.max(0,(now-startedNanos)/1_000_000_000L),
            Math.max(0,(now-lastActivityNanos)/1_000_000_000L),
            receivedCharacters,finished,failed,rate,workflow());
    }
}
