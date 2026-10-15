package com.hanco.buildai.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import java.util.ArrayList;
import java.util.List;

/**
 * Server access help and simple admin controls reachable directly from F8.
 * Nothing in this screen grants permissions client-side; buttons submit
 * ordinary commands to server-side BuildAIPermissions validation.
 */
public final class BuildAIAccessScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget playerField;
    private String playerName="";
    private String feedback="Permissions are checked by the Minecraft server.";
    private int scroll=0;
    private static final String[] INFO={
        "CHEATS OFF? YES. The owner of a singleplayer world can use all BuildAI building commands, including in Survival with Allow Cheats disabled.",
        "MULTIPLAYER BUILDER: Server OP2 or a separate BuildAI builder grant. Builders can generate, import, position, construct, use supplies, safely undo and manage their own projects.",
        "MODERATORS: No vanilla OP required. A BuildAI administrator enters their moderator's ONLINE Minecraft username or exact UUID below and clicks Grant Builder.",
        "ADMIN: The singleplayer/LAN world host, or a dedicated-server OP4. Administrators can grant/revoke builders and perform force undo or approve command-block scripts.",
        "LAN GUESTS: The world owner's automatic access is NOT shared. Guests must be granted Builder access explicitly, unless they already have server OP.",
        "SECURITY: BuildAI grants don't grant Minecraft OP. No claim or region-protection integration yet: builders can change terrain through their projects. Only grant trusted players.",
        "PERSISTENCE: Access grants are stored as UUIDs in this world's buildai/access.json, so changing a username or restarting the game doesn't erase the grant.",
        "Granting access: /buildai access grant <online-name-or-uuid>. Revoking: /buildai access revoke <online-name-or-uuid>.",
        "To check your own role: /buildai access. Admins can check all grants with /buildai access list.",
        "Force undo and command-block script approval require BuildAI ADMIN rights, not merely BUILDER rights.",
        "Admin buttons below send commands to the server; only an authorized admin can change permissions. Results appear in Minecraft chat."
    };
    public BuildAIAccessScreen(Screen parent){
        super(Text.literal("BuildAI Permissions"));
        this.parent=parent;
    }
    private Text text(String s){return Text.literal(s);}
    private int panel(){return Math.min(550,Math.max(160,width-16));}
    private int x(){return (width-panel())/2;}
    private void button(String label,int x,int y,int width,int height,Runnable action){
        addDrawableChild(ButtonWidget.builder(text(label),b->action.run())
            .dimensions(x,y,Math.max(28,width),Math.max(12,height)).build());
    }
    private void send(String command){
        if(client==null||client.getNetworkHandler()==null){
            feedback="Join a world/server before editing permissions.";return;
        }
        client.getNetworkHandler().sendChatCommand(command);
        feedback="Server command sent. Review the server's permission result in chat.";
    }
    private void modify(boolean grant){
        if(playerField!=null)playerName=playerField.getText().trim();
        if(!playerName.matches("[A-Za-z0-9_]{3,16}")&&
            !playerName.matches("[0-9a-fA-F\\-]{36}")){
            feedback="Enter an online Minecraft username or UUID (not display name).";
            return;
        }
        send("buildai access "+(grant?"grant ":"revoke ")+playerName);
    }
    @Override protected void init(){
        int px=x(),w=panel(),gap=4,half=(w-gap)/2;
        int h=20;
        int bottom=Math.max(70,height-24);
        int commandsY=bottom-24;
        int editY=commandsY-24;
        int entryY=editY-24;
        playerField=new TextFieldWidget(textRenderer,px,entryY,w,h,
            text("Player username or UUID"));
        playerField.setMaxLength(36);
        playerField.setText(playerName);
        playerField.setPlaceholder(text("Online player name or UUID to authorize"));
        addDrawableChild(playerField);
        button("Grant Builder",px,editY,half,h,()->modify(true));
        button("Revoke Builder",px+half+gap,editY,half,h,()->modify(false));
        button("Check My Role (chat)",px,commandsY,half,h,()->send("buildai access"));
        button("List Grants (admin)",px+half+gap,commandsY,half,h,()->send("buildai access list"));
        button("Back to BuildAI",px,bottom,w,h,()->
            {if(client!=null)client.setScreen(parent);});
    }
    private List<OrderedText> paragraphs(){
        List<OrderedText> lines=new ArrayList<>();
        for(String s:INFO){
            lines.addAll(textRenderer.wrapLines(text(s),panel()-14));
            lines.add(text(" ").asOrderedText());
        }
        return lines;
    }
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double horizontal,double vertical){
        int max=Math.max(0,paragraphs().size()-Math.max(1,(Math.max(27,height-153)-45)/12));
        scroll=Math.max(0,Math.min(max,scroll-(int)Math.signum(vertical)*3));
        return true;
    }
    @Override public void render(DrawContext ctx,int mouseX,int mouseY,float delta){
        super.render(ctx,mouseX,mouseY,delta);
        ctx.drawCenteredTextWithShadow(textRenderer,"BuildAI / Access & Permissions",
            width/2,9,0x69E4B7);
        int px=x();
        List<OrderedText> lines=paragraphs();
        int visible=Math.max(1,(Math.max(27,height-153)-45)/12);
        scroll=Math.min(scroll,Math.max(0,lines.size()-visible));
        int start=34;
        for(int i=scroll;i<Math.min(lines.size(),scroll+visible);i++)
            ctx.drawTextWithShadow(textRenderer,lines.get(i),px+4,
                start+(i-scroll)*12,0xE0E7EC);
        int infoY=Math.max(27,height-153);
        ctx.drawTextWithShadow(textRenderer,"Scroll here for server setup instructions",
            px,infoY,0x7BAFBB);
        ctx.drawTextWithShadow(textRenderer,
            textRenderer.trimToWidth(feedback,panel()-4),px,
            Math.max(45,height-142),0xE8D28A);
    }
    @Override public void close(){if(client!=null)client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
