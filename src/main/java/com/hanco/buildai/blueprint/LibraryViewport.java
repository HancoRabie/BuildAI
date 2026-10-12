package com.hanco.buildai.blueprint;

/**
 * Pixel-space layout for Minecraft's *scaled* GUI dimensions, not monitor
 * resolution. A fullscreen window may have FEWER logical pixels than a
 * smaller window when Minecraft chooses a larger GUI Scale.
 *
 * This keeps at least one file visible at ordinary playable heights and
 * always reserves separate bands for list, status, paging and actions.
 */
public record LibraryViewport(
    boolean compact,int listTop,int rowStep,int rowHeight,
    int statusY,int navY,int actionY,int utilityY,int visibleItems) {

    public static LibraryViewport fit(int width,int height) {
        boolean compact=height<330 || width<680;
        int listTop=compact?33:67;
        int step=compact?21:28;
        int rowHeight=compact?18:22;
        int utilityY=height-26;
        int actionY=utilityY-23;
        int navY=actionY-23;
        int statusY=navY-15;
        int listEnd=statusY-6;
        int capacity=(listEnd-listTop)/step;
        // At below ~150 logical pixels there is no physical space for a
        // readable file list AND three action rows; use the minimum
        // functional one-row browsing mode instead of silently hiding files.
        int visible=Math.max(1,Math.min(40,capacity));
        return new LibraryViewport(compact,listTop,step,rowHeight,
            statusY,navY,actionY,utilityY,visible);
    }
    public boolean listAndFooterDoNotOverlap(){
        return listTop+(visibleItems-1)*rowStep+rowHeight <=statusY-6;
    }
}
