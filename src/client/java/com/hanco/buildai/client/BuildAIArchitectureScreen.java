package com.hanco.buildai.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Headless-safe, in-game whole-schematic reference library.
 * Imported files never enter the active build/placement state.
 */
public final class BuildAIArchitectureScreen extends Screen {
    private final Screen parent;
    private List<Path> saved=List.of();
    private List<BuildAIStyleMemory.ArchitectureReference> references=List.of();
    private int fileOffset=0,referenceIndex=0,firstLine=0;
    private String entered="",status="Choose a blueprint or paste a file path to analyze its architectural style.";
    private TextFieldWidget pathField;
    private boolean busy=false;

    public BuildAIArchitectureScreen(Screen parent){
        super(Text.literal("BuildAI | Architectural Reference Library"));
        this.parent=parent;
    }

    private void reload(){
        try{
            saved=BlueprintLibrary.files();
            references=BuildAIStyleMemory.architectureReferences();
            referenceIndex=Math.min(Math.max(0,referenceIndex),Math.max(0,references.size()-1));
        }catch(Exception error){
            status="Could not load reference library: "+error.getMessage();
        }
    }

    @Override protected void init() {
        reload();
        int w=Math.min(620,Math.max(170,width-18)),x=(width-w)/2,gap=4;
        int half=(w-gap)/2,third=(w-2*gap)/3;
        for(int i=0;i<2;i++){
            if(i+fileOffset>=saved.size())break;
            Path file=saved.get(i+fileOffset);
            addDrawableChild(ButtonWidget.builder(
                Text.literal(textRenderer.trimToWidth(file.getFileName().toString(),w-16)),
                b->{
                    entered=file.toAbsolutePath().toString();
                    pathField.setText(entered);
                    status="Selected "+file.getFileName()+" for full architectural analysis.";
                }).dimensions(x,51+i*23,w,19).build());
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Previous Saved Builds"),b->{
            capture();fileOffset=Math.max(0,fileOffset-2);rebuild();
        }).dimensions(x,99,half,19).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Next Saved Builds"),b->{
            capture();fileOffset=Math.min(Math.max(0,saved.size()-2),fileOffset+2);rebuild();
        }).dimensions(x+half+gap,99,half,19).build());

        pathField=new TextFieldWidget(textRenderer,x,123,w,19,Text.literal("Reference schematic path"));
        pathField.setMaxLength(1024);
        pathField.setText(entered);
        pathField.setPlaceholder(Text.literal("Full path to a .schem / .schematic / .litematic / .nbt"));
        addDrawableChild(pathField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Analyze & Save Selected Schematic"),b->analyzePath())
            .dimensions(x,147,half,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Scan Reference Folder (up to 12)"),b->analyzeFolder())
            .dimensions(x+half+gap,147,half,20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Previous Reference"),b->switchReference(1))
            .dimensions(x,172,half,19).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Next Reference"),b->switchReference(-1))
            .dimensions(x+half+gap,172,half,19).build());

        int footerY=Math.max(194,height-25);
        addDrawableChild(ButtonWidget.builder(Text.literal("Scroll Up"),b->scroll(-1))
            .dimensions(x,footerY,third,19).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Scroll Down"),b->scroll(1))
            .dimensions(x+third+gap,footerY,third,19).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"),b->close())
            .dimensions(x+2*(third+gap),footerY,w-2*(third+gap),19).build());
    }

    private void capture(){if(pathField!=null)entered=pathField.getText();}
    private void rebuild(){clearChildren();init();}
    private void analyzePath(){
        if(busy)return;
        capture();
        try{
            Path path=BlueprintLibrary.resolveUserPath(entered);
            busy=true;status="Analyzing real blocks in "+path.getFileName()+" (read-only)...";
            CompletableFuture.supplyAsync(()->{
                try{return BuildAIStyleMemory.addArchitectureReference(path);}
                catch(Exception ex){throw new RuntimeException(ex);}
            }).whenComplete((report,error)->MinecraftClient.getInstance().execute(()->{
                busy=false;
                if(error!=null){status="Analysis failed: "+root(error);return;}
                referenceIndex=0;firstLine=0;reload();
                status="Saved "+report.blocks()+" block positions / "+report.materials()+
                    " material types. Future AI prompts will use your approved reference.";
            }));
        }catch(Exception ex){status="Invalid reference: "+ex.getMessage();}
    }
    private void analyzeFolder(){
        if(busy)return;
        busy=true;status="Scanning up to 12 schematic files in buildai/style-references ...";
        CompletableFuture.supplyAsync(()->{
            try{return BuildAIStyleMemory.addArchitectureFolder();}
            catch(Exception ex){throw new RuntimeException(ex);}
        }).whenComplete((scanned,error)->MinecraftClient.getInstance().execute(()->{
            busy=false;
            if(error!=null){status="Folder scan failed: "+root(error);return;}
            referenceIndex=0;firstLine=0;reload();
            status=scanned.inspected()==0 ?
                "No reference files found. Put schematics in buildai/style-references and scan again." :
                "Saved "+scanned.saved()+" / "+scanned.inspected()+" references; errors: "+
                    scanned.failed()+(scanned.failed()>0?"; "+scanned.firstError():"");
        }));
    }
    private static String root(Throwable error) {
        while(error.getCause()!=null)error=error.getCause();
        return error.getMessage()==null?error.toString():error.getMessage();
    }
    private void switchReference(int delta){
        if(references.isEmpty())return;
        referenceIndex=Math.floorMod(referenceIndex+delta,references.size());
        firstLine=0;
    }
    private List<OrderedText> lines(){
        if(references.isEmpty())return textRenderer.wrapLines(
            Text.literal("No whole-build references saved yet. Analyze a schematic above. "+
                "References stay on your machine and don't modify your current blueprint."),
            contentWidth());
        BuildAIStyleMemory.ArchitectureReference ref=references.get(referenceIndex);
        return textRenderer.wrapLines(Text.literal(ref.report()),contentWidth());
    }
    private int contentWidth(){return Math.max(130,Math.min(620,width-18)-18);}
    private int visibleLines(){return Math.max(1,(height-252)/11);}
    private void scroll(int direction){
        firstLine=Math.max(0,Math.min(Math.max(0,lines().size()-visibleLines()),firstLine+direction*6));
    }

    @Override public void renderBackground(DrawContext ctx,int mx,int my,float delta){}
    @Override public void render(DrawContext ctx,int mx,int my,float delta) {
        int w=Math.min(620,Math.max(170,width-18)),x=(width-w)/2;
        ctx.fill(x-7,4,x+w+7,height-4,0xF0162631);
        ctx.fill(x-7,4,x+w+7,8,0xFF6ADFC3);
        ctx.drawCenteredTextWithShadow(textRenderer,"BuildAI | Architectural Reference Library",
            width/2,12,0xFF7BF3D2);
        ctx.drawTextWithShadow(textRenderer,
            textRenderer.trimToWidth("Private references: game directory/buildai/style-references",w-8),
            x+4,28,0xFFD8E6F2);
        ctx.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(status,w-8),
            x+4,40,0xFFE9CE9B);
        String title=references.isEmpty()?"No analysis selected" :
            "Reference "+(referenceIndex+1)+"/"+references.size()+": "+
                references.get(referenceIndex).name();
        ctx.drawTextWithShadow(textRenderer,textRenderer.trimToWidth(title,w-8),x+4,197,0xFF7BF3D2);
        List<OrderedText> wrapped=lines();
        int visible=visibleLines();
        firstLine=Math.min(firstLine,Math.max(0,wrapped.size()-visible));
        for(int i=firstLine;i<Math.min(wrapped.size(),firstLine+visible);i++)
            ctx.drawTextWithShadow(textRenderer,wrapped.get(i),x+9,214+(i-firstLine)*11,0xFFD5E5EC);
        super.render(ctx,mx,my,delta);
    }
    @Override public boolean shouldPause(){return false;}
    @Override public void close(){if(client!=null)client.setScreen(parent);}
}
