package com.hanco.buildai.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import java.util.List;

/** Local, offline help guide. No third-party building mod required. */
public final class BuildAIHelpScreen extends Screen {
    private static final String[] TITLES = {
        "Welcome to BuildAI", "ChatGPT and API access", "Generate and revise",
        "Import and export", "Choose your placement", "Ground level and excavation",
        "Survival material chests", "Construction controls", "Safety and known limitations"
    };
    private static final String[][] CONTENT = {
        {
            "BuildAI converts a description or an imported schematic into real Minecraft blocks.",
            "Press F8 to open the building console. You can build from a text prompt or import an existing .schem, .schematic, .litematic or .nbt.",
            "BuildAI runs without WorldEdit, Litematica, PinLock, or ChainFrames. The Fabric API is its only mod API dependency.",
            "This is an early test build. Use a disposable world before experimenting with excavation or undo."
        },
        {
            "ChatGPT account sign-in is not connected in this release.",
            "AI generation currently uses a separately billed OpenAI API key, entered temporarily in F8. BuildAI does not save that key to disk or send it to Minecraft servers.",
            "Do not paste your ChatGPT password or session cookies into BuildAI. Your ChatGPT subscription is not an API key.",
            "The future Continue with ChatGPT flow needs an approved client and a secure browser-based authorization process.",
            "You can import community schematics and construct them without any AI account."
        },
        {
            "Type your building request in F8 and click Generate New.",
            "Example: A 24x20 medieval cottage with a slate roof, two floors, porch, glass windows and an interior stairwell.",
            "After generation, the editable blueprint is held locally. Describe a change such as 'Move the door to the east wall' and click Revise Existing.",
            "Changes re-generate a complete candidate blueprint from the previous AI instructions. Review every change before uploading; revisions are not guaranteed to preserve all details.",
            "Enable Chat Mode in F8 to send ordinary chat messages privately to the generator. Use 'new <description>' for a fresh design and 'exit' to stop intercepting chat."
        },
        {
            "Import File opens your operating system file picker. Files are converted to a common, bounded blueprint with explicit block states.",
            "Supported inputs: Sponge .schem, legacy .schematic (limited numeric IDs), Litematica .litematic, and Minecraft .nbt structures.",
            "Unknown IDs, missing blocks, unsupported NBT and oversized files may be rejected. It is safer to reject than to silently replace blocks.",
            "Export Blueprint as .schem saves the currently generated or imported building as a Sponge v3 schematic for sharing or later use."
        },
        {
            "Choose the blueprint surface Y layer with the +/- controls. This indicates which level of the design meets the ground.",
            "Upload Blueprint, then left-click a block to choose the horizontal X/Z location. This is an initial selection, not final construction approval.",
            "A colored outline shows the candidate volume. This early version does not yet provide a detached flying camera or 90-degree rotation.",
            "Sneak-right-click the intended surface block to set the final vertical alignment.",
            "The project remains in review until you explicitly confirm it."
        },
        {
            "The selected X/Z location and the sneak-clicked world surface fix the entire blueprint's real-world bounding volume.",
            "Anything in the approved construction volume can be excavated when you run /buildai confirm, including where the finished design contains air.",
            "BuildAI reports collisions; it blocks existing block entities and bedrock in its initial prototype.",
            "Excavation creates no collectible blocks. Test only on disposable terrain: a basic blockstate undo does not guarantee recovery of containers or player changes."
        },
        {
            "In Survival, put a sign reading BuildAI beside a chest. After anchoring the build, sneak-right-click the chest to link its inventory.",
            "Deliver required blocks to that chest. Link more chests as needed. Construction consumes supplies as each block is placed and pauses when they run out.",
            "Stand near a linked chest to view supplied/required counts and separate material/construction progress bars.",
            "Use /buildai materials to view per-material progress. Supply chests are protected from breaking while their active project is tracked.",
            "A project cannot yet survive a server restart; do not depend on the current implementation for long-running valuable-world builds."
        },
        {
            "/buildai status - report active project phase and construction count.",
            "/buildai materials - show required, used and currently supplied materials.",
            "/buildai substitute <original> <replacement> - allow compatible material substitutions before construction.",
            "/buildai cinematic - show slower construction and particles.",
            "/buildai fast - place many blocks per tick; may cause lag.",
            "/buildai confirm - after inspection, excavate and start construction.",
            "/buildai cancel - stop and keep already placed blocks.",
            "/buildai undo - attempt ordinary blockstate restoration. Used supplies are NOT automatically refunded."
        },
        {
            "DO NOT test BuildAI on a valuable world without a backup.",
            "Not in v0.1: actual detached camera, durable crash recovery, complete undo/refunds, full legacy conversions, arbitrary tested redstone mechanisms, or direct ChatGPT sign-in.",
            "The AI can generate block geometry and write schematics, but does not yet simulate every redstone mechanism. Check machinery in Minecraft.",
            "Save and reload frequently when designing, but remember ongoing construction sessions are not persisted across server restarts.",
            "For bug reports include the schematic type, block count, Minecraft/Fabric versions, build log and reproducible steps."
        }
    };

    private final Screen parent;
    private int page = 0;

    public BuildAIHelpScreen(Screen parent) {
        super(Text.literal("BuildAI How To"));
        this.parent = parent;
    }
    @Override protected void init() {
        int content = Math.min(540,width-24);
        int x = (width-content)/2;
        int y = Math.max(15,height-38);
        int gap=6;
        int w=(content-2*gap)/3;
        addDrawableChild(ButtonWidget.builder(Text.literal("Previous"), b -> move(-1))
            .dimensions(x,y,w,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Back to BuildAI"),b -> {
            if(client!=null)client.setScreen(parent);
        }).dimensions(x+w+gap,y,w,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Next"),b -> move(1))
            .dimensions(x+2*(w+gap),y,w,20).build());
    }
    private void move(int direction) {page = Math.floorMod(page+direction,TITLES.length);}
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta) {
        super.render(context,mouseX,mouseY,delta);
        int content=Math.min(540,width-24);
        int x=(width-content)/2;
        int y=20;
        context.drawCenteredTextWithShadow(textRenderer,"BuildAI / How To",width/2,y,0x69E4B7);
        context.drawCenteredTextWithShadow(textRenderer,TITLES[page],width/2,y+18,0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
            "Guide "+(page+1)+" of "+TITLES.length,width/2,y+32,0xA1AAB5);
        int lineY=y+54;
        int maxY=height-56;
        for(String paragraph:CONTENT[page]){
            List<OrderedText> lines=textRenderer.wrapLines(Text.literal(paragraph),content-32);
            if(lineY+(lines.size()+1)*12>maxY) {
                context.drawTextWithShadow(textRenderer,"Resize the window to read the remainder.",x+12,maxY-12,0xE9AA72);
                break;
            }
            for(OrderedText line:lines) {
                context.drawTextWithShadow(textRenderer,line,x+12,lineY,0xE0E7EC);
                lineY+=12;
            }
            lineY+=10;
        }
    }
    @Override public boolean shouldPause(){return false;}
    @Override public void close(){if(client!=null)client.setScreen(parent);}
}
