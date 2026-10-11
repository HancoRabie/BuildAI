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
            "Press F8 to open the building console. You can generate with AI, import a schematic, or click Load Demo Cottage to test offline without an account.",
            "BuildAI runs without WorldEdit, Litematica, PinLock, or ChainFrames. The Fabric API is its only mod API dependency.",
            "This is an early test build. Use a disposable world before experimenting with excavation or undo."
        },
        {
            "Press F8, then Continue with ChatGPT. BuildAI tries to open the secure OpenAI login in your default browser. Approve permission and return to Minecraft.",
            "If Java cannot launch your browser, use the sign-in modal's Open Browser or Copy Sign-in Link button. Paste the copied link into your preferred browser, such as Brave, while the sign-in session is active.",
            "Connected eligible ChatGPT users can generate with their plan. A separate OpenAI developer API key remains an optional fallback; it is not saved by the game.",
            "BuildAI never asks for ChatGPT passwords or cookies. Browser permission is distinct from ordinary ChatGPT account sign-in; plan access depends on eligible account usage limits.",
            "A first-time open-source OAuth registration issues a reusable client ID. Sign-in uses PKCE, ID token signature verification and a locally stored session. Disconnect from F8 when needed.",
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
            "Upload and Preview sends the design to the server. Once accepted, close F8 to enter a persistent detached observer camera. The actual player stays stationary.",
            "The preview starts in front of you. Move with WASD, Space, and Shift; mouse wheel offsets world Z. The camera stays active while F8 and Minecraft chat are open.",
            "Point the preview camera toward the intended surface block and press G to snap the blueprint elevation. Press Enter to request server XYZ validation. Use R or /buildai retry after a misclick.",
            "The project remains in review until you explicitly confirm it."
        },
        {
            "Enter locks the entire XYZ blueprint origin for review, not construction. R or /buildai retry returns to editable placement; /buildai confirm starts excavation only after successful server validation.",
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
            "/buildai normalize - repair glass pane and iron bar connections, including older completed builds near you.",
            "/buildai hide - dismiss the visual placement grid without modifying the real building.",
            "/buildai confirm - after inspection, excavate and start construction.",
            "/buildai cancel - stop and keep already placed blocks.",
            "/buildai undo - attempt ordinary blockstate restoration. Used supplies are NOT automatically refunded."
        },
        {
            "DO NOT test BuildAI on a valuable world without a backup.",
            "This test build includes detached placement flight and browser ChatGPT sign-in, but neither has been live user-verified. Still incomplete: durable crash recovery, full rollback/refunds, complete legacy conversions and tested arbitrary redstone.",
            "The AI can generate block geometry and write schematics, but does not yet simulate every redstone mechanism. Check machinery in Minecraft.",
            "Save and reload frequently when designing, but remember ongoing construction sessions are not persisted across server restarts.",
            "For bug reports include the schematic type, block count, Minecraft/Fabric versions, build log and reproducible steps."
        }
    };

    private final Screen parent;
    private int page=0,firstVisibleLine=0;
    public BuildAIHelpScreen(Screen parent){this(parent,0);}
    public BuildAIHelpScreen(Screen parent,int initialPage){
        super(Text.literal("BuildAI How To"));
        this.parent=parent;
        this.page=Math.floorMod(initialPage,TITLES.length);
    }
    private int contentWidth(){return Math.min(540,Math.max(140,width-16));}
    private int visibleLines(){return Math.max(2,(height-139)/12);}
    private List<OrderedText> wrapped(){
        List<OrderedText> out=new java.util.ArrayList<>();
        for(String paragraph:CONTENT[page]){
            out.addAll(textRenderer.wrapLines(Text.literal(paragraph),contentWidth()-24));
            out.add(Text.literal(" ").asOrderedText());
        }
        return out;
    }
    private void scroll(int direction){
        int max=Math.max(0,wrapped().size()-visibleLines());
        firstVisibleLine=Math.max(0,Math.min(max,firstVisibleLine+direction*4));
    }
    @Override protected void init(){
        int content=contentWidth(),x=(width-content)/2;
        int gap=4,third=(content-gap*2)/3,h=20;
        int navigation=Math.max(24,height-25),scrollRow=Math.max(3,navigation-24);
        addDrawableChild(ButtonWidget.builder(Text.literal("Previous"),b->move(-1))
            .dimensions(x,navigation,third,h).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Back to BuildAI"),b->{
            if(client!=null)client.setScreen(parent);
        }).dimensions(x+third+gap,navigation,third,h).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Next"),b->move(1))
            .dimensions(x+2*(third+gap),navigation,content-2*(third+gap),h).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Scroll up"),b->scroll(-1))
            .dimensions(x,scrollRow,(content-gap)/2,h).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Scroll down"),b->scroll(1))
            .dimensions(x+(content-gap)/2+gap,scrollRow,(content-gap)/2,h).build());
    }
    private void move(int direction){
        page=Math.floorMod(page+direction,TITLES.length);
        firstVisibleLine=0;
    }
    @Override public void render(DrawContext ctx,int mouseX,int mouseY,float delta){
        super.render(ctx,mouseX,mouseY,delta);
        int content=contentWidth(),x=(width-content)/2;
        ctx.drawCenteredTextWithShadow(textRenderer,"BuildAI / How To",width/2,12,0x69E4B7);
        ctx.drawCenteredTextWithShadow(textRenderer,TITLES[page],width/2,27,0xFFFFFF);
        List<OrderedText> lines=wrapped();
        int visible=visibleLines();
        int maxStart=Math.max(0,lines.size()-visible);
        firstVisibleLine=Math.min(firstVisibleLine,maxStart);
        ctx.drawCenteredTextWithShadow(textRenderer,
            "Topic "+(page+1)+"/"+TITLES.length+" · Lines "+
            (firstVisibleLine+1)+"-"+Math.min(lines.size(),firstVisibleLine+visible)+
            "/"+lines.size(),width/2,43,0xA1AAB5);
        for(int i=firstVisibleLine;i<Math.min(lines.size(),firstVisibleLine+visible);i++)
            ctx.drawTextWithShadow(textRenderer,lines.get(i),x+10,60+(i-firstVisibleLine)*12,0xE0E7EC);
    }
    @Override public boolean shouldPause(){return false;}
    @Override public void close(){if(client!=null)client.setScreen(parent);}
}
