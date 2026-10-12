package com.hanco.buildai.client;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Client-only persisted setting. Online research is ON by default. */
public final class InspirationSettings {
    private InspirationSettings(){}
    private static volatile Boolean enabled;

    private static Path file(){
        return BlueprintLibrary.directory().getParent().resolve("inspiration-settings.json");
    }

    public static synchronized boolean enabled(){
        if(enabled!=null)return enabled;
        try {
            Path path=file();
            if(Files.isRegularFile(path)){
                if(Files.size(path)>4096)throw new IOException("Oversized settings");
                JsonObject obj=JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8))
                    .getAsJsonObject();
                if(obj.has("onlineResearch")&&obj.get("onlineResearch").isJsonPrimitive()) {
                    enabled=obj.get("onlineResearch").getAsBoolean();
                    return enabled;
                }
            }
        }catch(Exception ex){
            System.err.println("[BuildAI] Could not read inspiration setting: "+ex.getClass().getSimpleName());
        }
        enabled=true;
        return true;
    }

    public static synchronized void setEnabled(boolean value) throws IOException {
        Path target=file();
        Files.createDirectories(target.getParent());
        JsonObject obj=new JsonObject();
        obj.addProperty("onlineResearch",value);
        Path temp=target.resolveSibling("inspiration-settings.tmp");
        Files.writeString(temp,new GsonBuilder().setPrettyPrinting().create().toJson(obj),
            StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
        try {
            Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }catch(AtomicMoveNotSupportedException ex){
            Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);
        }
        enabled=value;
    }
}
