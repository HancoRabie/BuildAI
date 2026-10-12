package com.hanco.buildai.blueprint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Validated geometry checkpoints for a multi-request generation. A transient
 * service failure after completing 3 floors must not force all 3 floors to be
 * re-designed. Never stores OAuth tokens, billing keys or model conversations.
 *
 * Scoped by hash(original prompt, model, reference handbook, pipeline version).
 * Files are private to BuildAI's Minecraft game directory. The requested
 * prompt is never stored in a checkpoint filename.
 */
public final class StagedFarmCheckpoints {
    private static final String FORMAT="staged-farm-v2-ports";
    private final Path dir;
    public StagedFarmCheckpoints(Path root,String request,String model,String reference){
        if(root==null)throw new IllegalArgumentException("Missing checkpoint root");
        this.dir=root.resolve(fingerprint(FORMAT+"\n"+request+"\n"+model+"\n"+reference));
    }
    static String fingerprint(String input){
        try{
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(
                input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0,32);
        }catch(Exception impossible){throw new IllegalStateException(impossible);}
    }
    private Path stageFile(int index){
        if(index<0||index>15)throw new IllegalArgumentException("Invalid stage "+index);
        return dir.resolve("stage-"+index+".json");
    }
    /** Start a NEW generation cleanly after a previously completed project.
     * Incomplete runs keep their prior validated stage JSON for resumption.
     */
    public void begin() {
        try{
            Files.createDirectories(dir);
            if(Files.exists(dir.resolve(".complete"),LinkOption.NOFOLLOW_LINKS)){
                for(int i=0;i<16;i++)Files.deleteIfExists(stageFile(i));
                Files.deleteIfExists(dir.resolve(".complete"));
            }
        }catch(IOException ignored){
            // Checkpoints are best-effort; inability to write must never
            // stop the actual architectural model request.
        }
    }
    public String load(int index){
        Path file=stageFile(index);
        try{
            if(Files.isSymbolicLink(file)||!Files.isRegularFile(file)||
               Files.size(file)>1_500_000)return null;
            return Files.readString(file,StandardCharsets.UTF_8);
        }catch(IOException ignored){return null;}
    }
    public boolean save(int index,String json){
        if(json==null||json.length()>1_450_000)return false;
        Path file=stageFile(index),temporary=dir.resolve("stage-"+index+".tmp");
        try{
            Files.createDirectories(dir);
            Files.writeString(temporary,json,StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
            try{Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException ignored){
                Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        }catch(IOException ignored){return false;}
    }
    public void discard(int index){
        try{Files.deleteIfExists(stageFile(index));}catch(IOException ignored){}
    }
    /** Subsequent identical user requests start fresh instead of silently
     * reusing a previous SUCCESSFUL design or preventing a remake.
     */
    public void complete(){
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(".complete"),"done",
                StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
        }catch(IOException ignored){}
    }
}
