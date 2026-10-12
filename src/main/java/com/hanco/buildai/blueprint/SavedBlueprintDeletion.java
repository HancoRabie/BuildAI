package com.hanco.buildai.blueprint;

import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;

/**
 * Destructive local-file operation intentionally limited to a single
 * regular .schem/.schematic/.litematic/.nbt inside BuildAI's OWN save folder.
 * Never accepts arbitrary external paths, directories, symlinks or traversal.
 * Call only AFTER the player confirms the exact filename in Minecraft.
 */
public final class SavedBlueprintDeletion {
    private SavedBlueprintDeletion() {}

    public static Path requireSavedFile(Path savedFolder,Path requested) throws IOException {
        if(savedFolder==null||requested==null)
            throw new IOException("No blueprint file selected.");
        Files.createDirectories(savedFolder);
        Path folder=savedFolder.toRealPath();
        Path candidate=requested.toAbsolutePath().normalize();
        if(!candidate.getParent().equals(savedFolder.toAbsolutePath().normalize()))
            throw new IOException("Only files in BuildAI's own saved-blueprints folder can be deleted.");
        if(Files.isSymbolicLink(candidate))
            throw new IOException("Refusing to delete a symbolic link.");
        if(!Files.isRegularFile(candidate,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Saved blueprint no longer exists or is not a regular file.");
        if(!candidate.toRealPath().getParent().equals(folder))
            throw new IOException("Selected file is outside BuildAI's saved-blueprints folder.");
        String filename=candidate.getFileName().toString().toLowerCase(Locale.ROOT);
        if(!filename.endsWith(".schem")&&!filename.endsWith(".schematic")&&
           !filename.endsWith(".litematic")&&!filename.endsWith(".nbt"))
            throw new IOException("Only schematic files can be deleted here.");
        return candidate;
    }

    /** The Java file deletion is irreversible through BuildAI's interface. */
    public static void delete(Path savedFolder,Path selected) throws IOException {
        Path safe=requireSavedFile(savedFolder,selected);
        Files.delete(safe); // Never move to an internal hidden trash.
    }
}
