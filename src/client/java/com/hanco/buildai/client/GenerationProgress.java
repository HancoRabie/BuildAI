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
                           double charactersPerSecond) {}

    private final String category;
    private final String originalPrompt;
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
        category=GenerationHistory.category(prompt);
    }
    public String category(){return category;}
    public String originalPrompt(){return originalPrompt;}

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
            " | Model "+modelLabel+" | "+connection+" | "+
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
            stage("Auto-stop ON","Started a fresh silence window: "+
                "8 minutes before first blueprint text, 3 minutes after "+
                "text begins. Your current model request is unchanged.");
        }else{
            stage("Auto-stop OFF","BuildAI will not automatically end this "+
                "request for output silence. It continues until the model "+
                "responds, the connection fails or you cancel it.");
        }
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
        stage="Generation failed";
        detail=explanation;
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
            receivedCharacters,finished,failed,rate);
    }
}
