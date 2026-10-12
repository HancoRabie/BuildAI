# BuildAI

**Speak buildings into existence in Minecraft.** Fabric 1.21.1, Java 21.

BuildAI is a freestanding Minecraft building mod: describe a structure to an AI, get an exact voxel blueprint, inspect/position it, and have the game construct it. It also imports community-created schematics and exports AI builds as .schem.

**Status: v0.1 developer prototype. Test on a backup or disposable world only.** Features not yet complete are listed in [docs/ROADMAP.md](docs/ROADMAP.md).

## Open source

BuildAI is licensed under **GNU GPL-3.0-only**. You can inspect, fork and contribute to this project under the terms in [LICENSE](LICENSE). See [CONTRIBUTING.md](CONTRIBUTING.md) and [SECURITY.md](SECURITY.md).

The source repository is public, but **Continue with ChatGPT has not been implemented yet**. Minecraft Chat Mode now routes generation through an authorized ChatGPT account when connected, or through an optional OpenAI API key otherwise. The planned user-consented, browser-based ChatGPT sign-in and eligible ChatGPT-plan usage are documented in [docs/OPEN_SOURCE_AND_CHATGPT.md](docs/OPEN_SOURCE_AND_CHATGPT.md). Public visibility alone does not automatically connect ChatGPT accounts.

## Requirements

Minecraft Java 1.21.1, Fabric Loader 0.16.7+, Fabric API, JDK 21 for development. No WorldEdit, Litematica, ChainFrames or PinLock dependency.

For text-to-build, v0.1 optionally connects to OpenAI API using an API key and an API-billed model. This is **not Sign in with ChatGPT** and does not consume ChatGPT Plus/Pro plan messages. No key is persisted by the mod or sent to Minecraft servers.

## Set up in IntelliJ (Windows)

Your project was previously cloned to the MC-MODS folder. In IntelliJ open that BuildAI directory and run:

    git pull origin main
    .\gradlew.bat build

The Gradle bootstrap downloads Gradle 8.10.2 on first run. Install Java 21 if Gradle reports a wrong runtime. Find the remapped mod JAR under build/libs, excluding sources and dev JARs. Use that JAR in a Fabric 1.21.1 test instance together with the matching Fabric API.

A GitHub Actions build also runs automatically on each latest main commit.

## In-game guide and conversational design

Press **F8 → How To** for the built-in guide (nine topics). No internet is required to read it.

BuildAI now supports an initial AI revision session. **Generate New** compiles a complete new blueprint; **Revise Existing** takes a follow-up instruction and sends the previous AI-authored design to the model, requesting a complete revised blueprint. The revised result remains local for review until you choose **Upload Blueprint**. Imported schematics do not have an AI edit history yet.

In private **Chat Mode** enabled from F8, ordinary chat messages create a new design when there is no earlier AI design, or revise the active AI design when present. Type `new <description>` to force a fresh building, `reset` to clear the AI design, and `exit` to disable chat interception. This is still an experimental, one-previous-design workflow, not a persistent ChatGPT conversation.

**Continue with ChatGPT** now has a client-only browser OAuth implementation. It's not yet proven against a real ChatGPT login or response; validate in a test world. An optional separately billed developer API key remains the fallback. See [docs/OPEN_SOURCE_AND_CHATGPT.md](docs/OPEN_SOURCE_AND_CHATGPT.md).

## Saving and loading builds (works in headless Minecraft)

**No Java Swing file dialog is used.** Minecraft's Fabric development JVM may be headless and cannot open `JFileChooser`, even on Windows. BuildAI now saves a unique `.schem` file **automatically whenever AI generation finishes successfully**. You can also press F8 → Placement / Export → **Save Blueprint to Library** to explicitly save an imported or generated structure.

Saved files live in the active Minecraft game directory under `buildai/blueprints/`. For IntelliJ's default Loom `runClient`, that is normally `<BuildAI project>/run/buildai/blueprints/`, not necessarily the regular launcher profile's `.minecraft` directory. F8 → **Import File / My Builds** opens a Minecraft-native list of these saved files, or allows you to paste the full path of an existing schematic file. The `.schem` is a permanent structure file, not merely a preview state; AI revision history is still in memory only.

**Important for existing sessions:** a BuildAI client launched *before* this feature was pulled does not have auto-save. Restarting it will lose an unsaved design. Use Upload & Preview → placement/confirm if you only need the structure in that disposable world; this is not equivalent to exporting a reimportable blueprint.

## Automatic real-web architectural inspiration

**F8 > Create / AI > Online Inspiration** defaults to ON. For every new
design or revision, BuildAI first *attempts* one genuine hosted Responses
API `web_search` operation for existing Minecraft build references. Only
responses with a completed actual `web_search_call`, a short design brief,
and usable HTTPS source citations are treated as successful research.
The original text-to-voxel generator then receives this cited inspiration
as untrusted data; the strict JSON blueprint format remains unchanged.

