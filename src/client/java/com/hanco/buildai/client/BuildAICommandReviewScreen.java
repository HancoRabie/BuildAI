package com.hanco.buildai.client;

import com.hanco.buildai.blueprint.Blueprint;
import com.hanco.buildai.blueprint.CommandBlockPlan;
import com.hanco.buildai.blueprint.CommandPurpose;
import com.hanco.buildai.net.CommandApprovalRequestPayload;
import com.hanco.buildai.net.CommandReviewPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import java.util.stream.Collectors;

/**
 * In-game, scrollable command checklist with a persistent selection and an
 * inspector for complete command text. Checkbox selections are never reset
 * by scrolling or resizing. The server is the authority for every approval.
 */
public final class BuildAICommandReviewScreen extends Screen {
    private static final int TEAL=0xFF69E4C6,BLUE=0xFF9BCDF0,
        LIGHT=0xFFEAF1F8,MUTED=0xFFAABECF,AMBER=0xFFFFC987,
        RED=0xFFFF9F98,DARK=0xEE152834,ROW=0xBB29465A,SELECTED=0xCC1D605D;
    private final Screen parent;
    private final CommandReviewPayload snapshot;
    private final boolean serverReview;
    private final Set<Integer> checked=new TreeSet<>();
    private int selected=0,offset=0,detailScroll=0;
    private boolean waiting=false,approved=false,error=false;
    private String status="Select commands to install. Nothing runs without server approval.";

