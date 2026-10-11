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
        int gap=6,half=(panel-gap)/2;
        addDrawableChild(ButtonWidget.builder(Text.literal("Open Default Browser"),
            b->{
                String url=ChatGPTSignIn.pendingSignInUrl();
                if(url.isBlank()){
                    status="Sign-in link not ready yet.";
                    return;
                }
                boolean started=BrowserLauncher.openOpenAiAuthorization(url);
                status=started?"Opening your default browser. Approve BuildAI there.":
                    "Browser is unavailable. Use Copy Sign-in Link.";
            }).dimensions(x,Math.min(height-52,center+48),half,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Copy Sign-in Link"),
            b->{
                String url=ChatGPTSignIn.pendingSignInUrl();
                if(url.isBlank()){
                    status="Sign-in link not ready yet. Please wait.";
                    return;
                }
                if(client!=null){
                    client.keyboard.setClipboard(url);
                    status="Sign-in link copied. Paste it into your preferred browser.";
                }
            }).dimensions(x+half+gap,Math.min(height-52,center+48),half,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Return to BuildAI"),
            b->close()).dimensions(x,Math.min(height-27,center+74),panel,20).build());
        if(!started)startSignIn();
    }

    private void startSignIn(){
        started=true;
        waiting=true;
        status="Opening OpenAI sign-in in your Windows default browser...";
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
        context.fill(x-10,center-94,x+panel+10,center+111,0xDB111B27);
        super.render(context,mouseX,mouseY,delta);
        context.drawCenteredTextWithShadow(textRenderer,"BuildAI  |  Connect with ChatGPT",
            width/2,center-72,0x70ECC6);
        context.drawCenteredTextWithShadow(textRenderer,"Secure one-time sign-in",width/2,center-50,0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
            textRenderer.trimToWidth(status,panel-12),width/2,center-24,0xD6E6F1);
        String hint=waiting?ChatGPTSignIn.browserStatus():details;
        context.drawCenteredTextWithShadow(textRenderer,
            textRenderer.trimToWidth(hint,panel-12),width/2,center-8,0xA4B8C8);
        context.drawCenteredTextWithShadow(textRenderer,
            waiting?"Waiting for browser approval — or copy the link into your browser.":
                "You can return to BuildAI, then retry or use the offline demo.",
            width/2,center+16,0xE4C58C);
        context.drawCenteredTextWithShadow(textRenderer,
            "Only sign in at auth.openai.com. Keep authorization links private.",
            width/2,center+34,0xB1D7C9);
    }

    @Override public boolean shouldPause(){return false;}
    @Override public void close(){
        if(client!=null)client.setScreen(parent);
    }
}