If web search is unavailable on the connected ChatGPT model/workspace or
developer API key, times out, or has no verifiable citations, the build
continues without it. Progress explicitly reports unavailable research;
BuildAI does not claim that unsourced model recollections are web research.
Successful searches show clickable source links in Minecraft chat, also
available from **Placement / Export > Design QA / Warnings**.

Online inspiration is a separate request that may increase generation time,
ChatGPT plan usage or API-key costs. It is **text-grounded research** rather
than guaranteed inspection of online images, and it does not imply permission
to copy somebody else's build. Toggle it OFF to use local style references
without web searches. Your preference is stored client-side at
`buildai/inspiration-settings.json`.

## Long-running ChatGPT generation and ETA

The BuildAI generation monitor now reports real elapsed time, output character count and average received characters per second. It never invents a fixed percent complete or a countdown from the unobservable model output length. After **two or more completed comparable generations**, BuildAI can display an approximate **remaining-time range** calibrated from local completed durations, always labeled rough. If the request exceeds that range, the UI returns to `remaining unknown`. Until enough samples exist, the UI shows `learning` rather than presenting a misleading clock.

The local history at `buildai/generation-timings.tsv` contains only generation categories, elapsed seconds and character counts—not prompts, OpenAI tokens, blueprints or account data.

## Minimized generation and multiplayer status reliability

Click **Return to Game (Keep Generating)** to close the large generation modal without stopping the AI request. The normal game remains playable; a compact nonblocking HUD shows the actual generation stage, elapsed time, and received characters. Successful completion keeps the blueprint and autosaves it, with a brief HUD confirmation. F8 reopens the controls without restarting the request.

The server's optional `buildai:project_status` packet limits long AI-generated names and material descriptions before encoding; progress packets are best-effort and should never pause construction. If another packet issue occurs, please provide `run/logs/latest.log` along with the screenshot. The prototype still requires careful world backups and does not recover active construction journals after a process restart.

## Local feedback and sign-labeled decoration style packs

F8 → **Feedback & Styles** lets players select a previously saved design, assign a 1–5 rating, and describe changes they liked or disliked. These notes are saved under `buildai/style-memory.json` in the local Minecraft game directory, never committed to GitHub. Only user-saved entries influence future ChatGPT/API prompts. This is **retrieval of preferences in a later prompt**, not fine-tuning or a modification of OpenAI's model.

To teach BuildAI a decorative style, create a normal `.schem` decoration pack with individual examples separated in the world. Put a sign directly in front of each model; its first line is `[BuildAI]`, and its second line is a short label such as `Chair`, `Table`, `Fountain`, `Bookshelf`, or `Chandelier`. A sign containing just one of the recognized object names also works. In **Feedback & Styles**, select the saved blueprint or paste the full path to an external file, then click **Learn Labeled Decorations from Schematic**. For each sign it recognizes, BuildAI samples nearby non-air block states and stores a compact, user-approved style reference to help future designs use similar detailing when requested.

Currently sign extraction works for **Sponge `.schem` and vanilla `.nbt`**, provided the file actually retains sign block-entity text. Imported Litematica files may be opened for review, but automatic sign extraction from `.litematic` is not implemented yet. Crops are bounded to local neighborhoods near each label; this first iteration does **not** infer perfect object boundaries, learn textures outside the crop, or guarantee exact multi-block copies. Schematic content is treated as untrusted data and no embedded commands, inventories, or entities are executed. Users should only import/reference content they have rights to reuse.

## Launch BuildAI directly from IntelliJ

Open the local `BuildAI` project (not ChainFrames), set **Gradle JVM to Java 21**, and run these in the IntelliJ Terminal:

```powershell
git pull origin main
.\gradlew.bat --version
.\gradlew.bat clean build
.\gradlew.bat runClient
```

The last command starts a separate **Minecraft 1.21.1 Fabric development client** with BuildAI loaded. No manual JAR copying is needed for this development test. The first launch may download the Minecraft/Gradle dependencies. If Java 21 is missing, select it in IntelliJ **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JVM**.

For the safest immediate smoke test, create a **brand-new Creative world** and press **F8 → Load Demo Cottage**. The demo produces real blueprint blocks with no OpenAI account, API key, internet, or extra building mods. Select **Export Blueprint as .schem** to verify serialization; **Upload Blueprint** to preview it. Pick X/Z with a left click, sneak-right-click the chosen surface block, then inspect collision warnings carefully. Only in a disposable world run `/buildai confirm` (server operator permission is required in this prototype).

**ChatGPT sign-in is now implemented in source but NOT yet live user-verified**. Open F8 → Continue with ChatGPT; approve plan usage in the browser and return to Minecraft. If login or account entitlements fail, use the optional OpenAI API-key fallback or offline demo.