    public BuildAICommandReviewScreen(Screen parent,Blueprint blueprint){
        this(parent,new CommandReviewPayload(blueprint.name,BlockPos.ORIGIN,
            localEntries(blueprint)),false);
    }
    public BuildAICommandReviewScreen(Screen parent,CommandReviewPayload review){
        this(parent,review,true);
    }
    private BuildAICommandReviewScreen(Screen parent,CommandReviewPayload review,boolean live){
        super(Text.literal("BuildAI | Command Commissioning"));
        this.parent=parent;this.snapshot=review;this.serverReview=live;
    }
    private static List<CommandReviewPayload.Item> localEntries(Blueprint b){
        List<CommandReviewPayload.Item> result=new ArrayList<>();
        int i=0;
        for(CommandBlockPlan.Entry e:b.commands())
            result.add(new CommandReviewPayload.Item(++i,new BlockPos(e.x(),e.y(),e.z()),
                b.get(e.x(),e.y(),e.z()),e.command(),e.auto(),e.trackOutput()));
        return result;
    }
    private int panelWidth(){return Math.min(930,Math.max(165,width-28));}
    private int left(){return (width-panelWidth())/2;}
    private int top(){return 6;}
    private boolean wide(){return panelWidth()>=560&&height>=310;}
    private int listLeft(){return left()+9;}
    private int listWidth(){return wide()?(panelWidth()*57/100)-12:panelWidth()-18;}
    private int detailLeft(){return listLeft()+listWidth()+9;}
    private int detailWidth(){return left()+panelWidth()-9-detailLeft();}
    private int listTop(){return 66;}
    private int listBottom(){return height-111;}
    private int rowHeight(){return 28;}
    private int visible(){return Math.max(1,(listBottom()-listTop())/rowHeight());}
    private int maxOffset(){return Math.max(0,snapshot.entries().size()-visible());}
    private List<CommandReviewPayload.Item> entries(){return snapshot.entries();}
    private CommandReviewPayload.Item focus(){
        return entries().isEmpty()?null:entries().get(Math.max(0,Math.min(selected,entries().size()-1)));
    }
    private ButtonWidget add(String title,int x,int y,int w,int h,Runnable action){
        return addDrawableChild(ButtonWidget.builder(
            Text.literal(textRenderer.trimToWidth(title,Math.max(20,w-12))),
            b->action.run()).dimensions(x,y,Math.max(25,w),h).build());
    }
    private void changed(){
        clearChildren();init();
    }
    private void markAll(){
        checked.clear();
        for(CommandReviewPayload.Item e:entries())checked.add(e.number());
        changed();
    }
    private void approve(boolean all){
        if(waiting){status="Still waiting on Minecraft server response.";return;}
        if(!serverReview){
            status="This is a local preview. Wait for the server's completed-build review.";
            error=true;return;
        }
        if(!all&&checked.isEmpty()){
            status="Check at least one command or use APPROVE ALL.";
            error=true;return;
        }
        if(client==null || client.getNetworkHandler()==null ||
            !ClientPlayNetworking.canSend(CommandApprovalRequestPayload.ID)){
            status="Approval link unavailable. Update BuildAI on both client and server.";
            error=true;return;
        }
        String selection=all?"all":checked.stream().map(Object::toString)
            .collect(Collectors.joining(","));
        waiting=true;error=false;
        status="Sending "+(all?entries().size():checked.size())+
            " command approvals. Waiting for the server's response...";
        ClientPlayNetworking.send(new CommandApprovalRequestPayload(selection));
        changed();
    }
    /** Actual response, including exact OP permissions and block mismatch errors. */
    public void approvalFeedback(boolean success,String reply){
        waiting=false;approved=success;error=!success;
        status=reply==null?"No server response details.":reply;
        changed();
    }
    @Override protected void init(){
        int x=left()+9,w=panelWidth()-18;
        if(height<245){
            add("BACK",x,Math.max(5,height-27),w,20,this::close);
            return;
        }
        int gap=5,third=(w-2*gap)/3,half=(w-gap)/2;
        add(checked.size()==entries().size()?"DESELECT ALL":"SELECT ALL",
            x,height-61,third,20,()->{
                if(checked.size()==entries().size()){checked.clear();changed();}
                else markAll();
            });
        add("APPROVE CHECKED ("+checked.size()+")",
            x+third+gap,height-61,third,20,()->approve(false))
            .active=serverReview&&!waiting&&!approved&&!checked.isEmpty();
        add("APPROVE ALL ("+entries().size()+")",
            x+2*(third+gap),height-61,w-2*(third+gap),20,()->approve(true))
            .active=serverReview&&!waiting&&!approved&&!entries().isEmpty();
        // Primary action: users should NEVER have to use Escape. After
        // successful commissioning this is an obvious, full-width DONE CTA;
        // before approval it is an explicit non-destructive exit.
        if(approved){
            add("DONE  >  RETURN TO MINECRAFT",x,height-34,w,21,this::returnToGame);
        }else{
            add(serverReview?"RETURN TO GAME  (REVIEW LATER)":"BACK TO BUILDAI",
                x,height-34,half,20,serverReview?this::returnToGame:this::close);
            add("SHOW FULL SCRIPT IN CHAT",x+half+gap,height-34,half,20,()->{
            var item=focus();
            if(item!=null&&client!=null&&client.player!=null){
                client.player.sendMessage(Text.literal("[BuildAI] #"+item.number()+
                    " | "+CommandPurpose.summary(item.command())+
                    " | "+item.command()),false);
                status="Full command #"+item.number()+" posted to Minecraft chat for inspection.";
            }
            }).active=!entries().isEmpty();
        }
    }
    private void returnToGame(){
        if(client!=null)client.setScreen(null);
    }
    @Override public boolean mouseScrolled(double mx,double my,double dx,double dy){
        if(height<245)return super.mouseScrolled(mx,my,dx,dy);
        if(mx>=listLeft()&&mx<=listLeft()+listWidth()&&
            my>=listTop()&&my<=listBottom()){
            int steps=Math.max(1,(int)Math.round(Math.abs(dy)*2));
            offset=Math.max(0,Math.min(maxOffset(),offset+(dy>0?-steps:steps)));
            return true;
        }
        if(wide()&&mx>=detailLeft()&&mx<=detailLeft()+detailWidth()&&
            my>=listTop()&&my<=listBottom()){
            detailScroll=Math.max(0,detailScroll+(dy>0?-2:2));
            return true;
        }
        return super.mouseScrolled(mx,my,dx,dy);
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button==GLFW.GLFW_MOUSE_BUTTON_LEFT&&height>=245&&
            mx>=listLeft()&&mx<listLeft()+listWidth()&&
            my>=listTop()&&my<listBottom()){
            int index=offset+((int)my-listTop())/rowHeight();
            if(index>=0&&index<entries().size()){
                selected=index;detailScroll=0;
                // Click the square or the row: both toggle approval; clicking
                // the command focuses details regardless.
                int n=entries().get(index).number();
                if(!checked.remove(n))checked.add(n);
                changed();
                return true;
            }
        }
        return super.mouseClicked(mx,my,button);
    }
    private void line(DrawContext ctx,String value,int x,int y,int limit,int color){
        ctx.drawTextWithShadow(textRenderer,
            textRenderer.trimToWidth(value,Math.max(16,limit)),x,y,color);
    }
    private void drawList(DrawContext ctx,int mouseX,int mouseY){
        int x=listLeft(),w=listWidth(),top=listTop(),bottom=listBottom();
        ctx.fill(x-2,top-2,x+w+2,bottom+2,0xAA0C1E2B);
        ctx.enableScissor(x,top,x+w,bottom);
        for(int n=0;n<visible();n++){
            int index=offset+n;
            if(index>=entries().size())break;
            CommandReviewPayload.Item e=entries().get(index);
            int y=top+n*rowHeight();
            boolean check=checked.contains(e.number()),foc=index==selected;
            ctx.fill(x+2,y+1,x+w-2,y+rowHeight()-2,
                foc?SELECTED:((mouseX>=x&&mouseX<x+w&&mouseY>=y&&mouseY<y+rowHeight())?
                    0xBB365568:ROW));
            int color=check?TEAL:MUTED;
            ctx.fill(x+8,y+7,x+19,y+18,0xFF071825);
            ctx.drawBorder(x+8,y+7,11,11,color);
            if(check)line(ctx,"X",x+10,y+8,10,TEAL);
            line(ctx,"#"+e.number(),x+25,y+4,42,check?TEAL:AMBER);
            line(ctx,CommandPurpose.summary(e.command()),x+69,y+4,w-84,LIGHT);
            line(ctx,e.command(),x+69,y+15,w-91,MUTED);
        }
        ctx.disableScissor();
        int total=entries().size();
        if(total>visible()){
            int scrollX=x+w-3;
            int barHeight=Math.max(12,((bottom-top)*visible())/total);
            int track=(bottom-top)-barHeight;
            int barTop=top+(track*offset)/Math.max(1,maxOffset());
            ctx.fill(scrollX,top,scrollX+3,bottom,0xFF385165);
            ctx.fill(scrollX,barTop,scrollX+3,barTop+barHeight,TEAL);
        }
    }
    private void drawDetails(DrawContext ctx){
        if(!wide())return;
        int x=detailLeft(),w=detailWidth(),y=listTop(),bottom=listBottom();
        ctx.fill(x-2,y-2,x+w+2,bottom+2,0xAA0C1E2B);
        var e=focus();if(e==null)return;
        line(ctx,"SELECTED COMMAND  #"+e.number(),x+7,y+7,w-14,TEAL);
        BlockPos world=snapshot.origin().add(e.local());
        line(ctx,"World XYZ: "+world.toShortString(),x+7,y+21,w-14,AMBER);
        line(ctx,CommandPurpose.summary(e.command()),x+7,y+35,w-14,LIGHT);
        line(ctx,e.blockState()+(e.auto()?" | Always Active":" | Redstone"),
            x+7,y+49,w-14,MUTED);
        line(ctx,"EXACT SCRIPT",x+7,y+66,w-14,BLUE);
        List<OrderedText> lines=textRenderer.wrapLines(Text.literal(e.command()),Math.max(30,w-18));
        int first=Math.min(detailScroll,Math.max(0,lines.size()-1));
        int lineTop=y+80;
        ctx.enableScissor(x+4,lineTop,x+w-4,bottom-13);
        for(int k=first;k<lines.size();k++){
            int py=lineTop+(k-first)*11;
            if(py>=bottom-12)break;
            ctx.drawTextWithShadow(textRenderer,lines.get(k),x+7,py,LIGHT);
        }
        ctx.disableScissor();
        line(ctx,"Wheel here: scroll long scripts | Checkboxes: left list",
            x+7,bottom-11,w-14,MUTED);
    }
    @Override public void renderBackground(DrawContext ctx,int mx,int my,float delta){
        int x=left(),w=panelWidth();
        ctx.fill(x-8,top(),x+w+8,height-4,DARK);
        ctx.fill(x-8,top(),x+w+8,top()+3,TEAL);
        ctx.fill(x+3,42,x+w-3,43,0xFF406276);
        if(approved&&height>=245){
            // Emerald accent around the completion action; buttons stay native
            // to Minecraft for focus, hover, scaling and accessibility.
            ctx.fill(x+7,height-37,x+w-7,height-11,0xCC15665A);
            ctx.drawBorder(x+6,height-38,w-12,28,TEAL);
        }
    }
    @Override public void render(DrawContext ctx,int mx,int my,float delta){
        super.render(ctx,mx,my,delta);
        int x=left()+9,w=panelWidth()-18;
        line(ctx,"BUILDAI  /  COMMAND COMMISSIONING",x,11,w,TEAL);
        line(ctx,snapshot.projectName(),x,26,w,BLUE);
        if(height<245){
            line(ctx,"Reduce Minecraft GUI Scale / enlarge the window.",x,50,w,AMBER);
            return;
        }
        line(ctx,entries().size()+" SCRIPTED BLOCKS    "+checked.size()+
            " CHECKED    "+(serverReview?"SERVER VERIFIED | PAUSED, NO TIMER":"LOCAL PREVIEW"),
            x,48,w,MUTED);
        drawList(ctx,mx,my);
        drawDetails(ctx);
        if(!wide()&&focus()!=null){
            line(ctx,"#"+focus().number()+": "+CommandPurpose.summary(focus().command()),
                x,listBottom()+6,w,AMBER);
        }
        int statusTop=height-103;
        List<OrderedText> lines=textRenderer.wrapLines(
            Text.literal(status),Math.max(90,w));
        int max=2;
        for(int i=0;i<Math.min(max,lines.size());i++){
            ctx.drawTextWithShadow(textRenderer,lines.get(i),x,statusTop+i*11,
                error?RED:approved?TEAL:AMBER);
        }
    }
    @Override public void close(){if(client!=null)client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
