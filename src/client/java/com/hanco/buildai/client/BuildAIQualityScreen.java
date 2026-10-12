package com.hanco.buildai.client;

import com.hanco.buildai.blueprint.Blueprint;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.text.OrderedText;
import java.util.*;

/** Readable, paged architectural QA findings; informational, never auto-edits blocks. */
public final class BuildAIQualityScreen extends Screen {
    private final Screen parent;
    private final Blueprint blueprint;
    private int offset;
    private final List<String> findings;
    public BuildAIQualityScreen(Screen parent,Blueprint blueprint){
        super(Text.literal("BuildAI | Architectural Review"));
        this.parent=parent;this.blueprint=blueprint;
        this.findings=blueprint==null?List.of():List.copyOf(blueprint.warnings);
    }
    @Override protected void init(){
        int w=Math.min(510,Math.max(140,width-20)),x=(width-w)/2,gap=5;
        int footer=Math.max(25,height-36);
        if(BuildAIClient.lastResearch().verified()){
            addDrawableChild(ButtonWidget.builder(
                Text.literal("View Cited Web Inspiration Sources"),
                button->BuildAIClient.showInspirationLinks())
                .dimensions(x+5,55,w-10,18).build());
        }
        int third=(w-gap*2)/3;
        addDrawableChild(ButtonWidget.builder(Text.literal("Previous"),b->{
            offset=Math.max(0,offset-4);
        }).dimensions(x,footer,third,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Next"),b->{
            offset=Math.min(Math.max(0,findings.size()-1),offset+4);
        }).dimensions(x+third+gap,footer,third,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"),b->close())
            .dimensions(x+2*(third+gap),footer,w-2*(third+gap),20).build());
    }
    @Override public void renderBackground(DrawContext ctx,int mx,int my,float delta){}
    @Override public void render(DrawContext ctx,int mx,int my,float delta){
        int w=Math.min(510,Math.max(140,width-20)),x=(width-w)/2,footer=height-36;
        ctx.fill(x-8,8,x+w+8,height-7,0xED142630);
        ctx.drawCenteredTextWithShadow(textRenderer,"BuildAI | Architectural Review",width/2,14,0x6DEBC8);
        ctx.drawCenteredTextWithShadow(textRenderer,
            textRenderer.trimToWidth(blueprint==null?"No design":blueprint.name,w-8),
            width/2,27,0xD8E5EE);
        ctx.drawTextWithShadow(textRenderer,findings.size()+" potential issue(s). "+
            "Heuristic warnings; inspect in-game.",x+5,43,0xE6CA91);
        int y=BuildAIClient.lastResearch().verified()?84:60;
        if(findings.isEmpty()){
            ctx.drawTextWithShadow(textRenderer,"No automated warnings recorded.",x+5,y,0xA3F1C9);
            ctx.drawTextWithShadow(textRenderer,"This is not proof that rooms and stairs are walkable.",
                x+5,y+14,0xCAD3E3);
        }else{
            for(int i=offset;i<findings.size();i++){
                List<OrderedText> lines=textRenderer.wrapLines(
                    Text.literal((i+1)+". "+findings.get(i)),w-20);
                if(y+lines.size()*11>=footer-5)break;
                for(OrderedText line:lines){
                    ctx.drawTextWithShadow(textRenderer,line,x+8,y,0xE7EAF2);
                    y+=11;
                }
                y+=8;
            }
        }
        super.render(ctx,mx,my,delta);
    }
    @Override public boolean shouldPause(){return false;}
    @Override public void close(){if(client!=null)client.setScreen(parent);}
}
