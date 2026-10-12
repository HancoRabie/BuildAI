package com.hanco.buildai.blueprint;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** GUI Scale can be smaller in a physically larger fullscreen window. */
public class LibraryViewportTest {
    @Test void fullscreenAutoGuiScaleStillDisplaysSavedBlueprints(){
        var fullscreen=LibraryViewport.fit(418,225);
        var windowed=LibraryViewport.fit(465,480);
        assertTrue(fullscreen.compact());
        assertTrue(fullscreen.visibleItems()>=3,
            "Fullscreen must NOT hide the saved library at GUI Scale 4");
        assertTrue(windowed.visibleItems()>fullscreen.visibleItems(),
            "A smaller physical window may offer MORE logical pixels");
        assertTrue(fullscreen.listAndFooterDoNotOverlap());
        assertTrue(windowed.listAndFooterDoNotOverlap());
    }
    @Test void allPlayableScaledViewportSizesKeepListAndActionsSeparate(){
        int[][] sizes={{120,120},{220,145},{350,165},{418,225},{550,310},
                       {465,480},{960,540},{1680,900}};
        for(int[] size:sizes){
            var p=LibraryViewport.fit(size[0],size[1]);
            assertTrue(p.visibleItems()>=1,"Empty file view at "+size[0]+"x"+size[1]);
            assertTrue(p.listAndFooterDoNotOverlap(),
                "Overlapping list/status/actions at "+size[0]+"x"+size[1]+": "+p);
            assertTrue(p.statusY()<p.navY());
            assertTrue(p.navY()<p.actionY());
            assertTrue(p.actionY()<p.utilityY());
            assertTrue(p.utilityY()<size[1]);
        }
    }
}
