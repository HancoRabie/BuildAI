package com.hanco.buildai.client;

import com.hanco.buildai.blueprint.StudioLayout;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Feedback belongs to the selected schematic. Switching projects restores
 * that project's own saved review or separate unsaved draft, never the last
 * selected project's text. The screen also routes to the style tools.
 */
public final class BuildAIFeedbackScreen extends Screen {
    private static final int BG=0xF0142230, PANEL=0xD9213443, MINT=0xFF6DE4C8;
    private static final int BLUE=0xFF91C4E8, GOLD=0xFFF3CD89, LIGHT=0xFFE4EFF4;
    private static final int DIM=0xFFAAC1CE, RED=0xFFFFA99F;
    private final Screen parent;
    private final Map<String,Draft> drafts=new HashMap<>();
    private List<Path> builds=List.of();
    private Path active;
    private int activeIndex=-1, mode=0, stars=4;
    private TextFieldWidget noteField,pathField;
    private String noteText="",pathText="",status="Select a project. Feedback is stored per schematic.";
    private boolean busy;
    private StudioLayout.View layout;
    private BuildAIStyleMemory.SavedFeedback saved;
    private record Draft(int rating,String note){}

    public BuildAIFeedbackScreen(Screen parent) {
        super(Text.literal("BuildAI | Feedback & Styles"));
        this.parent=parent;
    }

    private void loadBuilds(){
        try{
            builds=BlueprintLibrary.files();
            if(active==null&&!builds.isEmpty()){
                activeIndex=0;
                loadProject(builds.get(0),false);
            }
        }catch(Exception ex){status="Could not list your builds: "+ex.getMessage();}
    }
    private static String key(Path path){return path.toAbsolutePath().normalize().toString();}
    private void capture(){
        if(active!=null&&noteField!=null){
            noteText=noteField.getText();
            drafts.put(key(active),new Draft(stars,noteText));
        }
        if(pathField!=null)pathText=pathField.getText();
    }
    private void loadProject(Path project,boolean keepDraft){
        active=project;
        noteField=null;
        try{
            saved=BuildAIStyleMemory.feedbackFor(project);
            Draft cached=keepDraft?drafts.get(key(project)):null;
            if(cached!=null) {
                stars=cached.rating();noteText=cached.note();
                status="Restored this project's unsaved edits.";
            }else if(saved!=null) {
                stars=saved.rating();noteText=saved.comment();
                status=saved.legacy()
                    ?"Loaded an older filename-only review. Save again to bind it to this exact file."
                    :"Saved feedback loaded for "+project.getFileName()+".";
            }else{
                stars=4;noteText="";
                status="No feedback saved for "+project.getFileName()+".";
            }
        }catch(Exception ex){
            stars=4;noteText="";saved=null;
            status="Feedback could not be loaded: "+ex.getMessage();
        }
    }
    private void selectProject(Path file,int index){
        if(busy)return;
        capture();
        activeIndex=index;
        loadProject(file,true);
        rebuild();
    }
    private void moveProject(int delta){
        if(builds.isEmpty()){
            status="No saved builds. Use Other File to select a schematic.";
            return;
        }
        int next=activeIndex<0?(delta>0?0:builds.size()-1):
            Math.floorMod(activeIndex+delta,builds.size());
        selectProject(builds.get(next),next);
    }
    private void changeMode(int next){
        if(busy)return;
        capture();mode=next;rebuild();
    }
    private void rebuild(){clearChildren();init();}
    private ButtonWidget button(String label,int x,int y,int w,int h,ButtonWidget.PressAction press){
        var b=ButtonWidget.builder(Text.literal(textRenderer.trimToWidth(label,Math.max(25,w-10))),press)
            .dimensions(x,y,Math.max(25,w),Math.max(12,h)).build();
        addDrawableChild(b);
        return b;
    }

