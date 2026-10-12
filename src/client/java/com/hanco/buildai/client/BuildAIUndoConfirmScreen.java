package com.hanco.buildai.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Deliberate in-game acknowledgement before destructive force rollback. */
public final class BuildAIUndoConfirmScreen extends Screen {
    private final Screen parent;
    public BuildAIUndoConfirmScreen(Screen parent){
        super(Text.literal("BuildAI | Force Undo Confirmation"));
        this.parent=parent;
    }
    @Override protected void init(){
        int width=Math.min(400,Math.max(130,this.width-20)),x=(this.width-width)/2;
        int y=Math.max(45,this.height/2+25),gap=5,half=(width-gap)/2;
        if(y+20>this.height-4)y=this.height-25;
        addDrawableChild(ButtonWidget.builder(Text.literal("CANCEL"),b->close())
            .dimensions(x,y,half,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("FORCE RESTORE"),b->{
            if(client!=null&&client.getNetworkHandler()!=null){
                client.getNetworkHandler().sendChatCommand("buildai undo force");
                BuildAIClient.message("Force undo requested: check /buildai status. "+
                    "The server must approve OP4 and verify the in-memory rollback journal.");
            }
            close();
        }).dimensions(x+half+gap,y,half,20).build());
    }
    @Override public void renderBackground(DrawContext ctx,int mx,int my,float dt){}
    @Override public void render(DrawContext ctx,int mx,int my,float delta){
        int w=Math.min(400,Math.max(130,width-20)),x=(width-w)/2;
        int top=Math.max(8,height/2-47);
        ctx.fill(x-8,top-6,x+w+8,Math.min(height-2,height/2+54),0xF0202934);
        ctx.fill(x-8,top-6,x+w+8,top-3,0xFFFFAA6B);
        ctx.drawCenteredTextWithShadow(textRenderer,"FORCE UNDO | OPERATOR LEVEL 4",width/2,top+2,0xFFFFC68C);
        String[] lines={
            "Restores recorded original blocks in controlled batches.",
            "Conflicting containers, scripts and player edits MAY BE LOST.",
            "No material refunds. Cannot restore snapshots after restart.",
            "Bedrock remains protected. Make a world backup first."
        };
        int start=top+16;
        for(int i=0;i<lines.length;i++)
            ctx.drawCenteredTextWithShadow(textRenderer,
                textRenderer.trimToWidth(lines[i],w-14),width/2,start+i*10,0xFFE1E9EE);
        super.render(ctx,mx,my,delta);
    }
    @Override public void close(){if(client!=null)client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
