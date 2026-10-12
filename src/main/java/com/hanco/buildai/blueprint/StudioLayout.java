package com.hanco.buildai.blueprint;

/**
 * Pixel coordinates in Minecraft's scaled GUI space, not the physical display.
 * A bounded header, working area, status line and footer never intersect.
 */
public final class StudioLayout {
    private StudioLayout() {}
    public record View(int x, int width, int stepY, int bodyTop, int bodyBottom,
                       int statusY, int footerY, int buttonHeight, boolean compact) {
        public int bodyHeight(){return bodyBottom-bodyTop;}
    }

    public static View forWindow(int windowWidth,int windowHeight) {
        int w=Math.min(620,Math.max(100,windowWidth-20)),x=(windowWidth-w)/2;
        boolean compact=windowHeight<230;
        int buttonHeight=compact?17:20;
        int stepY=compact?29:42;
        int top=compact?52:68;
        int footerY=Math.max(top+30,windowHeight-26);
        int statusY=footerY-18;
        int bodyBottom=statusY-5;
        // Screens narrower/shorter than this are unsupported by the native GUI.
        return new View(x,w,stepY,top,bodyBottom,statusY,footerY,buttonHeight,compact);
    }
}
