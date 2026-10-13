package com.hanco.buildai.client;

import com.hanco.buildai.blueprint.CommunityFeedbackPackage;
import com.hanco.buildai.blueprint.StudioLayout;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Consent-first expert corrections: package anonymized original and optional
 * corrected schematics into a ZIP. Nothing is transmitted from Minecraft.
 * The only "Submit" action opens official PUBLIC GitHub issue template,
 * where the user must intentionally attach their file.
 */
public final class BuildAICommunityFeedbackScreen extends Screen {
    private final Screen parent;
    private final Path original;
    private Path corrected,exported;
    private StudioLayout.View layout;
    private TextFieldWidget expectedField,observedField,explanationField;
    private int page=0,pillar=1,verification=0,rating=4;
    private boolean consent=false,busy=false;
    private String expected="",observed="",explanation="",status=
        "Local package only. Nothing gets uploaded without your action.";
    private static final String[] PILLARS={
        "Power & transmission","Boolean logic","Pulse engineering",
        "Clocks & timing","Memory & sequential","Analog & sensors",
        "Pistons & block updates","Items & automation","Other"
    };
    private static final String[] VERIFICATION={
        "Failure observed","Correction unverified","Corrected and tested"
    };
    private static final int BG=0xF0142230, PANEL=0xE3213643;
    private static final int MINT=0xFF6DE4C8,BODY=0xFFF0F5F7;
    private static final int DIM=0xFFABC1CB,AMBER=0xFFF4CF8D,RED=0xFFFFABA3;

