# BuildAI

**Speak buildings into existence in Minecraft.** Fabric 1.21.1, Java 21.

BuildAI is a freestanding Minecraft building mod: describe a structure to an AI, get an exact voxel blueprint, inspect/position it, and have the game construct it. It also imports community-created schematics and exports AI builds as .schem.

**Status: v0.1 developer prototype. Test on a backup or disposable world only.** Features not yet complete are listed in [docs/ROADMAP.md](docs/ROADMAP.md).

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

**Direct ChatGPT account sign-in is NOT implemented**. See [authentication plan](docs/AUTHENTICATION.md) for eligibility, consent, and security constraints. The v0.1 development path requires a separately billed OpenAI developer API key if AI generation is used.

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
