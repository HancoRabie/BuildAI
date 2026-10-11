# BuildAI v0.1 / next releases

This repository is a **development prototype**. A green Gradle build only verifies compilation, not block placement or redstone correctness. Test in a disposable Minecraft 1.21.1 world with backups.

## Implemented in v0.1 source
- Fabric 1.21.1 / Java 21 / Fabric API; independent of WorldEdit, Litematica, ChainFrames, and PinLock.
- F8 in-game GUI for description-to-blueprint OpenAI generation using an optional API key, file import, and .schem export.
- Private in-game chat prompt mode; normal chat is intercepted while active. Type exit to disable.
- Compact generative AI operations compiler (fill, box, shell, walls, line), bounds/coordinate checks.
- .schem Sponge versions 1-3, .schematic limited legacy IDs, .litematic region decoding, .nbt structure templates.
- Common blockstate-palette blueprint, material count, Y slices and X/Z cross-section scans.
- Lightweight sampling-based exposed surface outline during selection, horizontal left-click selection, subsequent sneak-right-click surface anchoring.
- Server-authoritative, op-only, collision review and explicit /buildai confirm before excavation.
- Progressive no-drop excavation, Survival item consumption from signed/linked inventories, cinematic and fast placement modes.
- Strict material family substitutions including glass BLOCK -> glass PANE only at positions with original vertical glass adjacency. Horizontal glass stays glass.
- Registered temporary construction blocks/slabs, simple overhang staging and cleanup.
- Nearby signed chest project status HUD showing remaining materials and progress bars.
- Basic blockstate rollback; AI-generated schematics exported as Sponge v3.
- A machine-readable redstone reference plus a small core rules summary used by AI prompt.

## Major functionality still missing / unsafe to claim
- Camera does not detach from the body yet. The preview is an outline, not a rendered photorealistic textured shell; rotation and free flight are not implemented.
- Build projects and rollback snapshots are in memory only; server shutdown/crash **does not resume projects**.
- Undo restores ordinary block states only and **does not refund placed construction materials**. It refuses to remove newly placed block entities, but cannot guarantee restoration of all player changes.
- No sandboxed, validated arbitrary redstone simulation, nor a tested prefab machine library. Redstone correctness must be tested in Minecraft.
- Imported block entities and entities are deliberately not restored in this cut (including sign text and inventory contents).
- Legacy .schematic conversion covers only a conservative block ID subset; unsupported IDs produce errors.
- GUI substitutions, cinematic falling-block animation, truly attached hologram above chest, automatic refund chest generation, multiplayer land-claim support and user-level permission management are future work.
- The first development build requires an **OpenAI developer API key** for text-to-build. Direct ChatGPT account sign-in requires supported partner authorization and is **not available** in v0.1.
- Creative mode uses the same staged construction code, but does not consume materials; instantaneous pasting of huge blueprints is future work.
- New custom supports are temporarily shown under overhanging slabs/stairs; they are not a full dependency solver for arbitrary redstone or gravity blocks.
- Imported schematics containing unknown modded blocks are rejected rather than silently converted.
- Current approval clears all cells of the approved bounding volume, including intended air. Review collisions very carefully.

## Release gating
1. Pass Gradle CI compile.
2. Generate a simple 12x8 house from text; export/import; verify block states and doors in disposable world.
3. Test all four schematic inputs with valid and malformed fixtures.
4. Run marked chest delivery and scarcity/pause/resume test in Survival (operator account).
5. Test vertical-only glass pane substitution, separate skylights and slabs/doors.
6. Test cancel/undo without block entities, then stress with complex terrain.
7. Implement crash-safe WAL journal before recommending installation on valued worlds.
8. Add actual free camera and positional mesh previews, then multiuser permission enforcement.
9. Introduce redstone prefab tests and GameTest scenarios for mechanisms.