    public BuildAICommunityFeedbackScreen(Screen parent,Path original,
                                           int rating,String previousNote) {
        super(Text.literal("BuildAI | Community Feedback"));
        this.parent=parent;
        this.original=original;
        this.rating=Math.max(1,Math.min(5,rating));
        this.explanation=previousNote==null?"":previousNote;
    }
    private void capture(){
        if(expectedField!=null)expected=expectedField.getText();
        if(observedField!=null)observed=observedField.getText();
        if(explanationField!=null)explanation=explanationField.getText();
    }
    private void move(int step){
        capture();page=step;clearChildren();init();
    }
    private ButtonWidget button(String label,int x,int y,int width,int height,
                                ButtonWidget.PressAction action){
        var widget=ButtonWidget.builder(
            Text.literal(textRenderer.trimToWidth(label,Math.max(35,width-10))),action)
            .dimensions(x,y,Math.max(30,width),Math.max(16,height)).build();
        addDrawableChild(widget);
        return widget;
    }
    @Override protected void init(){
        capture();
        expectedField=null;observedField=null;explanationField=null;
        layout=StudioLayout.forWindow(width,height);
        int x=layout.x(),w=layout.width(),h=layout.buttonHeight();
        int top=layout.bodyTop(),gap=6,half=(w-gap)/2;
        if(page==0){
            if(layout.bodyHeight()>=114){
                button("SELECT CORRECTED .SCHEM (OPTIONAL)",x+8,top+23,
                    w-16,h,b->selectCorrection()).active=!busy;
                button("PILLAR: "+PILLARS[pillar],x+8,top+49,half-10,h,
                    b->{pillar=(pillar+1)%PILLARS.length;move(0);}).active=!busy;
                button("RATING "+rating+"/5",x+half+gap,top+49,half-8,h,
                    b->{rating=rating%5+1;move(0);}).active=!busy;
                button("RESULT: "+VERIFICATION[verification],x+8,top+76,
                    w-16,h,b->{
                        verification=(verification+1)%VERIFICATION.length;
                        move(0);
                    }).active=!busy;
            }
            button("CANCEL",x,layout.footerY(),half,h,b->close());
            button("NEXT: TEST EVIDENCE",x+half+gap,layout.footerY(),half,h,
                b->{if(original==null)status="Select a saved build first.";
                    else move(1);}).active=original!=null&&!busy;
        }else if(page==1){
            if(layout.bodyHeight()>=105){
                expectedField=field("Expected behavior",expected,x+8,top+17,w-16,h,1100);
                observedField=field("Observed behavior",observed,x+8,top+43,w-16,h,1100);
                explanationField=field("What changed? Test details",explanation,
                    x+8,top+69,w-16,h,2200);
                button(consent?"PERMISSION CONFIRMED [YES]":
                    "I OWN THIS WORK & AGREE TO SHARE [NO]",x+8,top+96,w-16,h,
                    b->{capture();consent=!consent;move(1);});
            }
            button("BACK",x,layout.footerY(),half,h,b->move(0));
            button("PREPARE LOCAL ZIP",x+half+gap,layout.footerY(),half,h,
                b->prepare()).active=!busy&&consent&&layout.bodyHeight()>=116;
        }else{
            if(layout.bodyHeight()>=75){
                button("OPEN ZIP FOLDER",x+8,top+19,half-10,h,b->openFolder());
                button("COPY PACKAGE PATH",x+half+gap,top+19,half-8,h,
                    b->copyPath());
                button("OPEN PUBLIC SUBMISSION PAGE",x+8,top+51,w-16,h,
                    b->openSubmit());
            }
            button("BACK TO EVIDENCE",x,layout.footerY(),half,h,b->move(1));
            button("CLOSE",x+half+gap,layout.footerY(),half,h,b->close());
        }
    }
    private TextFieldWidget field(String name,String current,int x,int y,int w,
                                  int h,int length){
        var field=new TextFieldWidget(textRenderer,x,y,w,h,Text.literal(name));
        field.setMaxLength(length);
        field.setText(current);
        field.setPlaceholder(Text.literal(name));
        addDrawableChild(field);
        return field;
    }
    private void selectCorrection(){
        if(busy)return;
        try{
            List<Path> selected=NativeFileAccess.chooseSchematics(
                BlueprintLibrary.directory(),false);
            if(selected.isEmpty()){status="Correction selection cancelled.";return;}
            corrected=selected.get(0);
            status="Correction selected: "+corrected.getFileName()+
                ". Source files will not be modified.";
        }catch(Exception error){status="Could not choose correction: "+reason(error);}
    }
    private Path destination(){
        return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir()
            .resolve("buildai").resolve("community-submissions");
    }
    private void prepare(){
        if(busy||original==null)return;
        capture();
        try{
            CommunityFeedbackPackage.Report report=new CommunityFeedbackPackage.Report(
                PILLARS[pillar],VERIFICATION[verification],rating,
                observed,expected,explanation,consent);
            if(!verificationEqualsFailure()&&corrected==null){
                status="Select a corrected schematic for this verification status.";
                return;
            }
            busy=true;status="Creating sanitized local contribution ZIP...";
            CompletableFuture.supplyAsync(()->{
                try{return CommunityFeedbackPackage.prepare(original,corrected,
                    destination(),report);
                }catch(Exception e){throw new RuntimeException(e);}
            }).whenComplete((path,error)->MinecraftClient.getInstance().execute(()->{
                busy=false;
                if(error!=null){
                    status="Could not prepare ZIP: "+reason(error);
                    return;
                }
                exported=path;
                status="ZIP ready. Review it, then attach it to a PUBLIC GitHub issue.";
                move(2);
            }));
        }catch(Exception ex){status="Please complete consent and test evidence: "+reason(ex);}
    }
    private boolean verificationEqualsFailure(){
        return verification==0;
    }
    private void openFolder(){
        try{NativeFileAccess.openDirectory(destination());
            status="Opened ZIP folder. Attach the generated ZIP on GitHub.";
        }catch(Exception e){status="Could not open folder: "+reason(e);}
    }
    private void copyPath(){
        if(exported==null)return;
        MinecraftClient.getInstance().keyboard.setClipboard(exported.toString());
        status="Local contribution ZIP path copied to clipboard.";
    }
    private void openSubmit(){
        if(exported==null){status="Prepare your ZIP before opening submission.";return;}
        if(!BrowserLauncher.openBuildAICommunityFeedback()){
            status="Couldn't launch browser. Open "+
                CommunityFeedbackPackage.SUBMISSION_URL+" manually.";
            return;
        }
        status="GitHub opened. Sign in, ATTACH the ZIP, and submit the issue. "+
            "Nothing was uploaded from Minecraft.";
    }
    private static String reason(Throwable t){
        while(t.getCause()!=null)t=t.getCause();
        String s=t.getMessage();
        return s==null?t.getClass().getSimpleName():s;
    }
    private void label(DrawContext context,String message,int x,int y,int w,
                       int color,float scale){
        context.getMatrices().push();
        context.getMatrices().translate(x,y,0);
        context.getMatrices().scale(scale,scale,1);
        context.drawTextWithShadow(textRenderer,
            textRenderer.trimToWidth(message,Math.max(15,(int)(w/scale))),
            0,0,color);
        context.getMatrices().pop();
    }
    @Override public void renderBackground(DrawContext context,int mx,int my,float delta){}
    @Override public void render(DrawContext context,int mx,int my,float delta){
        if(layout==null)return;
        int x=layout.x(),w=layout.width(),top=layout.bodyTop();
        context.fill(x-7,4,x+w+7,height-4,BG);
        context.fill(x-7,4,x+w+7,7,MINT);
        context.fill(x,top-6,x+w,layout.bodyBottom()+2,PANEL);
        label(context,"BUILDAI  /  EXPERT REDSTONE CONTRIBUTIONS",x+8,12,w-16,MINT,
            layout.compact()?0.85f:1.04f);
        label(context,"LOCAL EXPORT  ->  REVIEW  ->  PUBLIC GITHUB ATTACHMENT",
            x+8,layout.compact()?22:29,w-16,DIM,0.75f);
        if(page==0){
            label(context,"SOURCE  /  "+(original==null?"SELECT A SAVED BUILD":
                original.getFileName()),x+8,top+4,w-16,BODY,0.82f);
            if(layout.bodyHeight()>=118)
                label(context,"CORRECTED  /  "+(corrected==null?"NONE — FAILURE REPORT ONLY":
                    corrected.getFileName()),x+8,top+101,w-16,AMBER,0.73f);
        }else if(page==1){
            label(context,"TEST EVIDENCE  /  expected, observed, reproduction notes",
                x+8,top+3,w-16,BODY,0.75f);
            if(layout.bodyHeight()>=139){
                label(context,"PUBLIC GitHub issue. Content licensed CC BY 4.0.",
                    x+8,top+123,w-16,AMBER,0.75f);
                label(context,"No prompts, credentials, sign text or source paths in ZIP.",
                    x+8,top+134,w-16,DIM,0.72f);
            }
        }else{
            label(context,"EXPORTED ZIP  /  "+(exported==null?"none":
                exported.getFileName()),x+8,top+4,w-16,BODY,0.78f);
            if(layout.bodyHeight()>=110){
                label(context,"This tool NEVER uploads files automatically.",
                    x+8,top+83,w-16,AMBER,0.75f);
                label(context,"Open GitHub; drag ZIP into issue, review, then submit.",
                    x+8,top+96,w-16,DIM,0.75f);
            }
        }
        if(layout.bodyHeight()<115&&page!=2)
            label(context,"Enlarge window or lower GUI Scale to see all fields.",
                x+8,top+90,w-16,AMBER,0.76f);
        label(context,status,x+5,layout.statusY()+3,w-10,
            status.startsWith("Could")||status.startsWith("Please")?RED:DIM,0.76f);
        super.render(context,mx,my,delta);
    }
    @Override public void close(){if(client!=null)client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
