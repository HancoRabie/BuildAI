package com.hanco.buildai.blueprint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

public final class SavedBlueprintDeletionTest {
    @TempDir Path temp;

    @Test void confirmedFileIsActuallyDeletedFromDiskNotMerelyHidden() throws IOException {
        Path folder=Files.createDirectories(temp.resolve("buildai/blueprints"));
        Path schematic=Files.writeString(folder.resolve("My_Farm-20261012.schem"),"test file");
        assertTrue(Files.isRegularFile(schematic));
        SavedBlueprintDeletion.delete(folder,schematic);
        assertFalse(Files.exists(schematic),"Deletion must remove the actual save file.");
        assertTrue(Files.isDirectory(folder));
    }

    @Test void externalImportedFilesAreNeverDeleted() throws IOException {
        Path folder=Files.createDirectories(temp.resolve("buildai/blueprints"));
        Path outside=Files.writeString(temp.resolve("precious.schem"),"external build");
        assertThrows(IOException.class,()->SavedBlueprintDeletion.delete(folder,outside));
        assertTrue(Files.exists(outside));
    }

    @Test void preventsParentTraversalAndSiblingNamedFiles() throws IOException {
        Path folder=Files.createDirectories(temp.resolve("buildai/blueprints"));
        Path escaped=Files.writeString(temp.resolve("buildai/other.schem"),"keep this");
        assertThrows(IOException.class,()->SavedBlueprintDeletion.delete(
            folder,folder.resolve("../other.schem")));
        assertTrue(Files.exists(escaped));
    }

    @Test void refuseSymlinkInsteadOfDeletingTarget() throws IOException {
        Path folder=Files.createDirectories(temp.resolve("buildai/blueprints"));
        Path valuable=Files.writeString(temp.resolve("valuable.schem"),"keep");
        Path link=folder.resolve("fake.schem");
        try {
            Files.createSymbolicLink(link,valuable);
        } catch (FileSystemException | UnsupportedOperationException ex) {
            // Windows without Developer Mode/admin privileges cannot create the
            // fixture. Skip ONLY this test when symlinks are unavailable;
            // other deletion safety tests still run unchanged.
            Assumptions.assumeTrue(false,
                "This filesystem cannot create a test symlink: "+ex.getMessage());
        }
        assertThrows(IOException.class,()->SavedBlueprintDeletion.delete(folder,link));
        assertTrue(Files.exists(valuable));
        assertTrue(Files.isSymbolicLink(link));
    }

    @Test void neverDeleteNonSchematicOrDirectoryOrMissingFile() throws IOException {
        Path folder=Files.createDirectories(temp.resolve("buildai/blueprints"));
        Path notes=Files.writeString(folder.resolve("style-memory.json"),"keep");
        Path wrong=folder.resolve("missing.schem");
        assertThrows(IOException.class,()->SavedBlueprintDeletion.delete(folder,notes));
        assertThrows(IOException.class,()->SavedBlueprintDeletion.delete(folder,folder));
        assertThrows(IOException.class,()->SavedBlueprintDeletion.delete(folder,wrong));
        assertTrue(Files.exists(notes));
    }

    @Test void allowAllLibrarySchematicFormats() throws IOException {
        Path folder=Files.createDirectories(temp.resolve("buildai/blueprints"));
        for(String ext:new String[]{".schem",".schematic",".litematic",".nbt"}){
            Path file=Files.writeString(folder.resolve("saved"+ext),"test");
            SavedBlueprintDeletion.delete(folder,file);
            assertFalse(Files.exists(file),ext);
        }
    }
}
