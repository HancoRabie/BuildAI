package com.hanco.buildai.client;

import com.hanco.buildai.blueprint.Blueprint;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Native in-game inspection of an AI-generated or imported blueprint.
 * Never requires saving a schematic or leaving Minecraft. Layer slices
 * allow a quick check of interior openings before the 3D placement stage.
 */
public final class BlueprintInspectorScreen extends Screen {
    private final Screen parent;
    private final Blueprint blueprint;
    private int slice;
    public BlueprintInspectorScreen(Screen parent,Blueprint blueprint,int initialLayer){
        super(Text.literal("BuildAI | Blueprint Preview"));
        this.parent=parent;
        this.blueprint=blueprint;
        this.slice=Math.max(0,Math.min(blueprint.height-1,initialLayer));
    }
    private int panel(){return Math.min(540,Math.max(130,width-16));}
    private void layer(int change){slice=Math.max(0,Math.min(blueprint.height-1,slice+change));}
    @Override protected void init(){
        int p=panel(),x=(width-p)/2,gap=4,third=(p-2*gap)/3;
        int buttonsY=Math.max(30,height-50);
        addDrawableChild(ButtonWidget.builder(Text.literal("Layer -"),b->layer(-1))
            .dimensions(x,buttonsY,third,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Layer +"),b->layer(1))
            .dimensions(x+third+gap,buttonsY,third,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Return to BuildAI"),b->close())
            .dimensions(x+2*(third+gap),buttonsY,p-2*(third+gap),20).build());
    }
    private static int color(String raw){
        String id=Blueprint.id(raw);
        if(id.contains("glass")||id.contains("pane"))return 0xD05BCFDE;
        if(id.contains("water"))return 0xCC397BC8;
        if(id.contains("lava"))return 0xDDFF6A18;
        if(id.contains("redstone"))return 0xFFD44B49;
        if(id.contains("leaves"))return 0xFF438F59;
        if(id.contains("grass")||id.contains("moss"))return 0xFF71B869;
        if(id.contains("planks")||id.contains("_log")||id.contains("_wood"))return 0xFFD4A76C;
        if(id.contains("deepslate")||id.contains("blackstone"))return 0xFF54535A;
        if(id.contains("quartz")||id.contains("concrete"))return 0xFFE5DFD5;
        if(id.contains("stone")||id.contains("brick"))return 0xFFA6B0B7;
        return 0xFFC3AD84;
    }

    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta){
        super.render(context,mouseX,mouseY,delta);
        int p=panel(),left=(width-p)/2;
        context.drawCenteredTextWithShadow(textRenderer,"BuildAI | Blueprint Inspector",width/2,9,0x70ECC6);
        context.drawCenteredTextWithShadow(textRenderer,
            textRenderer.trimToWidth(blueprint.name,p),width/2,22,0xE9F1F7);
        String details=blueprint.width+" x "+blueprint.height+" x "+blueprint.length+
            " | "+blueprint.solidCount()+" blocks | Y layer "+slice+" of "+(blueprint.height-1);
        context.drawCenteredTextWithShadow(textRenderer,details,width/2,35,0xBAD6E0);
        int areaHeight=Math.max(22,height-120),areaWidth=Math.max(42,p-16);
        int cell=Math.max(1,Math.min(16,Math.min(areaWidth/blueprint.width,areaHeight/blueprint.length)));
        int blockW=blueprint.width*cell,blockD=blueprint.length*cell;
        int gridX=(width-blockW)/2,gridY=52+(Math.max(0,areaHeight-blockD)/2);
        context.fill(gridX-2,gridY-2,gridX+blockW+2,gridY+blockD+2,0xFF293745);
        for(int z=0;z<blueprint.length;z++)for(int x=0;x<blueprint.width;x++){
            if(blueprint.isAir(x,slice,z))continue;
            int px=gridX+x*cell,py=gridY+z*cell;
            context.fill(px,py,px+cell,py+cell,color(blueprint.get(x,slice,z)));
        }
        if(mouseX>=gridX&&mouseX<gridX+blockW&&mouseY>=gridY&&mouseY<gridY+blockD){
            int x=(mouseX-gridX)/cell,z=(mouseY-gridY)/cell;
            String block=blueprint.get(x,slice,z);
            context.drawCenteredTextWithShadow(textRenderer,
                textRenderer.trimToWidth("X "+x+" Y "+slice+" Z "+z+" : "+block,p),
                width/2,Math.max(8,height-76),0xFFECB0);
        }
        context.drawCenteredTextWithShadow(textRenderer,
            "Top-down cross-section: inspect openings before positioning the 3D ghost.",
            width/2,height-21,0x9DB6C1);
    }
    @Override public void close(){if(client!=null)client.setScreen(parent);}
    @Override public boolean shouldPause(){return false;}
}