    @Override protected void init(){
        // Screen.init is also called after window resize. Capture input first,
        // then detach old widgets so tabs cannot reuse a stale text field.
        capture();
        noteField=null;
        pathField=null;
        layout=StudioLayout.forWindow(width,height);
        loadBuilds();
        int x=layout.x(),w=layout.width(),h=layout.buttonHeight(),gap=5;
        int half=(w-gap)/2,tab=(w-2*gap)/3;
        button("MY BUILD FEEDBACK",x,layout.stepY(),tab,18,b->changeMode(0));
        button("STYLE REFERENCES",x+tab+gap,layout.stepY(),tab,18,b->changeMode(1));
        button("COMMUNITY SHARE",x+2*(tab+gap),layout.stepY(),
            w-2*(tab+gap),18,b->changeMode(3));
        int y=layout.bodyTop(), available=layout.bodyHeight();
        if(available>=(layout.compact()?106:110)){
            if(mode==0){
                int third=(w-16-2*gap)/3;
                button("< PREVIOUS",x+8,y+17,third,h,b->moveProject(-1)).active=!busy;
                button("OTHER FILE",x+8+third+gap,y+17,third,h,b->changeMode(2)).active=!busy;
                button("NEXT >",x+8+2*(third+gap),y+17,third,h,b->moveProject(1)).active=!busy;

                noteField=new TextFieldWidget(textRenderer,x+8,y+61,w-16,h,Text.literal("Feedback for this build"));
                noteField.setMaxLength(1100);noteField.setText(noteText);
                noteField.setPlaceholder(Text.literal("What looked great? What needs improving?"));
                addDrawableChild(noteField);
                // Rate / Save / Delete share one row; this keeps the
                // feedback editor usable at fullscreen Minecraft GUI scale
                // without pushing the footer or obscuring the saved note.
                int actions=(w-16-2*gap)/3;
                button("RATING "+stars+"/5",x+8,y+87,actions,h,b->{
                    stars=stars%5+1;
                    b.setMessage(Text.literal("RATING "+stars+"/5"));
                }).active=!busy;
                button("SAVE FEEDBACK",x+8+actions+gap,y+87,actions,h,
                    b->saveFeedback()).active=!busy;
                ButtonWidget delete=button("DELETE FILE...",x+8+2*(actions+gap),
                    y+87,w-16-2*(actions+gap),h,b->confirmDeleteProject());
                delete.active=!busy&&active!=null&&BlueprintLibrary.isLocalSavedPath(active);
                delete.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(
                    Text.literal("Permanently delete the selected saved schematic from "+
                        "Minecraft's files. Requires explicit confirmation.")));
            }else if(mode==1){
                button("ANALYZE A WHOLE BUILD",x+8,y+20,w-16,h,b->{
                    if(!busy){capture();client.setScreen(new BuildAIArchitectureScreen(this));}
                });
                button("LEARN SIGN-LABELED DETAILS",x+8,y+49,w-16,h,b->learnDecorations())
                    .active=active!=null&&!busy;
            }else if(mode==3){
                button("CONTRIBUTE REDSTONE FEEDBACK...",x+8,y+20,w-16,h,
                    b->shareFeedback()).active=active!=null&&!busy;
            }else if(mode==2){
                int browse=Math.min(110,Math.max(70,w/3));
                pathField=new TextFieldWidget(textRenderer,x+8,y+21,
                    w-20-browse,h,Text.literal("External schematic path"));
                pathField.setMaxLength(2048);pathField.setText(pathText);
                pathField.setPlaceholder(Text.literal("Schematic path"));
                addDrawableChild(pathField);
                button("BROWSE FILE",x+w-8-browse,y+21,browse,h,
                    b->browseExternal()).active=!busy;
                button("USE THIS FILE",x+8,y+50,half-10,h,b->useExternal()).active=!busy;
                button("CANCEL",x+half+gap,y+50,half-8,h,b->changeMode(0)).active=!busy;
            }
        }
        button("BACK TO BUILDAI",x,layout.footerY(),w,h,b->close());
    }

    /** Separate opt-in contribution flow: local feedback stays private
     * unless the player explicitly prepares and submits a public issue. */
    private void shareFeedback(){
        if(busy||active==null){
            status="Select a saved build first; no data was shared.";
            return;
        }
        capture();
        if(client!=null)client.setScreen(new BuildAICommunityFeedbackScreen(
            this,active,stars,noteText));
    }

    private void confirmDeleteProject(){
        if(busy||active==null||!BlueprintLibrary.isLocalSavedPath(active)){
            status="Only blueprints saved in BuildAI's own folder can be deleted. "+
                "External imports are protected.";
            return;
        }
        capture();
        Path doomed=active;
        if(client!=null)client.setScreen(new BlueprintDeleteConfirmScreen(
            this,doomed,result->{
                // File is already deleted on the worker thread. Remove
                // unsaved text for only that project; don't transfer its
                // note/rating to the next selected schematic.
                drafts.remove(key(doomed));
                active=null;activeIndex=-1;saved=null;noteField=null;
                noteText="";stars=4;mode=0;
                status=result.warning().isBlank()
                    ?"Permanently deleted saved file and attached feedback: "+
                        result.deleted().getFileName()
                    :result.warning();
                // init/loadBuilds selects next surviving project.
            }));
    }

    private void browseExternal(){
        if(busy)return;
        try{
            List<Path> files=NativeFileAccess.chooseSchematics(
                BlueprintLibrary.directory(),false);
            if(files.isEmpty()){status="File selection cancelled.";return;}
            pathText=files.get(0).toString();
            if(pathField!=null)pathField.setText(pathText);
            status="Selected "+files.get(0).getFileName()+
                ". Click Use This File to review it.";
        }catch(Exception ex){status="Native schematic picker: "+root(ex);}
    }

    private void useExternal(){
        if(busy)return;
        capture();
        try{
            Path file=BlueprintLibrary.resolveUserPath(pathText);
            mode=0;
            selectProject(file,-1);
        }catch(Exception ex){status="Invalid file: "+ex.getMessage();}
    }

    private void saveFeedback(){
        if(busy)return;
        capture();
        if(active==null){status="Choose a project first; no feedback has been saved.";return;}
        if(noteText.isBlank()){status="Write your feedback first.";return;}
        Path project=active;String comment=noteText;int rating=stars;
        busy=true;status="Saving feedback for "+project.getFileName()+"...";
        CompletableFuture.runAsync(()->{
            try{BuildAIStyleMemory.review(project,rating,comment);}
            catch(Exception ex){throw new RuntimeException(ex);}
        }).whenComplete((unused,error)->MinecraftClient.getInstance().execute(()->{
            busy=false;
            if(error!=null){status="Save failed: "+root(error);return;}
            drafts.remove(key(project));
            if(active!=null&&key(active).equals(key(project))){
                try{saved=BuildAIStyleMemory.feedbackFor(project);}
                catch(Exception ex){status="Saved, but failed to reread: "+ex.getMessage();return;}
            }
            status="Saved to "+project.getFileName()+" (rating "+rating+"/5).";
        }));
    }
    private void learnDecorations(){
        if(busy)return;
        if(active==null){status="Select a schematic in My Build Feedback first.";return;}
        Path project=active;busy=true;
        status="Reading sign-labeled detail examples in "+project.getFileName()+"...";
        CompletableFuture.supplyAsync(()->{
            try{return BuildAIStyleMemory.addReference(project);}
            catch(Exception ex){throw new RuntimeException(ex);}
        }).whenComplete((count,error)->MinecraftClient.getInstance().execute(()->{
            busy=false;
            if(error!=null)status="Detail scan failed: "+root(error);
            else status="Saved "+count+" labeled decoration examples.";
        }));
    }
    private static String root(Throwable error){
        while(error.getCause()!=null)error=error.getCause();
        return error.getMessage()==null?error.toString():error.getMessage();
    }
    private void small(DrawContext ctx,String txt,int x,int y,int max,int color,float scale){
        if(max<=0)return;
        ctx.getMatrices().push();ctx.getMatrices().translate(x,y,0);
        ctx.getMatrices().scale(scale,scale,1f);
        ctx.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(txt,Math.max(10,(int)(max/scale))),
            0,0,color);
        ctx.getMatrices().pop();
    }
    @Override public void renderBackground(DrawContext ctx,int mx,int my,float dt){}
    @Override public void render(DrawContext ctx,int mx,int my,float dt){
        if(layout==null)return;
        int x=layout.x(),w=layout.width(),top=layout.bodyTop(),bottom=layout.bodyBottom();
        ctx.fill(x-7,4,x+w+7,height-4,BG);
        ctx.fill(x-7,4,x+w+7,7,MINT);
        small(ctx,"BUILDAI  /  BUILD QUALITY STUDIO",x+8,12,w-16,MINT,
            layout.compact()?0.85f:1.05f);
        small(ctx,"Keep personal reviews separate from architectural reference files.",
            x+8,layout.compact()?22:29,w-16,DIM,0.76f);
        ctx.fill(x,top-6,x+w,bottom+2,PANEL);
        ctx.fill(x,top-6,x+2,bottom+2,0xFF467985);
        if(layout.bodyHeight()<(layout.compact()?106:110)){
            small(ctx,"Reduce Minecraft GUI Scale or enlarge the window to edit reviews.",
                x+8,top+13,w-16,GOLD,0.83f);
        }else if(mode==0){
            small(ctx,"01  SELECT BUILD  /  02  RATE & SAVE  /  03  DELETE FILE",
                x+8,top+3,w-16,BLUE,0.8f);
            small(ctx,active==null?"NO BUILD SELECTED":active.getFileName().toString(),
                x+9,top+42,w-18,active==null?GOLD:LIGHT,0.85f);
            if(active!=null)
                small(ctx,"SOURCE: "+active.toAbsolutePath().normalize(),
                    x+9,top+52,w-18,DIM,0.67f);
            if(bottom>=top+137){
                String last=saved==null?"NOT YET REVIEWED":
                    "PREVIOUSLY SAVED  /  "+saved.rating()+"/5"+
                    (saved.legacy()?"  (OLDER FILENAME-ONLY NOTE)":"");
                small(ctx,last,x+9,top+116,w-18,saved==null?DIM:MINT,0.74f);
                if(saved!=null&&bottom>=top+148)
                    small(ctx,"Saved note: "+saved.comment().replace('\n',' '),
                        x+9,top+127,w-18,DIM,0.72f);
            }
        }else if(mode==3){
            small(ctx,"CONTRIBUTE A REPRODUCIBLE REDSTONE CASE",x+8,top+4,
                w-16,MINT,0.84f);
            small(ctx,"Share an original schematic, optional expert correction",
                x+10,top+51,w-20,BODY,0.76f);
            small(ctx,"and truth-table evidence as a reviewed local ZIP.",
                x+10,top+63,w-20,DIM,0.76f);
            if(bottom>=top+96)
                small(ctx,"Public GitHub issue; explicit rights consent required.",
                    x+10,top+85,w-20,GOLD,0.74f);
        }else if(mode==1){
            small(ctx,"CHOOSE A REFERENCE METHOD",x+8,top+4,w-16,BLUE,0.84f);
            small(ctx,"Whole builds: no signs required; learn palettes, shapes & detail.",
                x+10,top+44,w-20,DIM,0.78f);
            small(ctx,"Labeled details: sample chairs, arches and decor near signs.",
                x+10,top+73,w-20,DIM,0.77f);
            if(bottom>=top+113){
                small(ctx,"Selected for labeled scan: "+(active==null?"none":active.getFileName()),
                    x+10,top+96,w-20,GOLD,0.75f);
            }
        }else{
            small(ctx,"PICK AN EXTERNAL SCHEMATIC",x+8,top+4,w-16,BLUE,0.84f);
            small(ctx,"A file is activated only after you click USE THIS FILE.",
                x+10,top+82,w-20,DIM,0.77f);
        }
        small(ctx,status,x+5,layout.statusY()+3,w-10,
            status.toLowerCase(Locale.ROOT).contains("fail")||
            status.toLowerCase(Locale.ROOT).contains("invalid")?RED:BLUE,0.78f);
        super.render(ctx,mx,my,dt);
    }
    @Override public void close(){if(client!=null)client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