## Seamless in-game building workflow

BuildAI keeps building creation and design inside Minecraft. The one-time authentication step must use the **system browser** according to the official native-client OAuth guidance; it cannot securely render the ChatGPT password form in a Minecraft WebView.

- F8 → **Continue with ChatGPT** displays a Minecraft status modal and opens OpenAI's secure browser login.
- After OpenAI redirects to BuildAI's local callback, the mod verifies authorization and attempts to restore focus to Minecraft. Whether Windows allows automatic foreground focus depends on OS settings.
- Future sessions reuse/refresh the account authorization when eligible; players do **not** need to sign in for every blueprint.
- Enter a prompt in F8 → **Generate New**, or enable **Chat Mode** and request/revise a design in Minecraft chat. AI uses the connected ChatGPT plan when eligible, with the developer API option as a fallback.
- The AI's generated geometry **becomes an in-memory BuildAI blueprint immediately**, without saving a file, using a file picker, or opening an external download.
- On success, the GUI automatically opens **Placement / Export**, with **Inspect Blueprint Layers** for a top-down cross-section and the existing **Upload & Preview** action for the full 3D ghost and surface/XYZ placement.
- Exporting a `.schem` file remains entirely optional.

Actual OAuth sign-in and the complete generate/preview/build flow still require in-game testing. Do not claim this code makes a fully functional chat account available without user consent and eligible plan access.

## Troubleshooting ChatGPT browser launch on Windows

In IntelliJ's Fabric development client, Java may report that AWT desktop/browser integration is unsupported even with Brave or another default browser installed. BuildAI now tries the Windows default-browser handlers directly after `Desktop.browse`.

If your selected default browser doesn't open, keep the Minecraft **Connect with ChatGPT** screen open and click **Copy Sign-in Link**. Paste that temporary URL into your preferred browser (Brave, Firefox, etc.). The original local callback remains listening and should finish login automatically when OpenAI redirects back to BuildAI. Alternatively click **Open Browser** to retry. The URL is unique to the sign-in attempt: do not post it publicly, and don't try a previously copied link after its sign-in session expires.

This only repairs local browser launching; account eligibility and OpenAI permission still need successful live testing.

## Live in-game AI generation status

When you press **Generate New** or **Revise Existing**, BuildAI opens a Minecraft-native generation monitor. It reports the **real** phases of generation: checking account/model availability, opening the HTTPS request, receiving model responses, compiling JSON operations into voxels, and validating block IDs. For streaming ChatGPT responses it also counts design characters actually received; developer API key calls report their milestones when the response arrives.

The monitor shows elapsed time and the time since the most recent activity. Its moving progress stripe is **indeterminate**, not a fabricated completion percentage. No claim is made about the model's internal chain of thought, research or unobservable steps. You can hide the monitor and play while generation runs; reopen F8 and press Generate to return to that same active monitor. A successful generation opens Placement / Export with the completed blueprint, while an error leaves the previous blueprint intact and displays the failure.

**Scale limitation for testing:** the current parametric compiler permits up to 96 blocks along each axis and 250,000 bounding cells. The prompt intentionally targets buildings around 48 × 32 × 48 or smaller; a historically faithful, furnished full-size White House may exceed the present geometry or model-output budget. A lengthy or failed generation is not evidence that a large building has been verified or rendered.

## ChatGPT login (test implementation)

In a disposable Minecraft 1.21.1 Fabric world, press **F8 → Continue with ChatGPT**. BuildAI opens your system browser on OpenAI's authorization site. Sign in with your own account and allow **ChatGPT plan usage**, then return to Minecraft. The F8 interface should display the connected account. You can then type a building request into F8 → Generate New, or turn on Chat Mode and type building requests into ordinary Minecraft chat. Follow-up prompts revise the active blueprint; type `new <prompt>` to start over, `reset` to clear the draft, or `exit` to return to normal chat.

The flow uses dynamic OAuth client registration with a stable local host ID, loopback PKCE, cryptographic ID-token verification, authorized plan scopes, account-model discovery, and streaming Responses API requests with `store:false`. **This has compiled, but real login and inference require in-game acceptance testing by a user with eligible ChatGPT Plus or Pro access.** User credentials are not sent through the Minecraft server. The mod saves authorized OAuth credentials in an owner-restricted local file under `~/.buildai/`; users can disconnect in F8 and revoke plan usage from ChatGPT Settings. Do not share that file or your ChatGPT password.

## Full-scale designs and normal-player placement

BuildAI no longer asks the AI to compress a building into the former 48×32×48 target. Generated architecture uses approximate real-world proportions at roughly **one horizontal Minecraft block per meter**, with a 1.8-block-tall avatar as human-scale reference. It must **not silently downscale** to fit a prompt budget. Full historical accuracy still requires measurements and in-world review.

