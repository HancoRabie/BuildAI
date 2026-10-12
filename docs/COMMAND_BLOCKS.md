# BuildAI command-block game mechanics — Minecraft Java 1.21.1

## What is implemented

AI blueprints can now include a bounded `commands` array beside their ordinary
block geometry. A command record looks like:

```json
{
  "x": 4, "y": 2, "z": 8,
  "command": "team add red",
  "auto": false,
  "trackOutput": false
}
```

The voxel at that coordinate **must** be
`minecraft:command_block`, `minecraft:chain_command_block`, or
`minecraft:repeating_command_block`. Every record is validated before
network transfer and again on the server. Up to **128 commands**, each up to
**768 characters**, are accepted per blueprint. This is an initial limit,
not a promise that large full-game builds always fit.

The AI receives the running Minecraft version, and the prompt specifically
requires Java 1.21.1-compatible command syntax and a complete functional
minigame plan. Command block modes depend on the actual block type and its
facing/conditional block states; the `auto` setting controls Always Active
versus Requires Redstone.

Commands are preserved in BuildAI network JSON and a private,
**non-executable** `BuildAICommandPlan` metadata field in exported Sponge
v3 `.schem` files. Reimported files **never automatically activate** scripts.

## Scrollable commissioning checklist and approval diagnostics

On completion, F8 automatically opens a Minecraft-native checklist of the
**actual server command plan**. Scroll the left-hand list to review all the
commands; each row displays a checkbox, the unique stable #number, a short
**heuristic** description of its role in the build, and a command preview.
Select a row to inspect the full command on the right (on a sufficiently wide
window); **Show Full Script in Chat** is available on narrower screens.
The description is based on command syntax and does **not** certify that the
game logic is correct.

Choose **Select All**, **Approve Checked**, or **Approve All**. These controls
send an approval request directly to BuildAI's server and the GUI displays
the actual result in place. If approval fails due to permission, disabled
command blocks, or a changed blockstate, the interface shows the exact reason.
The server still checks ownership, selected indices and command-block
position/facing. Failed approval leaves the map waiting without programming
any scripts.

**Permissions:** dedicated servers and any shared multiplayer/LAN instance
require the map owner's level-4 operator authorization. On a private
singleplayer integrated server with **exactly one connected player**, the
owner can approve with cheat-enabled level-2 permission. This exception
does not bypass permission checks on public servers.

## No-rush post-build approval

Approval happens **after** the physical structure is fully constructed, cleanup
and connection normalization are finished. Fast or tiny builds never bypass
review. When a blueprint contains command instructions, the server enters
`WAITING_COMMAND_APPROVAL` and **stays paused indefinitely**. No scripts are
programmed or enabled by the countdown or by build speed.

The server automatically sends the owner a verified map of the actual scripts,
including one-based command numbers, block IDs and facing, local XYZ and
locked world XYZ. BuildAI opens an in-game review GUI when possible. If you're
in another screen you can return to the game or reopen review through
**F8 > Placement / Export > Design QA > Review Command Block Map**.

Inspect each command and select **Approve All** or mark any subset and choose
**Approve Marked**. The GUI sends the same Minecraft chat command as typing:

```mcfunction
/buildai commands list
/buildai commands show 1
/buildai commands approve all
/buildai commands approve 1,2,4,7,8,9
/buildai commands 1, 2, 4, 7
```

All command numbers are **1-based everywhere**, including chat, the review
screen and the approval parser. You may close the GUI and come back later;
the server will keep waiting without installing scripts. Only the authorized project owner can approve: OP4 on shared/dedicated
servers, or the sole cheat-enabled singleplayer owner at OP2.

**Partial approval:** approving #1, #2 and #4 commissions those exact three
coordinate-mapped scripts. Other command-block cells are actively left blank
and disarmed. An approval selection completes the review for the project;
changing that selection after commissioning requires a future editing/review
workflow (not yet implemented). Never assume only part of a CTF script is a
functioning game.

### Server configuration and execution ordering

For a dedicated Java 1.21.1 server, set `enable-command-block=true` in
`server.properties` and restart before approving scripts. The build can
construct its physical geometry beforehand, but approval is rejected until
command blocks are enabled. Command-driven maps currently use **zero-degree
rotation only** to avoid altering relative command coordinates unsafely.

During commissioning, BuildAI verifies each selected block's exact local
position, world position, type and facing. It programs or explicitly clears
all script cells first, then arms only approved Always Active entries. A
failed verification halts the project instead of guessing which script
belongs to which command block.

The commands remain subject to Minecraft's world security rules and server
permissions. Command blocks are unobtainable through ordinary Survival
crafting; approved map machinery is excluded from the Survival materials
manifest.

## Reattaching all saved scripts after Minecraft restart

A Minecraft restart clears the old in-memory BuildAI construction/approval
session, **but not the saved blueprint or physical blocks**. For any
previously built project with a locally saved BuildAI `.schem`:

1. Stand in your old build and aim at **any** command block belonging to it
   (within 9 blocks). Keep the player looking at it.
