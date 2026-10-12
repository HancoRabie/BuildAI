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
        "Import and export", "Feedback & reference schematics", "Choose your placement", "Ground level and excavation",
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
            "After generation, BuildAI auto-saves the completed .schem in Minecraft's buildai/blueprints folder. The editable design is also kept in memory for revisions until the game closes.",
            "Changes re-generate a complete candidate blueprint from the previous AI instructions. Review every change before uploading; revisions are not guaranteed to preserve all details.",
            "Enable Chat Mode in F8 to send ordinary chat messages privately to the generator. Use 'new <description>' for a fresh design and 'exit' to stop intercepting chat."
        },
        {
            "Import File / My Builds opens the in-game blueprint library, even when Minecraft's Java runtime is headless. Select a saved file or paste a path to a .schem/.litematic/.nbt.",
            "Supported inputs: Sponge .schem, legacy .schematic (limited numeric IDs), Litematica .litematic, and Minecraft .nbt structures.",
            "Unknown IDs, missing blocks, unsupported NBT and oversized files may be rejected. It is safer to reject than to silently replace blocks.",
            "Save Blueprint to Library creates a unique .schem without a Windows file dialog. Reload it any time using F8 > Import File / My Builds."
        },
        {
            "F8 > Feedback & Styles opens your local learning library. Select a past saved blueprint, choose a rating from 1 to 5, add detailed likes/dislikes, and click Save Feedback. Only user-saved feedback guides future prompts.",
            "For FULL building references without signs, open Analyze Whole Builds / Reference Library. Choose a saved build, paste a local .schem/.schematic/.litematic/.nbt path, or put multiple files into buildai/style-references and click Scan Reference Folder.",
            "Whole-build analysis records the real block palette, roof-area stair/slab counts, height distribution and several compact 3D detail excerpts. After you approve a reference it is saved locally and may influence subsequent AI building prompts.",
            "This is voxel/statistical analysis, NOT proof the AI visually understood every facade, room or structural technique. Inspect complex geometry and use styles only from builds you have permission to reference.",
            "You can also choose a local .schem or .nbt decoration pack. Place a sign immediately in front of each example. Write [BuildAI] on its first line and Chair, Table, Fountain, etc on its second line.",
            "Click Learn Labeled Decorations. BuildAI scans a small bounded cluster of real blocks around each sign, stores it locally, and includes selected examples as style references in future ChatGPT requests. It does not execute schematic NBT or transmit the whole file.",
            "Saved preferences live in the client game directory at buildai/style-memory.json. They are not included in GitHub and are not machine-learning model training.",
            "This prototype supports sign scanning on .schem and vanilla .nbt files. Sign metadata must survive schematic export. The crop is a simple nearby example, not yet an automatic precise object boundary or full 3D style simulator."
        },
        {
            "Choose the blueprint surface Y layer with the +/- controls. This indicates which level of the design meets the ground.",
            "Upload and Preview sends the design to the server. Once accepted, close F8 and move normally in your own player view; Creative flight works as usual.",
            "The preview starts completely IN FRONT of the player, with the building facade facing the player. In Survival, walk to the site; in Creative, fly as usual.",
            "Scroll the mouse wheel to move the building along WORLD Z. Press LEFT/RIGHT arrow keys to rotate it 90 degrees. Only while positioning does BuildAI intercept wheel scrolling; normal hotbar scrolling returns after placement.",
            "Aim at the TOP of a ground block, then press G to align the selected schematic surface layer to that ground height. This sets Y WITHOUT re-centering the building over your body. You can still use the wheel and arrows before locking.",
            "The project remains in review until you explicitly confirm it."
        },
        {
            "After G, PRESS ENTER to lock the rotated blueprint at its proposed XYZ. R or /buildai retry lets you choose a different site. F8 > Confirm & Build (or /buildai confirm) starts excavation only after successful validation.",
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
            "/buildai cinematic - conceal chunk-safe excavation behind a void shroud, then show blocks spiraling into the central black hole. Construction starts afterward.",
            "/buildai fast - skip the cinematic wait and use faster construction. Large excavations are still batched to protect server performance.",
            "/buildai normalize - repair fence, wall, glass pane and iron bar connections, including older completed builds near you.",
            "/buildai hide - dismiss the visual placement grid without modifying the real building.",
            "/buildai confirm - after inspection, excavate and start construction.",
            "/buildai cancel - stop and keep already placed blocks.",
            "/buildai undo - attempt ordinary blockstate restoration. Used supplies are NOT automatically refunded."
        },
        {
            "DO NOT test BuildAI on a valuable world without a backup.",
            "BuildAI now uses the normal Minecraft player camera for all placement. ChatGPT sign-in works with eligible accounts. Still incomplete: durable crash recovery, full rollback/refunds, complete legacy conversions and tested arbitrary redstone.",
            "The AI can generate block geometry and write schematics, but does not yet simulate every redstone mechanism. Check machinery in Minecraft.",
            "Generated designs are automatically saved after successful completion; check Minecraft/buildai/blueprints. Construction sessions are not yet persisted across server restarts.",
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
