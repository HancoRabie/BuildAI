package com.hanco.buildai.blueprint;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Crash-resistant, LOCAL ONLY copy of the raw model output being streamed.
 * A partial JSON file is diagnostic/recovery material, NEVER a blueprint to
 * install directly. Kept on failures; removed only after successful QA.
 */
public final class ModelStreamDraftJournal implements AutoCloseable {
    private final Path folder;
    private Path output;
    private BufferedWriter writer;
    private long unflushed=0;
    private boolean closed=false;
    private boolean writable=true;

    public ModelStreamDraftJournal(Path folder){this.folder=folder;}
    public static ModelStreamDraftJournal inGameDirectory(){
        return new ModelStreamDraftJournal(
            net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir()
                .resolve("buildai").resolve("recovery"));
    }

    /** File creation and writes must never interrupt healthy AI generation. */
    public synchronized void append(String delta){
        if(closed||!writable||delta==null||delta.isEmpty())return;
        try{
            if(writer==null){
                Files.createDirectories(folder);
                String timestamp=LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                output=folder.resolve("stream-"+timestamp+"-"+
                    UUID.randomUUID().toString().substring(0,8)+".json.part");
                writer=Files.newBufferedWriter(output,StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
            }
            writer.write(delta);
            unflushed+=delta.length();
            if(unflushed>=2048){writer.flush();unflushed=0;}
        }catch(IOException ex){
            writable=false;
            try{close();}catch(Exception ignored){}
        }
    }
    public synchronized String recoveryNotice(){
        return output!=null&&Files.isRegularFile(output)
            ?" Raw streamed text saved locally to "+output.toAbsolutePath()
            :" No local streamed-text checkpoint could be saved.";
    }
    public synchronized void discard(){
        close();
        if(output!=null)try{Files.deleteIfExists(output);}catch(IOException ignored){}
    }
    @Override public synchronized void close(){
        if(closed)return;
        closed=true;
        if(writer!=null)try{writer.flush();writer.close();}
        catch(IOException ignored){}
        writer=null;
    }
}