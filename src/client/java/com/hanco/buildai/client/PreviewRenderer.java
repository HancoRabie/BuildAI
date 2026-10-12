package com.hanco.buildai.client;

import com.hanco.buildai.blueprint.Blueprint;
import com.hanco.buildai.blueprint.BlueprintRotator;
import java.util.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
import net.minecraft.util.hit.BlockHitResult;

/** Lightweight exposed-surface outline; never places fake blocks in the world. */
public final class PreviewRenderer {
    private static Blueprint cached;
    private static List<BlockPos> exposed=List.of();
    private PreviewRenderer(){}
    public static void register(){
        WorldRenderEvents.AFTER_ENTITIES.register(context->{
            Blueprint b=BuildAIClient.draft;
            if(b==null||(!BuildAIClient.selecting&&!BuildAIClient.surfacePending&&!BuildAIClient.previewLocked))return;
            if(context.matrixStack()==null||context.consumers()==null)return;
            if(cached!=b)rebuild(b);
            BlockPos base=BuildAIClient.previewOrigin;
            if(base==null)return;
            Vec3d cam=context.camera().getPos();
            MatrixStack matrices=context.matrixStack();
            VertexConsumer lines=context.consumers().getBuffer(RenderLayer.getLines());
            matrices.push();
            matrices.translate(-cam.x,-cam.y,-cam.z);
            float red=BuildAIClient.selecting?1.0f:0.25f;
            float green=BuildAIClient.selecting?0.24f:0.95f;
            float blue=0.45f;
            int turns=PlacementMode.rotation();
            int width=BlueprintRotator.width(b,turns),length=BlueprintRotator.length(b,turns);
            WorldRenderer.drawBox(matrices,lines,base.getX(),base.getY(),base.getZ(),
                base.getX()+width,base.getY()+b.height,base.getZ()+length,red,green,blue,0.85f);
            // Highlight the transformed front edge so the player can distinguish
            // the main facade from the back before locking.
            int face=turns;
            double fx1=base.getX(),fx2=base.getX()+width;
            double fz1=base.getZ(),fz2=base.getZ()+length;
            if(face==0)fz2=fz1+0.02;
            else if(face==1)fx1=fx2-0.02;
            else if(face==2)fz1=fz2-0.02;
            else fx2=fx1+0.02;
            WorldRenderer.drawBox(matrices,lines,fx1,base.getY(),fz1,fx2,base.getY()+b.height,fz2,
                0.15f,0.92f,1.0f,0.95f);
            int stride=Math.max(1,(exposed.size()+1499)/1500);
            for(int i=0;i<exposed.size();i+=stride){
                BlockPos local=exposed.get(i);
                BlockPos rotated=BlueprintRotator.position(local.getX(),local.getY(),local.getZ(),
                    b.width,b.length,turns);
                double x=base.getX()+rotated.getX(),y=base.getY()+rotated.getY(),z=base.getZ()+rotated.getZ();
                WorldRenderer.drawBox(matrices,lines,x,y,z,x+1,y+1,z+1,red,green,blue,0.25f);
            }
            matrices.pop();
        });
    }
    private static void rebuild(Blueprint b){
        cached=b;
        List<BlockPos> outer=new ArrayList<>();
        for(int y=0;y<b.height;y++)for(int z=0;z<b.length;z++)for(int x=0;x<b.width;x++){
            if(b.isAir(x,y,z))continue;
            if(x==0||x==b.width-1||y==0||y==b.height-1||z==0||z==b.length-1||
               b.isAir(x-1,y,z)||b.isAir(x+1,y,z)||b.isAir(x,y-1,z)||
               b.isAir(x,y+1,z)||b.isAir(x,y,z-1)||b.isAir(x,y,z+1))
                outer.add(new BlockPos(x,y,z));
        }
        exposed=outer;
    }
}
