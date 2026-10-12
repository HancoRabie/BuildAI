package com.hanco.buildai.client;

import com.hanco.buildai.blueprint.StudioLayout;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A short, non-scrolling 3-step guide to opt-in whole-schematic style analysis.
 * All GUI widgets are inside StudioLayout's scaled-window bounds.
 * A concise preview replaces the unbounded text report; the full report can
 * be copied to clipboard on demand, without needing scrolling controls.
 */
public final class BuildAIArchitectureScreen extends Screen {
    private static final int BG=0xF0142230, SURFACE=0xEF223444, BORDER=0xFF426678;
    private static final int MINT=0xFF6DE4C8, SKY=0xFF90BFE8, AMBER=0xFFF0CA84;
    private static final int BODY=0xFFDEEAF1, DIM=0xFF9FB5C4;
    private final Screen parent;
    private StudioLayout.View layout;
    private List<Path> saved=List.of();
    private List<BuildAIStyleMemory.ArchitectureReference> references=List.of();
    private int step=0,savedIndex=-1,referenceIndex=0;
    private String entered="",status="Select a saved build or paste the path to a schematic.";
    private TextFieldWidget pathField;
    private boolean busy=false;

    public BuildAIArchitectureScreen(Screen parent) {
        super(Text.literal("BuildAI | Architectural References"));
        this.parent=parent;
    }

    private void reload(){
        try{
            saved=BlueprintLibrary.files();
            references=BuildAIStyleMemory.architectureReferences();
            referenceIndex=Math.max(0,Math.min(referenceIndex,references.size()-1));
        }catch(Exception ex){
            status="Could not load your style library: "+ex.getMessage();
        }
    }

    private ButtonWidget button(String label,int x,int y,int w,int h,
                                ButtonWidget.PressAction action){
        ButtonWidget b=ButtonWidget.builder(
            Text.literal(textRenderer.trimToWidth(label,Math.max(30,w-12))),action)
            .dimensions(x,y,Math.max(25,w),Math.max(12,h)).build();
        addDrawableChild(b);
        return b;
    }
    private void capture(){if(pathField!=null)entered=pathField.getText();}
    private void navigate(int to){capture();step=to;clearChildren();init();}

    @Override protected void init(){
        capture();
        layout=StudioLayout.forWindow(width,height);
        reload();
        int x=layout.x(),w=layout.width(),gap=5,h=layout.buttonHeight();
        int half=(w-gap)/2,third=(w-2*gap)/3;
        int top=layout.bodyTop();
        if(layout.bodyHeight()<75){
            // Minecraft GUI Scale can produce very short logical windows.
            // Hide actions instead of stacking them on the footer.
        }else if(step==0){
            boolean showSaved=layout.bodyHeight()>=99;
            if(showSaved){
                String name=savedIndex<0?"Choose from My Builds":saved.get(savedIndex).getFileName().toString();
                button(name+"  (click to change)",x+8,top+16,w-16,h,b->chooseSaved());
            }
            int inputY=top+(showSaved?52:19);
            pathField=new TextFieldWidget(textRenderer,x+8,inputY,w-16,h,Text.literal("Schematic file path"));
            pathField.setMaxLength(1024);
            pathField.setText(entered);
            pathField.setPlaceholder(Text.literal("Paste a schematic file path here"));
            addDrawableChild(pathField);
            int actionY=top+(showSaved?79:43);
            ButtonWidget analyze=button("ANALYZE FILE",x+8,actionY,half-10,h,b->analyzePath());
            ButtonWidget folder=button("SCAN FOLDER",x+half+gap,actionY,half-8,h,b->analyzeFolder());
            analyze.active=!busy;
            folder.active=!busy;
        }else if(step==1){
            if(references.isEmpty()){
                button("Choose a schematic first",x+8,top+25,w-16,h,b->navigate(0));
            }else{
                String name=references.get(referenceIndex).name();
                button(references.size()==1?"Style: "+name:"Style "+(referenceIndex+1)+"/"+
                    references.size()+": "+name+"   (click for next)",x+8,
                    top+(layout.compact()?3:16),w-16,h,b->cycleReference());
            }
        }

        int foot=layout.footerY();
        if(step==0){
            button("BACK TO STYLES",x,foot,half,h,b->close());
            button("HOW IT WORKS",x+half+gap,foot,half,h,b->navigate(2));
        }else if(step==1){
            button("NEW SOURCE",x,foot,third,h,b->navigate(0));
            ButtonWidget copy=button("COPY REPORT",x+third+gap,foot,third,h,b->copyReport());
            ButtonWidget use=button("USE & CREATE",x+2*(third+gap),foot,w-2*(third+gap),h,
                b->createWithStyle());
            copy.active=!references.isEmpty();
            use.active=!references.isEmpty();
        }else{
            button("CHOOSE FILE",x,foot,half,h,b->navigate(0));
            button("OPEN BUILDAI",x+half+gap,foot,half,h,
                b->client.setScreen(new BuildAIScreen()));
        }
    }

