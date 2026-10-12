package com.hanco.buildai.blueprint;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** No Minecraft client required for project identity and viewport mathematics. */
public class StyleStudioRegressionTest {

    @Test void feedbackNeverConflatesDifferentFoldersWithSameFileName() {
        Path first=Path.of("buildai","blueprints","castle.schem").toAbsolutePath().normalize();
        Path other=Path.of("other-library","castle.schem").toAbsolutePath().normalize();
        assertNotEquals(ReviewIdentity.key(first),ReviewIdentity.key(other));
        assertTrue(ReviewIdentity.matches(ReviewIdentity.key(first),first));
        assertFalse(ReviewIdentity.matches(ReviewIdentity.key(first),other));
        assertTrue(ReviewIdentity.legacyMatches("castle.schem",first,first.getParent()));
        assertFalse(ReviewIdentity.legacyMatches("castle.schem",other,first.getParent()));
    }

    @Test void pathNormalizationPreservesIdentityForEquivalentSpellings() {
        Path file=Path.of("buildai","blueprints","city.schem");
        Path alias=Path.of("buildai","blueprints","temp","..","city.schem");
        assertEquals(ReviewIdentity.key(file),ReviewIdentity.key(alias));
    }

    @Test void studioIsBoundedWithinScaledMinecraftWindow() {
        for(int width:new int[]{240,320,640,1100}){
            for(int height:new int[]{180,200,230,260,480}){
                var v=StudioLayout.forWindow(width,height);
                assertTrue(v.x()>=0);
                assertTrue(v.x()+v.width()<=width);
                assertTrue(v.stepY()+18 < v.bodyTop(),"Step buttons must clear the content");
                assertTrue(v.bodyTop() <= v.bodyBottom());
                assertTrue(v.bodyBottom()+2 < v.statusY()+3,"Body and status must not collide");
                assertTrue(v.statusY()+10 < v.footerY(),"Status must clear the footer");
                assertTrue(v.footerY()+v.buttonHeight()<=height,"Footer must fit");
            }
        }
    }
}
