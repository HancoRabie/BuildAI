package com.hanco.buildai.client;

import com.hanco.buildai.blueprint.PixelArtConverter;
import com.hanco.buildai.blueprint.StudioLayout;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Offline image -> 1-block-deep vertical BuildAI blueprint studio.
 * Explicit local-only selection, no image upload to a third-party model.
 * Reuses the standard BuildAI blueprint preview/placement and save pipeline.
 */
public final class BuildAIPixelArtScreen extends Screen {
    private static final int[] WIDTHS={16,24,32,48,64,96,128};
    private final Screen parent;
    private int widthIndex=3;
    private PixelArtConverter.Material material=PixelArtConverter.Material.CONCRETE;
    private boolean smooth=true,transparency=true,busy=false;
    private String value="",status="Choose a local PNG/JPG; preview before building.";
    private TextFieldWidget pathField;
    private StudioLayout.View layout;
    private PixelArtConverter.Result result;

    public BuildAIPixelArtScreen(Screen parent) {
        super(Text.literal("BuildAI | Image to Pixel Art"));
        this.parent=parent;
    }

    private void capture(){
        if(pathField!=null)value=pathField.getText();
    }
    private ButtonWidget add(String title,int x,int y,int width,int height,
                             ButtonWidget.PressAction action){
        ButtonWidget button=ButtonWidget.builder(Text.literal(
            textRenderer.trimToWidth(title,Math.max(25,width-8))),action)
            .dimensions(x,y,Math.max(25,width),Math.max(12,height)).build();
        addDrawableChild(button);
        return button;
    }
    private void rebuild(){capture();clearChildren();init();}
    @Override protected void init(){
        capture();
        layout=StudioLayout.forWindow(width,height);
        int left=layout.x(),w=layout.width(),top=layout.bodyTop(),h=layout.buttonHeight();
        int gap=4,half=(w-gap)/2;
        pathField=new TextFieldWidget(textRenderer,left,top+2,w-94,h,
            Text.literal("Image file path"));
        pathField.setMaxLength(2048);
        pathField.setText(value);
        pathField.setPlaceholder(Text.literal("PNG or JPG file path"));
        addDrawableChild(pathField);
        ButtonWidget choose=add("BROWSE IMAGE",left+w-90,top+2,90,h,b->browse());
        ButtonWidget size=add("WIDTH: "+WIDTHS[widthIndex],left,top+25,half,h,b->{
            widthIndex=(widthIndex+1)%WIDTHS.length;result=null;rebuild();
        });
        ButtonWidget palette=add("PALETTE: "+material.name(),left+half+gap,top+25,
            half,h,b->{
                material=material==PixelArtConverter.Material.CONCRETE
                    ?PixelArtConverter.Material.WOOL:PixelArtConverter.Material.CONCRETE;
                result=null;rebuild();
            });
        ButtonWidget detail=add("RESAMPLE: "+(smooth?"SMOOTH":"PIXEL"),left,top+48,
            half,h,b->{smooth=!smooth;result=null;rebuild();});
        ButtonWidget alpha=add("TRANSPARENT: "+(transparency?"KEEP":"WHITE"),
            left+half+gap,top+48,half,h,b->{
                transparency=!transparency;result=null;rebuild();
            });
        ButtonWidget preview=add("GENERATE PREVIEW",left,top+71,half,h,b->generate());
        ButtonWidget use=add("USE BLUEPRINT",left+half+gap,top+71,half,h,b->useBlueprint());
        add("BACK TO BUILDAI",left,layout.footerY(),w,h,b->close());
        choose.active=!busy;
        preview.active=!busy;
        use.active=!busy&&result!=null;
        size.active=palette.active=detail.active=alpha.active=!busy;
    }

    private void browse(){
        if(busy)return;
        capture();
        // Same LWJGL native picker used by ChainFrames. Minecraft's JVM
        // can be AWT-headless even on a normal Windows gaming desktop.
        try{
            Path selected=NativeFileAccess.chooseImage(
                net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir());
            if(selected==null){
                status="Image selection cancelled.";
                return;
            }
            value=selected.toString();
            if(pathField!=null)pathField.setText(value);
            status="Selected "+selected.getFileName()+"; generating local preview...";
            generate();
        }catch(java.io.IOException error){
            status="Image chooser: "+error.getMessage();
        }
    }