    private void chooseSaved(){
        if(saved.isEmpty()){status="No saved designs found. Paste a full file path instead.";return;}
        savedIndex=(savedIndex+1)%saved.size();
        entered=saved.get(savedIndex).toAbsolutePath().toString();
        pathField.setText(entered);
        status="Ready: "+saved.get(savedIndex).getFileName()+". Select Analyze File.";
    }

    private void cycleReference(){
        if(references.isEmpty())return;
        referenceIndex=(referenceIndex+1)%references.size();
        clearChildren();init();
    }

    private void analyzePath(){
        if(busy)return;
        capture();
        try{
            Path path=BlueprintLibrary.resolveUserPath(entered);
            busy=true;status="Analyzing "+path.getFileName()+"; this does not change your world.";
            CompletableFuture.supplyAsync(()->{
                try{return BuildAIStyleMemory.addArchitectureReference(path);}
                catch(Exception ex){throw new RuntimeException(ex);}
            }).whenComplete((result,error)->MinecraftClient.getInstance().execute(()->{
                busy=false;
                if(error!=null){status="Analysis failed: "+root(error);return;}
                referenceIndex=0;
                status="Saved: "+result.blocks()+" blocks and "+result.materials()+
                    " material types. Reference ready.";
                navigate(1);
            }));
        }catch(Exception ex){status="Select a valid file: "+ex.getMessage();}
    }

    private void analyzeFolder(){
        if(busy)return;
        busy=true;status="Scanning up to 12 files in buildai/style-references...";
        CompletableFuture.supplyAsync(()->{
            try{return BuildAIStyleMemory.addArchitectureFolder();}
            catch(Exception ex){throw new RuntimeException(ex);}
        }).whenComplete((result,error)->MinecraftClient.getInstance().execute(()->{
            busy=false;
            if(error!=null){status="Folder scan failed: "+root(error);return;}
            referenceIndex=0;
            if(result.saved()>0){
                status="Saved "+result.saved()+" references"+
                    (result.failed()>0?" ("+result.failed()+" files failed)":"")+".";
                navigate(1);
            }else{
                status=result.inspected()==0 ?
                    "Folder is empty. Add .schem files to buildai/style-references/ and rescan." :
                    "No valid files: "+result.firstError();
            }
        }));
    }

    private void createWithStyle(){
        if(references.isEmpty())return;
        try{
            // Users explicitly choose which style is promoted to the prompt
            // context; merely browsing an old reference must not silently
            // claim it's being used.
            BuildAIStyleMemory.activateArchitectureReference(references.get(referenceIndex).name());
            client.setScreen(new BuildAIScreen());
        }catch(Exception ex){status="Could not select reference: "+ex.getMessage();}
    }

    private void copyReport(){
        if(references.isEmpty())return;
        MinecraftClient.getInstance().keyboard.setClipboard(references.get(referenceIndex).report());
        status="Complete analysis copied to clipboard.";
    }