2. Open **F8 > My Blueprints**, select the exact saved `.schem`.
3. Click **Attach Command Plan to Existing World Build**. BuildAI reads the
   saved script metadata from the server's local blueprint directory, tests
   possible alignment origins, and will accept **only one unambiguous full
   match** of every saved command-block position and facing. It never pastes
   the building or changes blocks during this matching step.
4. Close My Blueprints when prompted. The native command-commissioning
   checklist opens with the complete recovered plan. Select scripts or
   **Approve All**.
5. The server verifies all selected cells again, installs scripts, and only
   then arms the saved Always Active settings.

The chat equivalent is
`/buildai commands recover exact-saved-filename.schem`
while looking at an existing command block. This works in local
singleplayer because the saved library and integrated server share the same
game directory. Dedicated servers must have the matching schematic file in
**their own** `buildai/blueprints` directory.

If BuildAI cannot uniquely identify the full arrangement (unloaded chunks,
edited/missing blocks, changed facing), it will refuse recovery rather than
risk programming the wrong blocks. A schematic without embedded command
metadata cannot recover scripts it never saved; the separate
`/buildai commands preset` or `/buildai commands set` workflow can
manually stage individual blank command blocks.

## Fixing an existing blank command block — no rebuild required

A generated physical command block can be EMPTY if an earlier AI output did
not include a matching `commands` array. The message `Command plan: 0`
means there are no scripts to approve. **Approval cannot invent the command
text.** To recover an existing world build (even after restarting Minecraft):

1. Look directly at the empty command block from within eight blocks.
2. As a level-4 operator, stage a script using one of the presets below, or
   use `/buildai commands set <command without leading slash>`.
3. The BuildAI in-game review opens with the real block's world coordinates.
4. Run `/buildai commands approve all` or approve selected entries in the UI.
5. The command is written to the actual existing command-block entity,
   retaining the existing block and world. Presets default to redstone
   triggering so a button can activate the block safely.

```mcfunction
/buildai commands preset day
/buildai commands preset night
/buildai commands preset sun
/buildai commands preset rain
/buildai commands preset thunder
/buildai commands set time set noon
/buildai commands list
/buildai commands review
/buildai commands approve all
```

**Important:** The targeted block must be empty; BuildAI won't silently replace
a command already entered by a player. Singleplayer cheats/server operator 4
and enabled command blocks are required. Standalone staged changes are
in-memory until approved, but once written, the actual command block is saved
as ordinary world block-entity data. If approval fails, the world block stays
unchanged. Command reviews can be reopened inside Minecraft with
`/buildai commands review`.

Chat Mode is for natural-language blueprint requests, not the command
configuration syntax above. Typing `gpt` alone in Chat Mode was previously
treated as a new/revision prompt; it now explains the correct controls.

## Restriction / security model

Only the following initial command roots are accepted:

`scoreboard`, `team`, `tag`, `effect`, `give`, `clear`,
`tp`, `teleport`, `title`, `tellraw`, `playsound`,
`particle`, `execute`, and `spawnpoint`.

Nested `execute ... run` actions are also checked. Powerful or unrelated
administrative commands such as `op`, `deop`, `fill`, `setblock`,
`data`, `function`, `schedule`, and `stop` are not exposed by this
initial AI plan. This is defense in depth, **not** a substitute for operator
review or for the server's own permission model. Command blocks can affect
other players. Do not approve code that you haven't read.

## What is **not** verified yet

BuildAI now has the *pipeline* needed for programmed command blocks, but
**does not automatically prove minigames work**. The architectural QA adds
CTF-specific heuristics for team logic, scoreboards, loadouts, triggers,
spawning and announcements, and warns that full gameplay still requires
testing. Command syntax and selectors must be tested in a real server world.
World undo is not yet guaranteed to restore block entities, and a partly
completed command-driven map should **never** be left running unattended.

For a real capture-the-flag acceptance test, verify at least: two players can
choose different teams, the game starts once, each loadout is issued exactly
once, the flag can be taken and returned, scoring occurs only for valid
captures, win logic stops active rounds, death/respawn behaves correctly, and
reset clears every prior game-state objective. Check cross-team exploits and
repeating command block TPS.

**Physical chains** are a separate design problem: pane/fence normalization
cannot bridge gaps between chain blocks. Chain segments need actual adjoining
blocks with appropriate `axis` orientation. Do not assume normalization will
create missing chain links.

## Deterministic command-block coordinate mapping

Every assigned script is bound to **one unique local blueprint coordinate**.
The program normalizes command entries in the same Y/Z/X order their
command-block cells appear in the physical build queue and preserves this
mapped list during network transport and schematic export/import. On placement,
the server calculates the actual position as `locked_origin + local_XYZ` and
verifies the precise command-block type and facing before installing its code.
It rereads the stored command text before moving on; only after all scripts
have been installed does it arm automatic command blocks. Duplicate positions
are rejected, and missing scripts produce Design QA warnings rather than
silently assigning a neighbor's command. This is coordinate mapping, not
"first block gets first random script" or a dependence on packet arrival order.

The automatic in-game reviewer uses a fresh server-authoritative snapshot,
not a possibly outdated local schematic. Server-side OP4 permission and
mapping validation are mandatory. Approval commands are sent from inside
Minecraft and are never initiated by a timeout.
