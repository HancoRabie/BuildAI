package com.hanco.buildai.client;

import com.hanco.buildai.blueprint.Blueprint;
import com.hanco.buildai.blueprint.CommandBlockPlan;
import com.hanco.buildai.net.CommandReviewPayload;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Minecraft-native review of the server's actual numbered, coordinate-bound
 * commands. The entire structure may be built already; no countdown runs.
 * Approve selected or all only through the server OP4 command permission.
 */
public final class BuildAICommandReviewScreen extends Screen {
    private static final int MINT=0xFF70EAD0,BLUE=0xFF8CC8F1,TXT=0xFFE6F2F5,
        DIM=0xFFAEC6D1,GOLD=0xFFEAC781;
    private final Screen parent;
    private final CommandReviewPayload snapshot;
    private final boolean serverReview;
    private final Set<Integer> marked=new TreeSet<>();
    private int current=0,textPage=0;
    private String status="Commands stay inactive until the server operator approves them.";

    /** Older Design QA entry point; never pretends this local copy is server authority. */
    public BuildAICommandReviewScreen(Screen parent,Blueprint blueprint){
        this(parent,new CommandReviewPayload(blueprint.name,BlockPos.ORIGIN,
            localEntries(blueprint)),false);
    }
    /** Auto-opened from the server after actual construction stops for review. */
    public BuildAICommandReviewScreen(Screen parent,CommandReviewPayload review){
        this(parent,review,true);
    }
    private BuildAICommandReviewScreen(Screen parent,CommandReviewPayload review,boolean serverReview){
        super(Text.literal("BuildAI | Command Block Review"));
        this.parent=parent;this.snapshot=review;this.serverReview=serverReview;
    }
    private static List<CommandReviewPayload.Item> localEntries(Blueprint b){
        List<CommandReviewPayload.Item> entries=new ArrayList<>();
        int index=0;
        for(CommandBlockPlan.Entry e:b.commands())
            entries.add(new CommandReviewPayload.Item(++index,new BlockPos(e.x(),e.y(),e.z()),
                b.get(e.x(),e.y(),e.z()),e.command(),e.auto(),e.trackOutput()));
        return entries;
    }
    private List<CommandReviewPayload.Item> entries(){return snapshot.entries();}
    private int panelWidth(){return Math.min(660,Math.max(150,width-20));}
    private int left(){return (width-panelWidth())/2;}
    private int visibleLines(){return Math.max(1,(height-205)/11);}
    private List<OrderedText> wrapped(){
        if(entries().isEmpty())return textRenderer.wrapLines(
            Text.literal("No command scripts in this blueprint."),panelWidth()-24);
        return textRenderer.wrapLines(Text.literal(entries().get(current).command()),panelWidth()-24);
    }
    private void navigate(int delta){
        if(entries().isEmpty())return;
        current=Math.floorMod(current+delta,entries().size());
        textPage=0;clearChildren();init();
    }
    private void page(int direction){
        int pages=Math.max(1,(wrapped().size()+visibleLines()-1)/visibleLines());
        textPage=Math.max(0,Math.min(pages-1,textPage+direction));
    }
    private ButtonWidget control(String title,int x,int y,int w,Runnable press){
        return addDrawableChild(ButtonWidget.builder(Text.literal(title),b->press.run())
            .dimensions(x,y,Math.max(28,w),19).build());
    }
    private void toggle(){
        if(entries().isEmpty())return;
        int n=entries().get(current).number();
        if(!marked.remove(n))marked.add(n);
        clearChildren();init();
    }
    private void approve(String selection){
        if(!serverReview){
            status="Approval uses server review. Wait for BuildAI to finish and open the live command map.";
            return;
        }
        if(client==null||client.getNetworkHandler()==null)return;
        // Server validates OP4, exact number mapping, command block state and
        // pending review phase. GUI never installs/executed scripts itself.
        client.getNetworkHandler().sendChatCommand("buildai commands approve "+selection);
        status="Approval requested. Read Minecraft chat for server confirmation.";
        // Keep the screen open until a server phase transition proves success.
    }
    @Override protected void init(){
        int x=left(),w=panelWidth();
        if(height<250){
            control("BACK",x,Math.max(4,height-25),w,this::close);
            return;
        }
        int gap=5,half=(w-gap)/2,third=(w-2*gap)/3;
        int nav=height-83,act=height-58,foot=height-33;
        control("PREVIOUS COMMAND",x,nav,half,()->navigate(-1)).active=entries().size()>1;
        control("NEXT COMMAND",x+half+gap,nav,half,()->navigate(1)).active=entries().size()>1;
        control(!entries().isEmpty()&&marked.contains(entries().get(current).number())?
            "UNMARK #"+(current+1):"MARK #"+(current+1),
            x,act,third,this::toggle).active=!entries().isEmpty();
        control("APPROVE MARKED ("+marked.size()+")",x+third+gap,act,third,
            ()->approve(marked.stream().map(Object::toString).collect(Collectors.joining(","))))
            .active=serverReview&&!marked.isEmpty();
        control("APPROVE ALL",x+2*(third+gap),act,w-2*(third+gap),
            ()->approve("all")).active=serverReview&&!entries().isEmpty();
        control("TEXT <",x,foot,third,()->page(-1));
        control("TEXT >",x+third+gap,foot,third,()->page(1));
        control("BACK / LATER",x+2*(third+gap),foot,w-2*(third+gap),this::close);
    }
    @Override public void renderBackground(DrawContext ctx,int mx,int my,float delta){}
    @Override public void render(DrawContext ctx,int mx,int my,float delta){
        int x=left(),w=panelWidth();
        ctx.fill(x-7,5,x+w+7,height-4,0xF0142835);
        ctx.fill(x-7,5,x+w+7,8,MINT);
        ctx.drawCenteredTextWithShadow(textRenderer,"BUILDAI  /  COMMAND COMMISSIONING",
            width/2,14,MINT);
        if(height<250){
            ctx.drawCenteredTextWithShadow(textRenderer,
                "Reduce Minecraft GUI Scale or enlarge the window for complete command review.",
                width/2,42,GOLD);
            super.render(ctx,mx,my,delta);return;
        }
        ctx.drawCenteredTextWithShadow(textRenderer,
            textRenderer.trimToWidth(snapshot.projectName()+"  |  "+
                (serverReview?"PHYSICAL BUILD FINISHED — WAITING FOR YOU":"LOCAL PREVIEW ONLY"),w-20),
            width/2,29,BLUE);
        if(!entries().isEmpty()){
            CommandReviewPayload.Item e=entries().get(current);
            BlockPos world=snapshot.origin().add(e.local());
            ctx.drawTextWithShadow(textRenderer,
                "#"+e.number()+"/"+entries().size()+"   Local: "+e.local().toShortString()+
                (serverReview?"   World: "+world.toShortString():""),
                x+9,48,TXT);
            ctx.drawTextWithShadow(textRenderer,
                textRenderer.trimToWidth(e.blockState()+"  |  "+
                    (e.auto()?"Always Active":"Needs Redstone")+
                    (marked.contains(e.number())?"  |  MARKED FOR APPROVAL":""),w-20),
                x+9,61,marked.contains(e.number())?MINT:GOLD);
        }
        ctx.drawTextWithShadow(textRenderer,"EXACT COMMAND TO INSTALL",x+9,78,MINT);
        List<OrderedText> lines=wrapped();
        int rows=visibleLines(),pages=Math.max(1,(lines.size()+rows-1)/rows);
        textPage=Math.min(textPage,pages-1);
        for(int i=textPage*rows;i<Math.min(lines.size(),(textPage+1)*rows);i++)
            ctx.drawTextWithShadow(textRenderer,lines.get(i),
                x+10,92+(i-textPage*rows)*11,TXT);
        ctx.drawTextWithShadow(textRenderer,
            textRenderer.trimToWidth("Text page "+(textPage+1)+"/"+pages+"  |  "+
                marked.size()+" marked of "+entries().size()+"  |  Review has no countdown.",w-16),
            x+9,height-112,DIM);
        ctx.drawTextWithShadow(textRenderer,
            textRenderer.trimToWidth(status,w-16),x+9,height-101,GOLD);
        super.render(ctx,mx,my,delta);
    }
    @Override public boolean shouldPause(){return false;}
    @Override public void close(){if(client!=null)client.setScreen(parent);}
}