    private void generate(){
        if(busy)return;
        capture();
        if(value.isBlank()){status="Select or paste a local PNG/JPG file path.";return;}
        final Path image;
        try{
            String path=value.trim();
            if(path.length()>1&&path.startsWith("\"")&&path.endsWith("\""))
                path=path.substring(1,path.length()-1);
            image=Path.of(path);
        }catch(RuntimeException e){status="Invalid file path.";return;}
        final int targetWidth=WIDTHS[widthIndex];
        final var currentMaterial=material;
        final boolean currentSmooth=smooth,currentTransparency=transparency;
        busy=true;result=null;status="Reading image and matching Minecraft colors locally...";
        rebuild();
        CompletableFuture.supplyAsync(()->{
            try{return PixelArtConverter.fromFile(image,targetWidth,
                currentMaterial,currentSmooth,currentTransparency);}
            catch(Exception e){throw new RuntimeException(e);}
        }).whenComplete((converted,error)->MinecraftClient.getInstance().execute(()->{
            busy=false;
            if(error!=null){
                Throwable cause=error;
                while(cause.getCause()!=null)cause=cause.getCause();
                status="Could not convert: "+String.valueOf(cause.getMessage());
                result=null;
            }else{
                result=converted;
                status="Ready: "+converted.width()+" x "+converted.height()+
                    " blocks, "+converted.blueprint().solidCount()+
                    " placed, "+converted.colorCount()+" colors. Review then Use Blueprint.";
            }
            rebuild();
        }));
    }

    private void useBlueprint(){
        if(busy||result==null)return;
        var converted=result;
        BuildAIClient.acceptImport(converted.blueprint());
        // Autosave a NEW file, never overwrite the selected source image.
        CompletableFuture.supplyAsync(()->{
            try{return BlueprintLibrary.save(converted.blueprint());}
            catch(Exception e){throw new RuntimeException(e);}
        }).whenComplete((saved,error)->MinecraftClient.getInstance().execute(()->{
            if(error==null)
                BuildAIClient.message("Pixel art blueprint saved: "+saved.getFileName());
            else
                BuildAIClient.message("Pixel art is in memory, but autosave "+
                    "failed. Use F8 > Save Blueprint before leaving.");
        }));
        client.setScreen(BuildAIScreen.generatedResult());
    }

    @Override public void renderBackground(DrawContext ctx,int mx,int my,float delta){}
    @Override public void render(DrawContext ctx,int mx,int my,float delta){
        if(layout==null)return;
        int x=layout.x(),w=layout.width();
        ctx.fill(x-6,4,x+w+6,height-4,0xE9132834);
        ctx.fill(x-6,4,x+w+6,7,0xFF70E0C1);
        ctx.drawTextWithShadow(textRenderer,"BUILDAI  /  IMAGE TO PIXEL ART",
            x+5,15,0xFF79E9CC);
        ctx.drawTextWithShadow(textRenderer,
            textRenderer.trimToWidth("PNG / JPG > block colors > normal blueprint (no AI cost)",w-10),
            x+5,31,0xFFA8CDD9);
        int previewTop=layout.bodyTop()+96;
        int available=layout.bodyBottom()-previewTop-4;
        if(result!=null&&available>20){
            int[][] colors=result.previewArgb();
            int imageH=colors.length,imageW=colors[0].length;
            // Bounded to <=48x48 draw calls per frame, scaled to fit.
            int maxH=Math.min(48,Math.max(1,available));
            int maxW=Math.min(48,Math.max(1,w-20));
            double scale=Math.min((double)maxW/imageW,(double)maxH/imageH);
            int drawW=Math.max(1,(int)Math.round(imageW*scale));
            int drawH=Math.max(1,(int)Math.round(imageH*scale));
            int pixel=Math.max(1,Math.min((w-12)/drawW,available/drawH));
            int startX=x+(w-drawW*pixel)/2;
            int startY=previewTop+Math.max(0,(available-drawH*pixel)/2);
            ctx.fill(startX-2,startY-2,startX+drawW*pixel+2,
                startY+drawH*pixel+2,0xFF192B31);
            for(int y=0;y<drawH;y++)for(int xx=0;xx<drawW;xx++){
                int color=colors[Math.min(imageH-1,y*imageH/drawH)]
                    [Math.min(imageW-1,xx*imageW/drawW)];
                if((color>>>24)!=0)
                    ctx.fill(startX+xx*pixel,startY+y*pixel,
                        startX+(xx+1)*pixel,startY+(y+1)*pixel,color);
            }
        }else if(available>20){
            ctx.drawCenteredTextWithShadow(textRenderer,
                "Local preview will appear here after conversion.",
                width/2,previewTop+8,0xFF93B9C6);
        }
        ctx.drawTextWithShadow(textRenderer,
            textRenderer.trimToWidth(status,w-8),
            x+4,layout.statusY()+3,0xFFE9C58C);
        super.render(ctx,mx,my,delta);
    }
    @Override public boolean shouldPause(){return false;}
    @Override public void close(){if(client!=null)client.setScreen(parent);}
}
