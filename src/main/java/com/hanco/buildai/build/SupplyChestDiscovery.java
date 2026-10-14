package com.hanco.buildai.build;

/** Exact, case-insensitive BuildAI supply marker and bounded project search. */
public final class SupplyChestDiscovery {
    private SupplyChestDiscovery(){}
    public static boolean marked(String line){
        return line!=null&&"BuildAI".equalsIgnoreCase(line.trim());
    }
    public static boolean inProjectArea(int chestX,int chestY,int chestZ,
                                        int originX,int originY,int originZ,
                                        int width,int height,int length){
        if(width<1||height<1||length<1)return false;
        final int margin=24;
        return chestX>=originX-margin&&chestX<=originX+width+margin&&
            chestZ>=originZ-margin&&chestZ<=originZ+length+margin&&
            chestY>=originY-margin&&chestY<=originY+height+margin;
    }
}
