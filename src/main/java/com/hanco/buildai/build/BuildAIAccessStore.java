package com.hanco.buildai.build;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * World-scoped, durable allowlist IO. No Minecraft server is needed to test
 * read/write/reload/revocation or corruption handling. Invalid files FAIL
 * CLOSED rather than granting any role.
 */
public final class BuildAIAccessStore {
    private BuildAIAccessStore(){}
    public static Set<UUID> read(Path file){
        if(!Files.exists(file))return Set.of();
        try{
            JsonObject data=JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8))
                .getAsJsonObject();
            if(!data.has("version")||data.get("version").getAsInt()!=1)
                throw new IllegalArgumentException("Unsupported/missing access file version");
            JsonArray arr=data.getAsJsonArray("builders");
            if(arr==null)throw new IllegalArgumentException("Missing builders array");
            Set<UUID> granted=new LinkedHashSet<>();
            for(var entry:arr)granted.add(UUID.fromString(entry.getAsString()));
            return Set.copyOf(granted);
        }catch(Exception e){
            throw new IllegalStateException("BuildAI access file invalid at "+file+
                " (fail closed): "+e.getMessage(),e);
        }
    }
    public static void write(Path file,Set<UUID> builders){
        try{
            Files.createDirectories(file.getParent());
            JsonObject data=new JsonObject();
            data.addProperty("version",1);
            JsonArray arr=new JsonArray();
            builders.stream().map(UUID::toString).sorted().forEach(arr::add);
            data.add("builders",arr);
            Path temp=Files.createTempFile(file.getParent(),"access-", ".tmp");
            try{
                Files.writeString(temp,data.toString(),StandardCharsets.UTF_8);
                try{
                    Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
                }catch(AtomicMoveNotSupportedException e){
                    Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);
                }
            }finally{Files.deleteIfExists(temp);}
        }catch(Exception e){
            throw new IllegalStateException("Cannot save BuildAI access grants: "+
                e.getMessage(),e);
        }
    }
}
