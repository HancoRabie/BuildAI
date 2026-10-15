package com.hanco.buildai.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public final class BuildAIAccessStoreTest {
    @TempDir Path temporary;

    private Path access(String world){
        return temporary.resolve(world).resolve("buildai").resolve("access.json");
    }
    @Test void noFileDeniesAllBuilderGrants(){
        assertTrue(BuildAIAccessStore.read(access("new-world")).isEmpty());
    }
    @Test void grantsAndRevocationsSurviveFreshDiskReload(){
        Path f=access("world-1");
        UUID alice=UUID.randomUUID(),bob=UUID.randomUUID();
        BuildAIAccessStore.write(f,Set.of(alice,bob));
        assertEquals(Set.of(alice,bob),BuildAIAccessStore.read(f),
            "Builder grants survive simulated server process restart");
        BuildAIAccessStore.write(f,Set.of(bob));
        assertEquals(Set.of(bob),BuildAIAccessStore.read(f),
            "Revocation persists across simulated server process restart");
    }
    @Test void oneWorldsPermissionsNeverLeakToAnother(){
        UUID granted=UUID.randomUUID();
        BuildAIAccessStore.write(access("world-a"),Set.of(granted));
        assertEquals(Set.of(granted),BuildAIAccessStore.read(access("world-a")));
        assertTrue(BuildAIAccessStore.read(access("world-b")).isEmpty());
    }
    @Test void corruptedAndUnrecognizedAccessFilesFailClosed() throws Exception {
        Path file=access("corrupt");
        Files.createDirectories(file.getParent());
        Files.writeString(file,"{invalid json");
        assertThrows(IllegalStateException.class,()->BuildAIAccessStore.read(file));
        Files.writeString(file,"{\"version\":7,\"builders\":[]}");
        assertThrows(IllegalStateException.class,()->BuildAIAccessStore.read(file));
        Files.writeString(file,"{\"version\":1,\"builders\":[\"not-a-uuid\"]}");
        assertThrows(IllegalStateException.class,()->BuildAIAccessStore.read(file));
        Files.writeString(file,"{\"version\":1}");
        assertThrows(IllegalStateException.class,()->BuildAIAccessStore.read(file));
    }
    @Test void writeIsAtomicAndDoesNotLeaveTemporaryPermissionFiles() throws Exception {
        Path file=access("atomic");
        BuildAIAccessStore.write(file,Set.of(UUID.randomUUID()));
        BuildAIAccessStore.write(file,Set.of());
        assertTrue(BuildAIAccessStore.read(file).isEmpty());
        try(var files=Files.list(file.getParent())){
            assertEquals(1,files.count(),"No stray partial grant file survives");
        }
    }
    @Test void grantedNonOpRemainsBuilderAndCannotAccessAdminTools(){
        var granted=BuildAIPermissions.role(false,false,false,true);
        assertEquals(BuildAIPermissions.Role.BUILDER,granted);
        assertNotEquals(BuildAIPermissions.Role.ADMIN,granted);
        assertEquals(BuildAIPermissions.Role.NONE,
            BuildAIPermissions.role(false,false,false,false));
    }
}
