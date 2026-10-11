package com.hanco.buildai.client;

import com.hanco.buildai.net.ProjectStatusPayload;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.*;

public final class ProjectHud {
    public static volatile ProjectStatusPayload latest;
    private ProjectHud(){}
    public static void register(){
        HudRenderCallback.EVENT.register((context,tickCounter)->{
            MinecraftClient mc=MinecraftClient.getInstance();
            ProjectStatusPayload s=latest;
            if(s==null||mc.player==null||mc.options.hudHidden)return;
            // Material hologram-style heads-up view only when standing near linked supply chest.
            BlockPos chest=s.chest();
            if(chest.getY()==-9999||mc.player.squaredDistanceTo(chest.getX()+0.5,chest.getY()+0.5,chest.getZ()+0.5)>20*20)return;
            int width=mc.getWindow().getScaledWidth(),x=Math.max(4,width-250),y=18;
            List<String> lines=new ArrayList<>();
            lines.add("BuildAI | "+s.title());
            lines.add("Stage: "+s.phase());
            lines.add("Placed: "+s.placed()+" / "+s.total());
            lines.add("Supplies: "+s.supplied()+" / "+s.required());
            for(String line:s.materials().split("\n")){
                if(lines.size()>=12)break;
                if(!line.isBlank())lines.add(line);
            }
            int panelHeight=24+lines.size()*12+34;
            context.fill(x-6,y-8,width-7,y+panelHeight,0xCF101A22);
            int offset=0;
            for(String line:lines){
                context.drawTextWithShadow(mc.textRenderer,mc.textRenderer.trimToWidth(line,232),
                    x,y+offset,offset==0?0x85F0C4:0xE2E8F0);
                offset+=12;
            }
            drawBar(context,x,y+offset+2,222,s.total()==0?0:(double)s.placed()/s.total(),0xFF32BA8B);
            drawBar(context,x,y+offset+15,222,s.required()==0?0:(double)s.supplied()/s.required(),0xFFD6A54D);
        });
    }
    private static void drawBar(DrawContext ctx,int x,int y,int width,double progress,int color){
        double pct=Math.max(0,Math.min(1,progress));
        ctx.fill(x,y,x+width,y+7,0xFF35404D);
        ctx.fill(x,y,x+(int)(width*pct),y+7,color);
    }
}
