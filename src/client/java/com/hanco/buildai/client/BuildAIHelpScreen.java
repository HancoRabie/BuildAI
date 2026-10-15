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
        "Welcome to BuildAI", "Creative quick start", "Survival quick start",
        "BuildAI chest labels", "Survival material costs", "Foundation & hollowing",
        "Undo and item returns", "Access & permissions", "ChatGPT and API access", "Generate and revise",
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
            "Creative quick start: 1) Press F8 to create a structure with AI, load a saved schematic, or use the offline demo.",
            "2) Select the intended ground or surface Y-layer, then Upload and Preview. Fly to the build site; the ghost starts in front of you.",
            "3) Press G to freeze the preview. Adjust it with arrows and mouse wheel; CTRL+arrows rotate or move forward/back. Press ENTER to lock. F8 > Placement / Export > Retry Placement resets.",
            "4) Inspect the space BEFORE confirming. /buildai confirm clears the full volume; /buildai confirm excavate:no overlays existing terrain.",
            "5) foundation:no SANITIZES THE SCHEMATIC: omitted foundation blocks are never placed. It does not delete existing world terrain. Only excavate:yes or hollow:yes explicitly clears terrain.",
            "6) Creative building does not require or consume inventory resources. Follow /buildai status; inspect rooms and redstone before trusting the result.",
            "7) /buildai normalize repairs neighbor connections; /buildai undo restores recorded blocks after a safety check. Still use a backup."
        },
        {
            "Survival quick start: 1) Press F8 to generate/import a design, preview it, and lock the site using G and ENTER.",
            "2) Near the site, place an ordinary chest and put a sign immediately BESIDE it. Write BuildAI on any line, front or back.",
            "3) No sneak-click or manual chest link is required. Supply chests beside your player or near the project are discovered automatically; add several if needed.",
            "4) Put the needed items into signed chests. Use /buildai materials for the exact materials list and delivered/used quantities.",
            "5) /buildai confirm starts progressive construction, removing only the terrain allowed by your chosen excavate/hollow mode.",
            "6) Construction automatically withdraws the item for each placed block, pauses for shortages, and resumes when you add more materials.",
            "7) Building scaffolds created temporarily by BuildAI are FREE and are NOT in the supply manifest. Skipped foundation blocks are also FREE.",
            "8) If you undo, BuildAI restores world blocks and returns consumed supplies to the SAME signed/source chests. Keep those chests in place."
        },
        {
            "Labeling Survival supply chests: Put a sign immediately adjacent to the chest (side, above or below) and write BuildAI, any capitalization.",
            "BuildAI automatically searches for marked chests near the locked site's corner and near the active builder anywhere along the structure.",
            "The label is the only step. You do NOT need to sneak-right-click or link the chest; normal sign placement and chest opening work as vanilla Minecraft.",
            "The chest must stay in loaded chunks and near the building. You can move around a very large site so newly labeled chests are discovered.",
            "Double-chest halves are treated as real inventory sources. BuildAI remembers the EXACT chest position used for each withdrawal.",
            "The signed chests are protected from being broken during active construction. Removing labels mid-project can interrupt future supply discovery.",
            "If supplies are missing, stand next to your marked chest, wait a couple of seconds for discovery, then check /buildai materials."
        },
        {
            "Survival material accounting: The list counts actual BLOCK ITEMS, not temporary BuildAI scaffolds or the air in rooms.",
            "foundation:no excludes omitted foundation items from material costs because they are skipped in the placement plan. It does not mine existing terrain and needs no foundation supplies.",
            "The original blocks intentionally retained for basements, walls, stairs, redstone, and decorations still cost their normal items.",
            "Two-block structures (such as doors/beds) are billed for the item once, not for both block-state halves.",
            "Water requires TWO starting water buckets total for a renewable supply; lava requires ONE bucket per designed lava source.",
            "Fluid buckets turn into empty buckets in their source chest. Undo exchanges those empties back for the original filled buckets.",
            "Missing ordinary block items pause construction. You can fill additional labeled chests to continue without restarting the blueprint.",
            "If a block has no obtainably mapped item, BuildAI may pause; that requires a different design or administrative support."
        },
        {
            "Choosing foundation and hollow options: Default /buildai confirm removes the ENTIRE selected volume. Be extremely careful.",
            "foundation:no finds the broad repeated connected base footprint in the lower build levels, ignoring schematic AIR padding and tiny dangling stone supports beneath it.",
            "A foundation layer needs >=90% footprint coverage. Automatically detected blocks need >=51% of the FULL footprint; EACH block ID explicitly named after foundation:no: needs only >=20%.",
            "BuildAI scans qualifying layers upward until failure, then omits attached eligible lower tails from the SCHEMATIC too. Omitted cells are NOT world edits. Actual underground basement blocks are protected in the design.",
            "Stone, grass_block, dirt and moss_block are recognized automatically at 51%. foundation:no:moss_block explicitly lowers MOSS ONLY to 20%; other automatic materials stay at 51%.",
            "hollow:yes clears existing-world blocks ONLY where the schematic calls for AIR inside recognized X/Y/Z-bounded rooms and corridors.",
            "Use /buildai confirm foundation:no excavate:no to omit a schematic foundation WITHOUT digging terrain. Adding hollow:yes explicitly clears verified existing-world terrain inside intended empty rooms.",
            "A solid basement may be indistinguishable from a removable schematic foundation; inspect the saved design. foundation:no never clears existing terrain, but ordinary blueprint block placement can still replace overlapping world blocks."
        },
        {
            "Undo and Survival refunds: /buildai undo preflights protected blocks, then restores every recorded original blockstate in batches.",
            "When the world is restored, BuildAI checks whether EVERY original supply chest can accept all items consumed from that specific chest.",
            "The refund is all-or-nothing: if a source chest is removed or full, undo waits for that EXACT chest to return or have enough room. No items are silently dropped.",
            "For water/lava supplies, keep the empty buckets in their source chest (or put them back) so filled buckets can be returned without duplication.",
            "Use /buildai status to read any waiting-for-refund message. The world can be restored while the inventory refund is still pending.",
            "/buildai undo force is dangerous: it may destroy player-edited block entities. Do not use it unless you understand the warning.",
            "IMPORTANT: Undo and its chest withdrawal receipts are still MEMORY-ONLY. Do not restart the server or replace the project before refund finishes.",
            "World backups are required for crash safety. Cancel without undo keeps placed blocks and does NOT return materials."
        },
        {
            "BuildAI WORKS WITH CHEATS OFF in singleplayer Survival and Creative. The singleplayer world owner is a BuildAI administrator automatically.",
            "F8 -> generate/import a blueprint -> G to freeze preview -> Enter to lock -> /buildai confirm. The Allow Cheats world setting does NOT affect BuildAI.",
            "The integrated-world HOST keeps this permission even while hosting LAN. LAN visitors do NOT gain the host's privileges.",
            "On dedicated servers, OP2+ or an explicit BuildAI UUID grant allows all ordinary building workflows without giving the player Minecraft OP.",
            "An administrator (singleplayer host or dedicated OP4) can /buildai access grant <online-player-name-or-UUID> and /buildai access revoke <online-player-name-or-UUID>.",
            "/buildai access displays your role; /buildai access list shows saved UUID grants (admin only). Grants persist per world in buildai/access.json.",
            "Builders can upload, place, build, manage Survival materials, substitute, retry, cancel and SAFELY undo their OWN projects.",
            "Force Undo and command-block script programming/approval are administrator-only. BuildAI grants never elevate vanilla operator permissions.",
            "There is no region-claim protection yet. Only grant BuildAI to trusted players on shared worlds, as buildings can modify nearby terrain.",
            "Server-side permission checks apply to both chat commands and uploaded blueprint/placement network packets."
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
            "Online Inspiration is ON by default. F8 > Create / AI > Online Inspiration switches real web research on or off. BuildAI looks across multiple different published Minecraft builders, extracts reusable craftsmanship techniques and then designs FROM YOUR PROMPT. It does not pick one build to reproduce.",
            "Reference names and web links are for your review only; they are not passed to the blueprint model. Research is used to improve detail, depth, materials, massing and landscape treatment, never to override your requested concept, size, rooms or original design.",
            "Research may add time, API-key charges and plan usage. The model/tool must support web search; BuildAI honestly reports when research failed or had no usable citations, and still generates the structure using the normal local architectural references.",
            "After an actually sourced generation, source links appear as clickable Minecraft chat messages. Reopen them via F8 > Placement / Export > Design QA / Warnings > View Cited Web Inspiration Sources. Online text inspires original geometry, not a direct copy; web-search results do not guarantee the model has seen images.",
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
            "After G, LEFT/RIGHT arrows nudge sideways relative to the captured camera heading; UP/DOWN changes Y. CTRL+LEFT/RIGHT rotates in 90-degree steps; CTRL+UP/DOWN or the wheel moves forward/back. Hotbar scrolling returns when placement is locked.",
            "Press G anywhere to freeze the ghost exactly where it is shown. No ground block or raycast required. Walk/fly around it, fine-tune with arrows/wheel, and press ENTER to lock XYZ. Use F8 > Placement / Export > Retry Placement to reset.",
            "The project remains in review until you explicitly confirm it."
        },
        {
            "After G, use arrows and wheel to fine tune the ghost, then ENTER locks its XYZ. Use F8 > Retry Placement or /buildai retry to reset it (R remains available for Cobblemon). F8 > Confirm & Build begins excavation only after server validation; protected containers and unloaded chunks are still rejected.",
            "DEFAULT /buildai confirm: clear all existing blocks in the blueprint volume before placing the build. /buildai confirm excavate:no: overlay blueprint blocks without clearing its empty spaces. Existing terrain in blueprint-AIR cells remains.",
            "/buildai confirm hollow:yes explicitly clears recognized interior schematic AIR in the WORLD. Adding foundation:no only omits foundation blocks from the schematic placement list; it does not excavate world stone. Use a backup before hollowing.",
            "In hollow mode, only existing terrain inside wall-defined blueprint AIR cells is excavated; generated decorations/floors/walls are never removed. Rooms with four-sided wall containment AND wall-defined corridors with long parallel standing walls qualify. No recognized area? The request fails safely. Protected containers/bedrock and unloaded chunks reject the whole operation before edits.",
        },
        {
            "Place a chest near the building, then attach or place an adjacent sign saying BuildAI (any line, front or back). No BuildAI right-click interaction exists; G freezes ONLY the building preview.",
            "Keep the sign and source chest where they are. BuildAI searches near the site and near you while you work, and draws from multiple chests as required.",
            "The material total is based on actual placed item requirements. Omitted foundation, temporary construction blocks and temporary slabs are NOT charged.",
            "Use /buildai materials for supplied, needed and used counts. Projects pause safely when materials run out and resume when the signed chest is refilled.",
            "Undo returns consumed items to their individual SOURCE chests after restoring the world. If a chest is missing/full, the item receipt remains pending and /buildai status tells you why.",
            "In-memory receipts and construction cannot yet survive a server restart; use a backup and complete undo/refund before leaving."
        },
        {
            "/buildai status - report active project phase and construction count.",
            "/buildai materials - show required, used and currently supplied materials.",
            "/buildai substitute <original> <replacement> - allow compatible material substitutions before construction.",
            "/buildai cinematic - conceal chunk-safe excavation behind a void shroud, then show blocks spiraling into the central black hole. Construction starts afterward.",
            "/buildai fast - skip the cinematic wait and use faster construction. Large excavations are still batched to protect server performance.",
            "/buildai normalize - repair fence, wall, glass pane, iron bar AND redstone dust connections. A final signal pass wakes supported vanilla 1.21.1 devices after wiring. Works on finished BuildAI projects and nearby older builds.",
            "Chains are not self-connecting fences. BuildAI now flags one-block gaps between aligned chain segments as QA warnings; add real links to bridge a gap.",
            "For command-driven maps, AI supplies a command plan mapped to actual placed blocks. Lock placement at zero-degree rotation. Build the physical map normally with /buildai confirm: you do NOT need to race against construction to approve commands.",
            "AFTER the physical build and naturalization finish, BuildAI pauses indefinitely at WAITING_COMMAND_APPROVAL and automatically opens the full command review IN Minecraft. Each script has a stable number, exact world location and matching block. You can close and reopen the review at F8 > Placement / Export > Design QA.",
            "Only BuildAI ADMINS may program/approve command-block scripts: the singleplayer host even with cheats OFF, or dedicated OP4. Dedicated servers also need enable-command-block=true. Review each script carefully: it executes with server authority. /buildai commands approve all or /buildai commands approve 1,2,4 authorizes selected scripts. Ordinary BuildAI builders never receive this power.",
            "/buildai hide - dismiss the visual placement grid without modifying the real building.",
            "/buildai confirm: excavate:no overlays only intended schematic non-AIR blocks; unpainted terrain stays. foundation:no SANITIZES the schematic by omitting majority foundation material from consecutive lower layers (>=90% connected footprint coverage, automatic type >=51% OR each explicitly named type >=20%); existing world blocks at omitted positions are NEVER cleared. foundation:no by itself defaults to excavate:no. hollow:yes separately requests interior AIR excavation, while excavate:yes explicitly requests full-site clearing. hollow:yes excavate:yes is invalid.",
            "/buildai cancel - stop and keep already placed blocks.",
            "/buildai undo - safely restores the original world blockstates in batches, then returns Survival supplies to their original BuildAI chests without partial refunds. Full/missing chests hold the pending refund until fixed. /buildai undo force (BuildAI Admin only) can destroy edited inventories or scripts. Undo receipts are NOT crash-persistent yet."
        },
        {
            "DO NOT test BuildAI on a valuable world without a backup.",
            "BuildAI uses the normal Minecraft camera for placement. In-session Survival undo now refunds original supply chests, but crash-safe persistence of the build AND refund ledger is not implemented. Complex redstone and legacy imports are still experimental.",
            "When your request mentions redstone, circuitry, automatic machines, pistons, hoppers, observers, crafters, sorters or command blocks, BuildAI automatically consults its bundled Java 1.21.1 Redstone Master Handbook. It covers real device quirks, support, power routing, timings, QC, memory, inventory and failure/recovery cases.",
            "The AI is instructed to provide a redstone_plan for functional machines: input, signal path, output, timing, reset and in-world tests. Redstone Design QA flags absent paths, missing controls, default-facing devices and unsupported wire. This reference is NOT real model training or proof a machine works.",
            "Redstone normalization is connection and neighbor-update repair, NOT a full redstone simulator. It does not add missing wire, flip repeater/comparator facing, choose delays or certify logic. In-game testing is still required. Running manual /buildai normalize on a powered existing circuit can trigger pulses.",
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