    private static String root(Throwable error){
        while(error.getCause()!=null)error=error.getCause();
        return error.getMessage()==null?error.toString():error.getMessage();
    }

    private void small(DrawContext ctx,String value,int x,int y,int maxWidth,int color,float scale){
        if(maxWidth<=4)return;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x,y,0);
        ctx.getMatrices().scale(scale,scale,1f);
        String trimmed=textRenderer.trimToWidth(value,Math.max(8,(int)(maxWidth/scale)));
        ctx.drawTextWithShadow(textRenderer,trimmed,0,0,color);
        ctx.getMatrices().pop();
    }

    private void stepMap(DrawContext ctx){
        int x=layout.x(),w=layout.width(),gap=4;
        int each=(w-2*gap)/3, y=layout.stepY();
        String[] labels={"01  SELECT","02  ANALYZE","03  CREATE"};
        for(int i=0;i<3;i++){
            int xx=x+i*(each+gap),ww=i==2?w-2*(each+gap):each;
            ctx.fill(xx,y,xx+ww,y+18,i==step?0xFF287362:0xFF273E4C);
            ctx.fill(xx,y+17,xx+ww,y+18,i==step?MINT:BORDER);
            small(ctx,labels[i],xx+6,y+5,ww-10,i==step?0xFFFFFFFF:DIM,0.83f);
        }
    }

    private static String reportLine(String report,String prefix){
        for(String line:report.split("\\R")){
            if(line.startsWith(prefix))return line.substring(prefix.length());
        }
        return "";
    }
    private static String palette(String report){
        String line=reportLine(report,"Main material palette (actual counts): ");
        if(line.isEmpty())return "No material summary available";
        StringBuilder result=new StringBuilder();
        String[] pieces=line.split(";");
        for(int i=0;i<Math.min(4,pieces.length);i++){
            String item=pieces[i].trim().replace("minecraft:","");
            if(item.endsWith("."))item=item.substring(0,item.length()-1);
            if(i>0)result.append("  /  ");
            result.append(item);
        }
        return result.toString();
    }

    private void source(DrawContext ctx,int x,int top,int w){
        boolean showSaved=layout.bodyHeight()>=99;
        small(ctx,"SOURCE  /  choose one of your builds or import another person's schematic",
            x+9,top+3,w-18,SKY,0.8f);
        if(showSaved)small(ctx,"MY BUILDS  /  click the selector to cycle saved files",
            x+9,top+39,w-18,DIM,0.75f);
        int actions=top+(showSaved?79:43);
        if(layout.bodyBottom()>=actions+39){
            small(ctx,"BATCH OPTION  /  place .schem files in game/buildai/style-references/",
                x+9,actions+24,w-18,DIM,0.76f);
            small(ctx,"Scan Folder analyzes up to 12 files; source files remain unchanged.",
                x+9,actions+34,w-18,DIM,0.76f);
        }else if(layout.bodyBottom()>=actions+27){
            small(ctx,"For batches, use game/buildai/style-references/",x+9,actions+23,w-18,DIM,0.77f);
        }
    }

    private void summary(DrawContext ctx,int x,int top,int w){
        if(references.isEmpty()){
            small(ctx,"No references saved yet. Choose a file to get started.",
                x+10,top+6,w-20,BODY,0.85f);
            return;
        }
        BuildAIStyleMemory.ArchitectureReference ref=references.get(referenceIndex);
        int y=top+(layout.compact()?25:43),h=layout.compact()?23:31;
        int gap=5,third=(w-20-2*gap)/3;
        String[] heads={"BLOCKS ANALYZED","BLOCK TYPES","IN LIBRARY"};
        String[] values={String.format("%,d",ref.blocks()),Integer.toString(ref.materials()),
            Integer.toString(references.size())};
        for(int i=0;i<3;i++){
            int bx=x+10+i*(third+gap);
            ctx.fill(bx,y,bx+third,y+h,SURFACE);
            ctx.fill(bx,y,bx+2,y+h,i==0?MINT:i==1?SKY:AMBER);
            small(ctx,heads[i],bx+6,y+4,third-9,DIM,0.68f);
            small(ctx,values[i],bx+6,y+(layout.compact()?13:16),third-9,BODY,0.89f);
        }
        int infoY=y+h+(layout.compact()?4:8);
        small(ctx,"MATERIAL PALETTE",x+10,infoY,w-20,MINT,0.78f);
        small(ctx,palette(ref.report()),x+10,infoY+11,w-20,BODY,0.77f);
        if(infoY+32<layout.bodyBottom()){
            String details=reportLine(ref.report(),"Detail vocabulary (actual counts): ");
            small(ctx,"DETAILING  /  "+details,x+10,infoY+23,w-20,SKY,0.75f);
        }
    }

    private void explain(DrawContext ctx,int x,int top,int w){
        int gap=5,cardW=(w-20-2*gap)/3,cardY=top+17;
        int cardH=Math.max(27,Math.min(layout.compact()?33:45,layout.bodyHeight()-37));
        small(ctx,"HOW YOUR REFERENCES HELP THE NEXT BUILD",x+10,top+3,w-20,MINT,0.83f);
        String[] labels={"1 / IMPORT","2 / LEARN","3 / CREATE"};
        String[] descriptions={"Choose .schem","Analyze shapes","Prompt in F8"};
        for(int i=0;i<3;i++){
            int xx=x+10+i*(cardW+gap);
            ctx.fill(xx,cardY,xx+cardW,cardY+cardH,SURFACE);
            ctx.fill(xx,cardY,xx+cardW,cardY+2,i==0?SKY:i==1?AMBER:MINT);
            small(ctx,labels[i],xx+5,cardY+7,cardW-10,i==2?MINT:BODY,0.75f);
            small(ctx,descriptions[i],xx+5,cardY+19,cardW-10,DIM,0.69f);
        }
        if(cardY+cardH+12<layout.bodyBottom()){
            small(ctx,"Saved reports guide the AI automatically; the latest two approved references are used.",
                x+10,cardY+cardH+6,w-20,BODY,0.76f);
            small(ctx,"No model training or full visual inspection. Preview the result before building.",
                x+10,cardY+cardH+17,w-20,DIM,0.74f);
        }
    }

    @Override public void renderBackground(DrawContext ctx,int mouseX,int mouseY,float delta){}
    @Override public void render(DrawContext ctx,int mx,int my,float delta){
        if(layout==null)return;
        int x=layout.x(),w=layout.width();
        ctx.fill(x-7,4,x+w+7,height-4,BG);
        ctx.fill(x-7,4,x+w+7,7,MINT);
        small(ctx,"BUILDAI   /   ARCHITECTURAL REFERENCES",x+9,13,w-18,MINT,
            layout.compact()?0.84f:1.05f);
        small(ctx,"Learn from the blocks and details in great builds. No signs required.",
            x+9,layout.compact()?22:29,w-18,DIM,0.78f);
        stepMap(ctx);
        ctx.fill(x,layout.bodyTop()-6,x+w,layout.bodyBottom()+2,0xB01E3040);
        ctx.fill(x,layout.bodyTop()-6,x+2,layout.bodyBottom()+2,0xFF356E76);
        if(layout.bodyHeight()<75) {
            small(ctx,"Enlarge the window or lower Minecraft GUI Scale for the studio controls.",
                x+9,layout.bodyTop()+12,w-18,AMBER,0.79f);
        }else if(step==0)source(ctx,x,layout.bodyTop(),w);
        else if(step==1)summary(ctx,x,layout.bodyTop(),w);
        else explain(ctx,x,layout.bodyTop(),w);
        small(ctx,status,x+5,layout.statusY()+3,w-10,
            status.contains("failed")||status.contains("Invalid")?AMBER:SKY,0.77f);
        super.render(ctx,mx,my,delta);
    }
    @Override public boolean shouldPause(){return false;}
    @Override public void close(){if(client!=null)client.setScreen(parent);}
}
