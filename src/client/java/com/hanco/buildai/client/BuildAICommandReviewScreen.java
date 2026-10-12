package com.hanco.buildai.client;

import com.hanco.buildai.blueprint.Blueprint;
import com.hanco.buildai.blueprint.CommandBlockPlan;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import java.util.List;

/**
 * Fully Minecraft-native read-through of the coordinate-mapped command plan.
 * Never opens a browser, explorer or external editor. Long commands are paged
 * as wrapped text, not truncated. Authorization remains server-side /op4.
 */
public final class BuildAICommandReviewScreen extends Screen {
    private final Screen parent;
    private final Blueprint blueprint;
    private int current=0,textPage=0;
    private static final int MINT=0xFF70EAD0,TXT=0xFFE6F2F5,DIM=0xFFAEC6D1,GOLD=0xFFEAC781;
    public BuildAICommandReviewScreen(Screen parent,Blueprint blueprint){
        super(Text.literal("BuildAI | Command Block Map"));
        this.parent=parent;
        this.blueprint=blueprint;
    }
    private List<CommandBlockPlan.Entry> entries(){return blueprint.commands();}
    private int panelWidth(){return Math.min(640,Math.max(150,width-20));}
    private int x(){return (width-panelWidth())/2;}
    private int footer(){return height-28;}
    private int textStart(){return 91;}
    private int linesVisible(){return Math.max(1,(footer()-textStart()-49)/11);}
    private List<OrderedText> wrapped(){
        if(entries().isEmpty())return textRenderer.wrapLines(
            Text.literal("No command-block instructions attached to this blueprint."),panelWidth()-20);
        CommandBlockPlan.Entry e=entries().get(current);
        return textRenderer.wrapLines(Text.literal(e.command()),panelWidth()-24);
    }
    private void change(int next){
        if(entries().isEmpty())return;
        current=Math.floorMod(next,entries().size());textPage=0;
        clearChildren();init();
    }
    private void changeTextPage(int direction){
        int pageCount=Math.max(1,(wrapped().size()+linesVisible()-1)/linesVisible());
        textPage=Math.max(0,Math.min(pageCount-1,textPage+direction));
    }
    private ButtonWidget control(String label,int left,int top,int width,Runnable action){
        return addDrawableChild(ButtonWidget.builder(Text.literal(label),b->action.run())
            .dimensions(left,top,Math.max(28,width),19).build());
    }
    @Override protected void init(){
        int left=x(),w=panelWidth(),gap=5;
        if(height<190){
            control("BACK",left,Math.max(4,height-24),w,this::close);
            return;
        }
        int half=(w-gap)/2;
        int top=footer()-46;
        if(top<90)top=90; // very small scaled windows show only the first page
        control("PREVIOUS BLOCK",left,top,half,()->change(current-1))
            .active=entries().size()>1;
        control("NEXT BLOCK",left+half+gap,top,half,()->change(current+1))
            .active=entries().size()>1;
        int quarter=(w-3*gap)/4;
        control("TEXT <",left,footer(),quarter,()->changeTextPage(-1));
        control("TEXT >",left+quarter+gap,footer(),quarter,()->changeTextPage(1));
        control("SHOW IN CHAT",left+2*(quarter+gap),footer(),quarter,()->{
            if(client!=null&&client.player!=null&&!entries().isEmpty()){
                CommandBlockPlan.Entry e=entries().get(current);
                client.player.sendMessage(Text.literal("[BuildAI] #"+(current+1)+
                    " local ("+e.x()+","+e.y()+","+e.z()+"): "+e.command()),false);
            }
        });
        control("BACK",left+3*(quarter+gap),footer(),w-3*(quarter+gap),this::close);
    }
    @Override public void renderBackground(DrawContext ctx,int mx,int my,float delta){}
    @Override public void render(DrawContext ctx,int mx,int my,float delta){
        int left=x(),w=panelWidth(),top=8;
        ctx.fill(left-7,top,left+w+7,height-4,0xF0142835);
        if(height<190){
            ctx.drawCenteredTextWithShadow(textRenderer,
                textRenderer.trimToWidth("Enlarge window or reduce Minecraft GUI Scale for complete command review.",w-12),
                width/2,Math.min(height-43,25),GOLD);
            super.render(ctx,mx,my,delta);
            return;
        }
        ctx.fill(left-7,top,left+w+7,top+3,MINT);
        ctx.drawCenteredTextWithShadow(textRenderer,"COMMAND BLOCK MAP  |  IN-GAME REVIEW",
            width/2,top+9,MINT);
        ctx.drawCenteredTextWithShadow(textRenderer,
            "Each instruction is bound to one unique block coordinate",
            width/2,top+24,DIM);
        if(!entries().isEmpty()){
            CommandBlockPlan.Entry e=entries().get(current);
            String block=Blueprint.id(blueprint.get(e.x(),e.y(),e.z()))
                .replace("minecraft:","");
            ctx.drawTextWithShadow(textRenderer,"#"+(current+1)+"/"+entries().size()+
                "  Local XYZ: "+e.x()+", "+e.y()+", "+e.z(),left+10,top+42,TXT);
            ctx.drawTextWithShadow(textRenderer,
                textRenderer.trimToWidth(block+" | "+(e.auto()?"Always Active":"Needs Redstone")+
                    " | trackOutput="+e.trackOutput(),w-20),left+10,top+55,GOLD);
        }
        ctx.drawTextWithShadow(textRenderer,"EXACT STORED COMMAND",left+10,top+72,MINT);
        List<OrderedText> text=wrapped();
        int shown=linesVisible();
        int pages=Math.max(1,(text.size()+shown-1)/shown);
        textPage=Math.min(textPage,pages-1);
        for(int i=textPage*shown;i<Math.min(text.size(),(textPage+1)*shown);i++){
            ctx.drawTextWithShadow(textRenderer,text.get(i),left+10,textStart()+(i-textPage*shown)*11,TXT);
        }
        if(footer()-25>textStart())
            ctx.drawTextWithShadow(textRenderer,"Text page "+(textPage+1)+"/"+pages+
                " | Commands are inert until OP4 approval in Minecraft chat.",
                left+10,footer()-25,DIM);
        super.render(ctx,mx,my,delta);
    }
    @Override public boolean shouldPause(){return false;}
    @Override public void close(){if(client!=null)client.setScreen(parent);}
}
