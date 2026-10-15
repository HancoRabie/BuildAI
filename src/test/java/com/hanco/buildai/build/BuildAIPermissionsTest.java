package com.hanco.buildai.build;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BuildAIPermissionsTest {
    @Test void integratedHostAlwaysGetsAdminWithoutAnyVanillaCheatPermission(){
        assertEquals(BuildAIPermissions.Role.ADMIN,
            BuildAIPermissions.role(true,false,false,false),
            "Cheat-disabled singleplayer must support upload, build and undo");
    }
    @Test void lanVisitorNeverInheritsIntegratedHostPrivilege(){
        assertEquals(BuildAIPermissions.Role.NONE,
            BuildAIPermissions.role(false,false,false,false));
        assertEquals(BuildAIPermissions.Role.BUILDER,
            BuildAIPermissions.role(false,false,false,true),
            "Explicit grant must authorize non-op LAN and dedicated players");
    }
    @Test void serverOperatorAndGrantMatrix(){
        assertEquals(BuildAIPermissions.Role.BUILDER,
            BuildAIPermissions.role(false,true,false,false));
        assertEquals(BuildAIPermissions.Role.BUILDER,
            BuildAIPermissions.role(false,false,false,true));
        assertEquals(BuildAIPermissions.Role.ADMIN,
            BuildAIPermissions.role(false,true,true,false));
        assertEquals(BuildAIPermissions.Role.ADMIN,
            BuildAIPermissions.role(false,false,true,false));
    }
    @Test void builderGrantCannotEscalateToAdmin(){
        assertNotEquals(BuildAIPermissions.Role.ADMIN,
            BuildAIPermissions.role(false,false,false,true));
        assertEquals(BuildAIPermissions.Role.NONE,
            BuildAIPermissions.role(false,false,false,false),
            "Removing grant fully revokes ordinary player access");
    }
    @Test void opLevelFourAlwaysOutranksGrant(){
        assertEquals(BuildAIPermissions.Role.ADMIN,
            BuildAIPermissions.role(false,true,true,true));
    }
}
