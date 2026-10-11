package com.hanco.buildai.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Minecraft-native sign-in status modal. OpenAI credentials are entered ONLY
 * into the user's system browser, never into a Minecraft field or a WebView.
 *
 * Once OAuth finishes, the mod automatically returns to F8 with the authenticated
 * account ready. All future prompting, drafting and previewing happens in game.
 */
public final class ChatGPTConnectScreen extends Screen {
    private final BuildAIScreen parent;
    private String status="Preparing a secure browser connection...";
    private String details="You authorize BuildAI once in OpenAI's browser.";
    private boolean started=false, waiting=false;

    public ChatGPTConnectScreen(BuildAIScreen parent) {
        super(Text.literal("BuildAI | Connect with ChatGPT"));
        this.parent=parent;
    }

    @Override protected void init() {
        int panel=Math.min(450,Math.max(150,width-24));
        int x=(width-panel)/2;
        int center=height/2;
        addDrawableChild(ButtonWidget.builder(Text.literal("Return to BuildAI"),
            b->close()).dimensions(x,Math.min(height-27,center+49),panel,20).build());
        if(!started)startSignIn();
    }

    private void startSignIn(){
        started=true;
        waiting=true;
        status="Opening OpenAI sign-in in your system browser...";
        details="Approve access there. BuildAI will return here automatically.";
        ChatGPTSignIn.connect().whenComplete((result,error)->
            MinecraftClient.getInstance().execute(()->{
                waiting=false;
                if(error!=null){
                    Throwable problem=error;
                    while(problem.getCause()!=null)problem=problem.getCause();
                    status="Sign-in could not be completed.";
                    details=problem.getMessage()==null?problem.toString():problem.getMessage();
                    parent.updateStatus("ChatGPT sign-in failed: "+details);
                    return;
                }
                parent.updateStatus(result+". Your blueprint generator is ready.");
                MinecraftClient mc=MinecraftClient.getInstance();
                if(mc.currentScreen==this)mc.setScreen(parent);
                else BuildAIClient.message("ChatGPT account connected. Press F8 to generate a building.");
                // Best effort only: operating systems can block applications from stealing focus.
                try {
                    long handle=mc.getWindow().getHandle();
                    GLFW.glfwRequestWindowAttention(handle);
                    GLFW.glfwFocusWindow(handle);
                }catch(Exception ignored){}
            }));
    }

    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta){
        int panel=Math.min(450,Math.max(150,width-24));
        int x=(width-panel)/2;
        int center=height/2;
        context.fill(x-10,center-84,x+panel+10,center+83,0xDB111B27);
        super.render(context,mouseX,mouseY,delta);
        context.drawCenteredTextWithShadow(textRenderer,"BuildAI  |  Connect with ChatGPT",
            width/2,center-72,0x70ECC6);
        context.drawCenteredTextWithShadow(textRenderer,"Secure one-time sign-in",width/2,center-50,0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
            textRenderer.trimToWidth(status,panel-12),width/2,center-24,0xD6E6F1);
        context.drawCenteredTextWithShadow(textRenderer,
            textRenderer.trimToWidth(details,panel-12),width/2,center-8,0xA4B8C8);
        context.drawCenteredTextWithShadow(textRenderer,
            waiting?"Waiting for browser authorization...":
                "Return to BuildAI to retry the connection or use the offline mode.",
            width/2,center+16,0xE4C58C);
        context.drawCenteredTextWithShadow(textRenderer,
            "Your password and browser session never enter Minecraft.",
            width/2,center+32,0xB1D7C9);
    }

    @Override public boolean shouldPause(){return false;}
    @Override public void close(){
        if(client!=null)client.setScreen(parent);
    }
}
