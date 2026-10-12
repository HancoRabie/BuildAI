package com.hanco.buildai.client;

import com.hanco.buildai.net.ProjectStatusPayload;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/**
 * Persistent project status anywhere in-world, not only beside supply chests.
 * Shows meaningful phase, actual excavation inspections/removals, placement
 * progress, and material availability. Detailed supply hologram is proximity-only.
 */
public final class ProjectHud {
    public static volatile ProjectStatusPayload latest;
    private ProjectHud() {}
    public static void register(){
        HudRenderCallback.EVENT.register((context,tickCounter)->{
            MinecraftClient mc=MinecraftClient.getInstance();
            ProjectStatusPayload s=latest;
            if(s==null||mc.player==null||mc.options.hudHidden)return;
            int screenW=mc.getWindow().getScaledWidth();
            int panelW=Math.min(256,Math.max(118,screenW/3));
            int x=Math.max(5,screenW-panelW-8),y=12;
            BlockPos chest=s.chest();
            boolean nearChest=chest.getY()!=-2048 &&
                mc.player.squaredDistanceTo(chest.getX()+0.5,chest.getY()+0.5,chest.getZ()+0.5)<=20*20;
            List<String> lines=new ArrayList<>();
            lines.add("BuildAI | "+s.title());
            lines.add("Phase: "+label(s.phase()));
            if(s.phase().equals("EXCAVATE")){
                lines.add("Checked: "+s.inspected()+" / "+s.excavationCells());
                lines.add("Removed: "+s.removed()+" blocks (air skipped)");
            }else if(s.phase().equals("CINEMATIC_PULL")){
                lines.add("Site cleared: "+s.removed()+" blocks");
                lines.add("Black-hole suction animation");
            }else if(s.phase().equals("BUILD")||s.phase().equals("WAITING_SUPPLIES")){
                lines.add("Placed: "+s.placed()+" / "+s.total());
                if(s.phase().equals("WAITING_SUPPLIES"))lines.add("Waiting for materials");
            }else if(s.phase().equals("NORMALIZE")){
                lines.add("Repairing fence, wall and pane connections");
            }else if(s.phase().equals("CLEANUP")){
                lines.add("Removing temporary supports");
            }else if(s.phase().equals("WAITING_COMMAND_APPROVAL")){
                lines.add("Physical build finished — awaiting your review");
                lines.add("F8 > Design QA > Command Review");
                lines.add("/buildai commands approve all");
                lines.add("or approve 1,2,4 (OP4, no deadline)");
            }else if(s.phase().equals("COMMISSION")){
                lines.add("Installing approved scripts; others stay inactive");
            }else if(s.phase().equals("COMPLETE")){
                lines.add("Build complete: "+s.placed()+" blocks");
            }else if(s.phase().equals("PAUSED_ERROR")){
                lines.add("Stopped for safety. Read chat.");
            }else{
                lines.add("Placed: "+s.placed()+" / "+s.total());
            }
            if(nearChest){
                lines.add("Supplies: "+s.supplied()+" / "+s.required());
                for(String line:s.materials().split("\n")){
                    if(lines.size()>=12)break;
                    if(!line.isBlank())lines.add(line);
                }
            }
            int bars=nearChest?3:2;
            int panelHeight=17+lines.size()*12+bars*13;
            context.fill(x-6,y-7,x+panelW+3,y+panelHeight,0xE012202B);
            int pos=0;
            for(String line:lines){
                context.drawTextWithShadow(mc.textRenderer,
                    mc.textRenderer.trimToWidth(line,panelW-6),
                    x,y+pos,pos==0?0x78F1CA:pos==12?0xE7F3FA:0xDBE7ED);
                pos+=12;
            }
            int barWidth=panelW-12;
            drawBar(context,x,y+pos+3,barWidth,
                s.excavationCells()==0?0:(double)s.inspected()/s.excavationCells(),
                0xFFB37DEA);
            drawBar(context,x,y+pos+16,barWidth,
                s.total()==0?0:(double)s.placed()/s.total(),
                0xFF39CDA0);
            if(nearChest)drawBar(context,x,y+pos+29,barWidth,
                s.required()==0?1:(double)s.supplied()/s.required(),
                0xFFF0BC65);
        });
    }
    private static String label(String phase) {
        return switch(phase) {
            case "SELECT_XZ","SELECT_SURFACE","REVIEW"->"Positioning / Reviewing";
            case "EXCAVATE"->"Excavating terrain";
            case "CINEMATIC_PULL"->"Black-hole suction";
            case "BUILD"->"Constructing";
            case "WAITING_SUPPLIES"->"Waiting for supplies";
            case "CLEANUP"->"Cleaning supports";
            case "NORMALIZE"->"Normalizing connections";
            case "WAITING_COMMAND_APPROVAL"->"Command approval paused";
            case "COMMISSION"->"Programming approved commands";
            case "COMPLETE"->"Complete";
            case "CANCELLED"->"Cancelled";
            case "PAUSED_ERROR"->"Paused - safety";
            default->phase;
        };
    }
    private static void drawBar(DrawContext ctx,int x,int y,int width,double ratio,int color){
        double pct=Math.max(0,Math.min(1,ratio));
        ctx.fill(x,y,x+width,y+7,0xFF344753);
        ctx.fill(x,y,x+(int)(width*pct),y+7,color);
    }
}
