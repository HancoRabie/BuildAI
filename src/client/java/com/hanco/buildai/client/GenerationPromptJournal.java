package com.hanco.buildai.client;

import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * Crash/restart-safe last submitted building request, NOT credentials.
 * A stuck or cancelled request must never force a user to retype a long
 * building prompt. Stored only on the user's local Minecraft game directory.
 *
 * Does NOT store authentication, OAuth state, private architectural history
 * or access tokens. Last prompt survives both success and failure so the
 * player can explicitly rerun/edit it at any time.
 */
final class GenerationPromptJournal {
    private GenerationPromptJournal(){}

    static Path path(){
        return FabricLoader.getInstance().getGameDir().resolve("buildai")
            .resolve("last-generation-prompt.txt");
    }
    static void save(String raw) {
        if(raw==null||raw.isBlank())return;
        String prompt=raw.length()>4000?raw.substring(0,4000):raw;
        Path file=path(),temp=file.resolveSibling("last-generation-prompt.tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(temp,prompt,StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
            try {
                Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            }catch(AtomicMoveNotSupportedException ignored){
                Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);
            }
        }catch(IOException e){
            // Never fail AI generation solely because backup storage fails.
            BuildAIClient.message("Unable to back up building prompt locally: "+
                e.getClass().getSimpleName());
        }
    }
    static String load(){
        Path file=path();
        try{
            if(!Files.isRegularFile(file)||Files.size(file)>20_000)return "";
            String raw=Files.readString(file,StandardCharsets.UTF_8);
            return raw.length()>4000?raw.substring(0,4000):raw;
        }catch(IOException ignored){return "";}
    }
}
