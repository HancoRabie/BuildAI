package com.hanco.buildai.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Optional ETA estimates calibrated only from actual COMPLETED local BuildAI requests.
 * Stores aggregate elapsed time + character count, never prompts, passwords or design text.
 * An estimate is a broad range, NOT an exact model completion deadline.
 */
public final class GenerationHistory {
    private GenerationHistory() {}
    private record Sample(String category,long seconds,int chars) {}
    private static boolean loaded=false;
    private static final List<Sample> samples=new ArrayList<>();
    private static Path store(){
        return BlueprintLibrary.directory().getParent().resolve("generation-timings.tsv");
    }
    private static synchronized void load(){
        if(loaded)return;
        loaded=true;
        try{
            Path file=store();
            if(!Files.isRegularFile(file))return;
            for(String line:Files.readAllLines(file,StandardCharsets.UTF_8)){
                String[] a=line.split("\t");
                if(a.length!=3)continue;
                long secs=Long.parseLong(a[1]);int chars=Integer.parseInt(a[2]);
                if(secs>0&&secs<86400&&chars>0&&chars<2_000_000&&
                    (a[0].equals("detailed")||a[0].equals("standard")))
                    samples.add(new Sample(a[0],secs,chars));
            }
            if(samples.size()>60)samples.subList(0,samples.size()-60).clear();
        }catch(Exception ignored){}
    }
    public static String category(String prompt){
        String value=prompt==null?"":prompt.toLowerCase(Locale.ROOT);
        return value.matches("(?s).*(full.scale|detailed|server.ready|furnished|interior|replica|palace|white.house|mansion|cathedral|historical|realistic|accurate.to.scale).*")
            ?"detailed":"standard";
    }
    public static synchronized void record(GenerationProgress progress){
        if(progress==null)return;
        GenerationProgress.Snapshot s=progress.snapshot();
        if(!s.finished()||s.failed()||s.receivedCharacters()<50)return;
        load();
        samples.add(new Sample(progress.category(),Math.max(1,s.elapsedSeconds()),s.receivedCharacters()));
        if(samples.size()>60)samples.remove(0);
        try {
            Path output=store();
            Files.createDirectories(output.getParent());
            StringBuilder data=new StringBuilder();
            for(Sample sample:samples)
                data.append(sample.category()).append('\t').append(sample.seconds())
                    .append('\t').append(sample.chars()).append('\n');
            // Timing data are non-sensitive; best-effort persistence.
            Files.writeString(output,data.toString(),StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
        }catch(IOException ignored){}
    }
    /**
     * Keep the ETA visible on every request. Actual completed BuildAI
     * generations calibrate it; the current stream's live character rate
     * alone CANNOT reveal how many characters the model still plans to send.
     */
    public static synchronized String estimate(GenerationProgress.Snapshot current,String category){
        if(current.finished())return current.failed()?
            "Estimated time remaining: stopped":"Estimated time remaining: complete";
        load();
        List<Sample> similar=samples.stream()
            .filter(s->s.category().equals(category)).toList();
        boolean categoryMatched=!similar.isEmpty();
        // Early users may have one sample in another category. A broad
        // cross-category fallback is better than hiding the field, but
        // must identify the weaker evidence rather than feign precision.
        List<Sample> reference=categoryMatched?similar:samples;
        if(reference.isEmpty())
            return "Estimated time remaining: learning (no finished builds recorded)";
        List<Long> durations=reference.stream().map(Sample::seconds).sorted().toList();
        int n=durations.size();
        long median=durations.get(n/2);
        // One sample is particularly uncertain; don't call it exact.
        double lowMultiplier=n==1?0.5:n<5?0.55:0.7;
        double highMultiplier=n==1?2.0:n<5?1.85:1.5;
        long low=Math.max(1,(long)Math.floor(median*lowMultiplier));
        long high=Math.max(low+1,(long)Math.ceil(median*highMultiplier));
        long elapsed=current.elapsedSeconds();
        String basis=n+" completed "+(categoryMatched?"similar ":"other ")+
            (n==1?"build":"builds");
        if(elapsed>=high)
            return "Estimated time remaining: uncertain (past "+basis+" range)";
        long remainingLow=Math.max(0,low-elapsed);
        long remainingHigh=Math.max(1,high-elapsed);
        return "Estimated time remaining: "+clock(remainingLow)+" - "+
            clock(remainingHigh)+" (rough; "+basis+")";
    }

    private static String clock(long seconds){
        return (seconds/60)+"m "+String.format(Locale.ROOT,"%02d",seconds%60)+"s";
    }
}
