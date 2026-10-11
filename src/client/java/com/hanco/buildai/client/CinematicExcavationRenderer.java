package com.hanco.buildai.client;

import com.hanco.buildai.net.CinematicExcavationPayload;
import java.util.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.*;

/**
 * Cosmetic cinematic, never spawns collectible items or server entities.
 *
 * PREPARE: occlude the construction volume while chunk-safe world clearing
 *          happens on the server. This prevents visible layer-by-layer digging.
 * PULL:    after every affected block is already air, reveal the empty site
 *          simultaneously and spiral sampled phantom fragments into a void core.
 * BUILD:   begins only after the server's matching 80-tick cinematic pause.
 */
public final class CinematicExcavationRenderer {
    private static CinematicExcavationPayload active;
    private static long startedTick;
    private static long startedSystemNanos;
    private CinematicExcavationRenderer(){}

    public static void receive(CinematicExcavationPayload payload){
        MinecraftClient mc=MinecraftClient.getInstance();
        if(payload.stage()==CinematicExcavationPayload.STOP){
            active=null;return;
        }
        active=payload;
        startedTick=mc.world==null?0:mc.world.getTime();
        startedSystemNanos=System.nanoTime();
    }
    public static void reset(){active=null;}
    public static void register(){
        WorldRenderEvents.AFTER_ENTITIES.register(context->{
            CinematicExcavationPayload s=active;
            MinecraftClient mc=MinecraftClient.getInstance();
            if(s==null||mc.world==null||context.matrixStack()==null||context.consumers()==null)return;
            if(s.stage()==CinematicExcavationPayload.PREPARE &&
                System.nanoTime()-startedSystemNanos>5L*60*1_000_000_000L){
                active=null;return; // failsafe for disconnects or lost project state
            }
            double elapsed=Math.max(0,mc.world.getTime()-startedTick);
            double t=s.durationTicks()<=0?0:Math.min(1.0,elapsed/s.durationTicks());
            if(s.stage()==CinematicExcavationPayload.PULL && t>=1){
                active=null;return;
            }
            BlockPos origin=s.origin();
            Vec3d center=new Vec3d(origin.getX()+s.width()/2.0,
                origin.getY()+s.height()/2.0,origin.getZ()+s.length()/2.0);
            Vec3d viewpoint=context.camera().getPos();
            // No expensive geometry is assembled while out of render distance.
            if(center.squaredDistanceTo(viewpoint)>256*256)return;
            MatrixStack matrices=context.matrixStack();
            matrices.push();
            matrices.translate(-viewpoint.x,-viewpoint.y,-viewpoint.z);
            VertexConsumer lines=context.consumers().getBuffer(RenderLayer.getLines());
            VertexConsumer filled=context.consumers().getBuffer(RenderLayer.getDebugFilledBox());
            if(s.stage()==CinematicExcavationPayload.PREPARE){
                // Opaque effect envelope: blocks are cleared in safe server batches
                // behind it, and visually vanish as one event when PULL begins.
                WorldRenderer.renderFilledBox(matrices,filled,
                    origin.getX()-0.03,origin.getY()-0.03,origin.getZ()-0.03,
                    origin.getX()+s.width()+0.03,
                    origin.getY()+s.height()+0.03,
                    origin.getZ()+s.length()+0.03,
                    0.025f,0.007f,0.045f,0.97f);
                WorldRenderer.drawBox(matrices,lines,
                    origin.getX(),origin.getY(),origin.getZ(),
                    origin.getX()+s.width(),origin.getY()+s.height(),origin.getZ()+s.length(),
                    0.50f,0.11f,0.89f,0.85f);
            }
            double coreRadius=(s.stage()==CinematicExcavationPayload.PULL?0.8+0.8*Math.sin(t*Math.PI):0.9);
            WorldRenderer.renderFilledBox(matrices,filled,
                center.x-coreRadius,center.y-coreRadius,center.z-coreRadius,
                center.x+coreRadius,center.y+coreRadius,center.z+coreRadius,
                0.007f,0.001f,0.012f,1.0f);
            for(int i=0;i<28;i++){
                double angle=(i/28.0)*Math.PI*2+mc.world.getTime()*0.11;
                double r=coreRadius*(1.35+0.15*Math.sin(angle*3));
                double x=center.x+Math.cos(angle)*r;
                double z=center.z+Math.sin(angle)*r;
                double y=center.y+0.28*Math.sin(angle*2);
                WorldRenderer.drawBox(matrices,lines,x-0.085,y-0.085,z-0.085,
                    x+0.085,y+0.085,z+0.085,0.69f,0.18f,0.95f,0.95f);
            }
            if(s.stage()==CinematicExcavationPayload.PULL) {
                for(int i=0;i<s.samples().size();i++){
                    BlockPos p=s.samples().get(i);
                    Vec3d source=Vec3d.ofCenter(p);
                    double remaining=Math.pow(1.0-t,1.5);
                    Vec3d off=source.subtract(center);
                    double spin=t*(5.0*Math.PI)+(i%13)*0.13;
                    double cs=Math.cos(spin),sn=Math.sin(spin);
                    double x=center.x+(off.x*cs-off.z*sn)*remaining;
                    double z=center.z+(off.x*sn+off.z*cs)*remaining;
                    double y=center.y+off.y*remaining+Math.sin(spin+i)*0.35*remaining;
                    double size=0.22*(1.0-t)+0.035;
                    float red=i%4==0?0.90f:0.57f;
                    float green=i%4==0?0.67f:0.36f;
                    WorldRenderer.drawBox(matrices,lines,x-size,y-size,z-size,
                        x+size,y+size,z+size,red,green,0.95f,(float)(0.9*(1.0-t)));
                }
            }
            matrices.pop();
        });
    }
    public static void tick(){
        MinecraftClient mc=MinecraftClient.getInstance();
        CinematicExcavationPayload s=active;
        if(mc.world==null){active=null;return;}
        if(s==null)return;
        Vec3d center=new Vec3d(s.origin().getX()+s.width()/2.0,
            s.origin().getY()+s.height()/2.0,s.origin().getZ()+s.length()/2.0);
        if(mc.player==null||mc.player.squaredDistanceTo(center)>180*180)return;
        net.minecraft.util.math.random.Random rand=mc.world.random;
        for(int i=0;i<8;i++){
            double a=rand.nextDouble()*Math.PI*2;
            double r=1.0+rand.nextDouble()*2.0;
            double x=center.x+Math.cos(a)*r,z=center.z+Math.sin(a)*r;
            double y=center.y+(rand.nextDouble()-0.5)*2.2;
            mc.world.addParticle(i%3==0?ParticleTypes.END_ROD:ParticleTypes.PORTAL,
                x,y,z,(center.x-x)*0.13,(center.y-y)*0.13,(center.z-z)*0.13);
        }
    }
}
