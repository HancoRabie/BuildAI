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
                           int receivedCharacters, boolean finished, boolean failed) {}

    private final long startedNanos=System.nanoTime();
    private long lastActivityNanos=startedNanos;
    private String stage="Preparing request";
    private String detail="Connecting to the model service.";
    private final List<String> milestones=new ArrayList<>();
    private int receivedCharacters=0;
    private boolean finished=false;
    private boolean failed=false;

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
        receivedCharacters+=count;
        lastActivityNanos=System.nanoTime();
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
        return new Snapshot(stage,detail,List.copyOf(milestones),
            Math.max(0,(now-startedNanos)/1_000_000_000L),
            Math.max(0,(now-lastActivityNanos)/1_000_000_000L),
            receivedCharacters,finished,failed);
    }
}
