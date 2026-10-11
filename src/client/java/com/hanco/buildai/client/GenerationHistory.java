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
    public static synchronized String estimate(GenerationProgress.Snapshot current,String category){
        if(current.finished())return current.failed()?"No ETA — generation failed":"Generation complete";
        load();
        List<Long> durations=samples.stream().filter(s->s.category().equals(category))
            .map(Sample::seconds).sorted().toList();
        int n=durations.size();
        if(n<2)
            return "ETA: learning from completed "+category+" builds ("+n+" sample"+(n==1?"":"s")+")";
        // A historical range is more honest than a fake countdown from unknown output length.
        long median=durations.get(n/2);
        long low=(long)Math.floor(median*(n<5?0.55:0.7));
        long high=(long)Math.ceil(median*(n<5?1.85:1.5));
        long elapsed=current.elapsedSeconds();
        if(elapsed>=high)
            return "ETA: beyond historical range; remaining time unknown ("+n+" samples)";
        long leftLow=Math.max(0,low-elapsed),leftHigh=Math.max(1,high-elapsed);
        return "Historical time remaining: "+clock(leftLow)+"–"+clock(leftHigh)+
            " ("+n+" similar builds; rough range)";
    }
    private static String clock(long seconds){
        return (seconds/60)+"m "+String.format(Locale.ROOT,"%02d",seconds%60)+"s";
    }
}