The single-blueprint engine currently accommodates up to **4,000,000 cells** and up to 512 blocks along any axis (subject to the volume cap), with palette run-length upload compression. This is a **per-project runtime safety limit**, not unlimited megastructure support; unbounded architectures require future sectioned generation, streaming construction and persistent project journals. Oversized projects now report a clear error instead of claiming to have generated a miniature at full scale.

BuildAI no longer creates a fake observer/spectator camera or intercepts normal mouse movement, keyboard input, hotbar scrolling, and attacking. Once the server accepts **Upload & Preview**, close F8. A temporary ghost follows your *real* position. In **Creative**, fly with vanilla movement; in **Survival**, walk to the desired site. Aim at the specific ground block where the center of the building should be and press **G**. BuildAI anchors the proposed blueprint center to that block and aligns the user-selected schematic surface level with the block's top. The preview stays fixed while you inspect it from your normal camera. Press **Enter** to lock the full XYZ for server collision review, and **R** to retry a mistaken selection. Use F8 → **Confirm & Build** (or `/buildai confirm`) only after the server reports a locked, reviewed position. `/buildai hide` hides the ghost without canceling the project.

**Normal-camera placement, cinematic excavation, and large-blueprint construction still require actual Minecraft click-through testing.** A crash after `/buildai confirm` is not diagnosed by the Gradle `runClient` exit status alone: please supply the timestamped file in `run/crash-reports/` for the client stack trace. Passing CI does not prove live renderer stability or safe large-world construction. Use a disposable backed-up world. This prototype cannot safely recover ongoing projects after restart.

## First in-world test (creative, throwaway world)

1. Press **F8** to open BuildAI.
2. Enter an OpenAI developer API key (optional if importing rather than generating) and describe an ornate house. Click **Generate with AI**.
3. Or choose **Import File** to select .schem / .schematic / .litematic / .nbt using a desktop file picker.
4. Adjust **Surface Y** to the local blueprint layer intended to match the terrain surface. Use **Export .schem** to save generated build files to disk.
5. Click **Upload Blueprint**, close GUI, aim at a block and **left-click** to mark horizontal X/Z. See green/red outline guidance.
6. **Sneak-right-click** your desired surface block to lock absolute Y. An existing-volume collision report appears in chat.
7. Inspect the outline and only on a disposable world run **/buildai confirm**. Construction excavates overlapping blocks without drops, then builds Y-layer by Y-layer.
8. Use **/buildai status**, **/buildai cinematic**, **/buildai fast**, **/buildai cancel** and **/buildai undo**.

This prototype restricts world modification to **operators** until claim/permission checks are implemented. Blockstate undo is not a full world backup and will not automatically refund consumed supplies.

## Survival supplies

Mark a chest with a sign that reads **BuildAI** on any line. After selecting the building's surface, **sneak-right-click the chest** to link it. Add materials; they are consumed as each block is placed. Additional marked chests can be linked. Nearby chest HUD shows material totals and two progress bars.

Use **/buildai materials** to see consumed + available / required per material. When items run out, construction pauses until more appear.

Eligible whole-block family replacements, slab-to-slab, stair-to-stair, pane-to-pane, and similar exact shape families can be configured with:

    /buildai substitute minecraft:stone minecraft:cobblestone

Glass-block-to-pane is allowed only at glass positions with an original glass block immediately above or below. The compiler keeps horizontal skylight glass as full blocks. Invalid swaps are rejected. Fluid swaps are disabled until proper Survival accounting is tested.

**WARNING:** The approved build bounding volume is excavated after /confirm, including any nonair blocks in what should be interior space. It will refuse bedrock and existing block entities, and prints warnings for other existing blocks.

## Architecture

- Format adapters -> canonical blockstate blueprint.
- AI generator -> limited JSON geometry operations -> same canonical blueprint.
- Deterministic 3-axis voxel scan, canonical material accounting and shape-family substitution.
- Client file import/AI generation -> chunked compressed upload -> authoritative server validation.
- Selected X/Z + world surface block -> world-space alignment.
- Server tick engine -> staged excavation -> progressive material-consuming placement -> staging cleanup.

See [docs/AI_CONTRACT.md](docs/AI_CONTRACT.md) and [docs/REDSTONE_REFERENCE.md](docs/REDSTONE_REFERENCE.md). The AI receives a redstone component summary. The separate machine-readable redstone library under assets/buildai is intended to grow into verified mechanical templates; simply generating redstone does not prove a machine works.

## Safety notes

Never use a valuable world for v0.1. Imported files are capped, unknown blocks rejected, and command-bearing entities/inventories stripped. Run a backup before testing. Do not expect a partially built project to resume after server restart until persistence is implemented.
