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
    @Test void onlyOwningAdministratorCanProgramCommandBlocks(){
        var owner=java.util.UUID.randomUUID();
        var another=java.util.UUID.randomUUID();
        assertTrue(CommandAuthorization.mayApprove(owner,owner,
            BuildAIPermissions.Role.ADMIN));
        assertFalse(CommandAuthorization.mayApprove(owner,owner,
            BuildAIPermissions.Role.BUILDER),
            "Builder grants must never authorize server-command execution");
        assertFalse(CommandAuthorization.mayApprove(owner,owner,
            BuildAIPermissions.Role.NONE));
        assertFalse(CommandAuthorization.mayApprove(another,owner,
            BuildAIPermissions.Role.ADMIN),
            "Even server admins cannot approve scripts on someone else's active project");
    }
    @Test void exhaustiveSixteenCasePermissionMatrix(){
        for(int mask=0;mask<16;mask++){
            boolean host=(mask&1)!=0,op2=(mask&2)!=0,
                    op4=(mask&4)!=0,granted=(mask&8)!=0;
            var expected=host||op4?BuildAIPermissions.Role.ADMIN:
                op2||granted?BuildAIPermissions.Role.BUILDER:
                BuildAIPermissions.Role.NONE;
            assertEquals(expected,BuildAIPermissions.role(host,op2,op4,granted),
                "Host/OP2/OP4/Grant bitmask: "+mask);
        }
    }

    @Test void opLevelFourAlwaysOutranksGrant(){
        assertEquals(BuildAIPermissions.Role.ADMIN,
            BuildAIPermissions.role(false,true,true,true));
    }
}
