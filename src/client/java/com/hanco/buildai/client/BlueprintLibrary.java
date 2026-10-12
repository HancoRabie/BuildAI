package com.hanco.buildai.client;

import com.hanco.buildai.blueprint.Blueprint;
import com.hanco.buildai.blueprint.SchematicWriter;
import net.minecraft.client.MinecraftClient;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

/**
 * Client-local library. Never requires AWT, Swing or a native file picker.
 * All disk reads/writes must be invoked from background tasks, not render ticks.
 */
public final class BlueprintLibrary {
    private BlueprintLibrary() {}
    private static final DateTimeFormatter STAMP=DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final Set<String> EXTENSIONS=Set.of(".schem",".schematic",".litematic",".nbt");

    public static Path directory() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("buildai").resolve("blueprints");
    }

    public static Path save(Blueprint blueprint) throws IOException {
        if(blueprint==null)throw new IOException("There is no blueprint to save");
        Path folder=directory();
        Files.createDirectories(folder);
        String safe=blueprint.name.replaceAll("[^\\p{Alnum}._-]+","_")
            .replaceAll("^[_ .]+|[_ .]+$","");
        if(safe.isBlank())safe="build";
        if(safe.length()>56)safe=safe.substring(0,56);
        String base=safe+"-"+LocalDateTime.now().format(STAMP);
        Path output=folder.resolve(base+".schem");
        // Prevent accidental overwrites even when multiple designs finish in the same millisecond.
        for(int n=1;Files.exists(output);n++){
            if(n>1000)throw new IOException("Could not create a unique save filename");
            output=folder.resolve(base+"-"+n+".schem");
        }
        return SchematicWriter.write(blueprint,output);
    }

    public static List<Path> files() throws IOException {
        Path folder=directory();
        Files.createDirectories(folder);
        try(Stream<Path> paths=Files.list(folder)){
            return paths.filter(Files::isRegularFile)
                .filter(BlueprintLibrary::supported)
                .sorted(Comparator.comparing((Path p)->{
                    try{return Files.getLastModifiedTime(p).toMillis();}
                    catch(IOException ignored){return 0L;}
                }).reversed())
                .limit(300)
                .toList();
        }
    }

    /** Cheap UI-only eligibility check; the delete operation performs
     * real-path, regular-file and symlink validation again on the worker.
     */
    public static boolean isLocalSavedPath(Path candidate){
        return candidate!=null && supported(candidate) &&
            candidate.toAbsolutePath().normalize().getParent().equals(
                directory().toAbsolutePath().normalize());
    }

    public record DeletionResult(Path deleted,String warning) {}
    /** Permanent delete, only for a single file in this Minecraft
     * instance's own blueprints directory; never for imported external files.
     * The caller must display an explicit irreversible-action confirmation.
     */
    public static DeletionResult deleteSaved(Path selected) throws IOException {
        com.hanco.buildai.blueprint.SavedBlueprintDeletion.delete(directory(),selected);
        String warning="";
        try {
            BuildAIStyleMemory.removeFeedbackForDeleted(selected);
        }catch(IOException problem) {
            warning="File deleted, but old review cleanup failed: "+problem.getMessage();
        }
        return new DeletionResult(selected,warning);
    }

    public static boolean supported(Path path) {
        String name=path.getFileName().toString().toLowerCase(Locale.ROOT);
        return EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    public static Path resolveUserPath(String supplied) throws IOException {
        String value=supplied==null?"":supplied.trim();
        if(value.isBlank())throw new IOException("Enter a file path or select a saved blueprint");
        if(value.startsWith("\"")&&value.endsWith("\"")&&value.length()>1)
            value=value.substring(1,value.length()-1);
        Path candidate=Path.of(value);
        if(!candidate.isAbsolute())candidate=directory().resolve(candidate);
        candidate=candidate.toAbsolutePath().normalize();
        if(!supported(candidate))throw new IOException("Use .schem, .schematic, .litematic or .nbt");
        if(!Files.isRegularFile(candidate))throw new IOException("File not found: "+candidate.getFileName());
        return candidate;
    }
}
