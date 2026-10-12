package com.hanco.buildai.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SSE BodyHandlers.ofInputStream reads are potentially unbounded AFTER HTTP
 * response headers: HttpRequest.timeout DOES NOT bound a blocked readLine().
 *
 * A daemon watches time without actual blueprint text when the player keeps
 * Auto-stop ON. With Auto-stop OFF, no silence deadline applies. Re-enabling
 * begins a full fresh 8m/3m grace period. Heartbeat/reasoning events do NOT
 * reset the deadline.
 * This does not change model output or lower quality for healthy requests.
 */
final class ModelStreamWatchdog implements AutoCloseable {

    private final InputStream input;
    private final GenerationProgress progress;
    private final AtomicLong lastText=new AtomicLong(System.nanoTime());
    private final AtomicBoolean begun=new AtomicBoolean(false);
    private final AtomicBoolean expired=new AtomicBoolean(false);
    private final AtomicBoolean closed=new AtomicBoolean(false);
    private final Thread thread;
    private boolean warned=false;

    ModelStreamWatchdog(InputStream input,GenerationProgress progress) {
        this.input=input;this.progress=progress;
        thread=new Thread(this::watch,"BuildAI-OpenAI-Stream-Timeout");
        thread.setDaemon(true);
        thread.start();
    }
    static boolean isSilentTooLong(long now,long lastText,boolean everReceived){
        return com.hanco.buildai.blueprint.StreamSilencePolicy.expired(
            now,lastText,everReceived);
    }
    void textArrived(){
        begun.set(true);lastText.set(System.nanoTime());
    }
    boolean expired(){return expired.get();}
    String reason(){
        return begun.get()
            ?"OpenAI sent no new blueprint text for 3 minutes. "+
              "BuildAI ended the stalled response; previous blueprint unchanged. "+
              "Retry the request in F8."
            :"OpenAI produced no blueprint text for 8 minutes after accepting the "+
              "request. BuildAI closed the stalled stream; previous blueprint "+
              "unchanged. Retry in F8, or try the API-key model option.";
    }
    private void watch(){
        while(!closed.get()){
            long nanos=System.nanoTime();
            // Cancellation always works, even if the user disabled the
            // silence watchdog. A disabled deadline never closes the stream.
            if(progress!=null&&progress.cancellationRequested()){
                expired.set(true);
                closeInput();
                return;
            }
            boolean autoStop=progress==null||progress.autoStopEnabled();
            long baseline=Math.max(lastText.get(),progress==null?0L:
                progress.autoStopRearmedAtNanos());
            long idle=nanos-baseline;
            if(autoStop){
                if(!warned&&!begun.get()&&idle>=2L*60*1_000_000_000L){
                    warned=true;
                    if(progress!=null)progress.stage(
                        "Still waiting for model output",
                        "No blueprint text yet. Auto-stop is ON; you can "+
                        "switch it OFF here to continue waiting.");
                }
                if(com.hanco.buildai.blueprint.StreamSilencePolicy.expired(
                    nanos,lastText.get(),begun.get(),autoStop,
                    progress==null?0L:progress.autoStopRearmedAtNanos())){
                    // Recheck the user's choice just before committing to
                    // closing the stream, to avoid a stale loop snapshot.
                    if(progress==null||progress.autoStopEnabled()){
                        expired.set(true);
                        closeInput();
                        return;
                    }
                }
            }else{
                warned=false;
            }
            try{Thread.sleep(1500);}
            catch(InterruptedException e){return;}
        }
    }
    private void closeInput(){
        try{input.close();}catch(IOException ignored){}
    }
    @Override public void close(){
        closed.set(true);
        thread.interrupt();
    }
}
