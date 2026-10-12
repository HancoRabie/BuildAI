package com.hanco.buildai.blueprint;

import java.util.*;

/** Read-only QA for separated physical Minecraft chain segments.
 * Neighbor-state normalization cannot manufacture missing blocks. */
public final class ChainGapAudit {
    private ChainGapAudit(){}
    public static List<String> inspect(Blueprint b){
        int gaps=0;
        for(int y=0;y<b.height;y++)for(int z=0;z<b.length;z++)for(int x=0;x<b.width;x++){
            String state=b.get(x,y,z);
            if(!Blueprint.id(state).equals("minecraft:chain"))continue;
            int[][] axes={{1,0,0},{0,1,0},{0,0,1}};
            String[] names={"x","y","z"};
            for(int i=0;i<3;i++){
                if(!state.contains("axis="+names[i]))continue;
                int dx=axes[i][0],dy=axes[i][1],dz=axes[i][2];
                int mx=x+dx,my=y+dy,mz=z+dz;
                int ex=x+2*dx,ey=y+2*dy,ez=z+2*dz;
                if(ex>=b.width||ey>=b.height||ez>=b.length)continue;
                if(!b.isAir(mx,my,mz))continue;
                String opposite=b.get(ex,ey,ez);
                if(Blueprint.id(opposite).equals("minecraft:chain")&&
                    opposite.contains("axis="+names[i]))gaps++;
            }
        }
        return gaps==0?List.of():List.of(gaps+
            " one-block gaps between matching chain segments: add actual chains along their axis; "+
            "fence/pane normalization cannot fill chain gaps.");
    }
}
