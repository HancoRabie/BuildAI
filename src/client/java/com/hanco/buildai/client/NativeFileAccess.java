package com.hanco.buildai.client;

import org.lwjgl.util.tinyfd.TinyFileDialogs;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Native OS file picker used by ChainFrames (LWJGL tinyfiledialogs). It does
 * not depend on AWT GraphicsEnvironment/Desktop, which are often unavailable
 * in Minecraft's bundled JVM. Only explicit user button actions invoke it.
 */
public final class NativeFileAccess {
    private NativeFileAccess(){}

    public static List<Path> choose(String title,Path startingDirectory,
                                    String filterDescription,boolean multiple)
                                    throws IOException {
        String defaultPath=startingDirectory==null?"":startingDirectory.toAbsolutePath().toString();
        final String chosen;
        try{
            chosen=TinyFileDialogs.tinyfd_openFileDialog(title,defaultPath,
                null,filterDescription,multiple);
        }catch(Exception | LinkageError failure){
            throw new IOException("Native file picker could not start: "+
                failure.getClass().getSimpleName()+
                ". You may still paste the full file path.",failure);
        }
        if(chosen==null||chosen.isBlank())return List.of(); // cancellation
        List<Path> picked=new ArrayList<>();
        for(String filename:chosen.split("\\|",-1)){
            if(filename.isBlank())continue;
            try{picked.add(Path.of(filename).toAbsolutePath().normalize());}
            catch(RuntimeException bad){throw new IOException("Invalid file selected",bad);}
        }
        return List.copyOf(picked);
    }

    public static Path chooseImage(Path base) throws IOException {
        List<Path> files=choose("Select PNG or JPG for BuildAI",base,
            "PNG, JPG or JPEG images",false);
        if(files.isEmpty())return null;
        Path file=files.get(0);
        String name=file.getFileName().toString().toLowerCase(Locale.ROOT);
        if(!(name.endsWith(".png")||name.endsWith(".jpg")||name.endsWith(".jpeg")))
            throw new IOException("Select a PNG, JPG or JPEG image");
        if(!Files.isRegularFile(file))throw new IOException("Selected image not found");
        return file;
    }

    public static List<Path> chooseSchematics(Path base,boolean multiple)
        throws IOException {
        List<Path> files=choose(
            multiple?"Select schematic references (multiple allowed)":"Select a schematic",
            base,".schem, .schematic, .litematic, .nbt",multiple);
        for(Path file:files)
            if(!BlueprintLibrary.supported(file)||!Files.isRegularFile(file))
                throw new IOException("Choose existing .schem, .schematic, "+
                    ".litematic or .nbt files");
        return files;
    }

    /** Open an app-owned output directory through the OS file manager. */
    public static void openDirectory(Path folder) throws IOException {
        Path directory=folder.toAbsolutePath().normalize();
        Files.createDirectories(directory);
        String os=System.getProperty("os.name","").toLowerCase(Locale.ROOT);
        List<List<String>> commands=os.contains("win")
            ?List.of(List.of("explorer.exe",directory.toString()))
            :os.contains("mac")
                ?List.of(List.of("open",directory.toString()))
                :List.of(List.of("xdg-open",directory.toString()),
                    List.of("gio","open",directory.toString()));
        IOException last=null;
        for(List<String> command:commands){
            try{
                new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
                return;
            }catch(IOException e){last=e;}
        }
        throw new IOException("Could not open the file manager for "+directory+
            (last==null?"":": "+last.getMessage()),last);
    }
    /** Keep existing Analyze Folder behavior and exact destination. */
    public static void openStyleReferenceFolder() throws IOException {
        openDirectory(BuildAIStyleMemory.architectureDirectory());
    }
}
