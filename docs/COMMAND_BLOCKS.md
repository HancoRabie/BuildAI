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

## Server requirements

1. A trusted **level-4 server operator** reviews the game scripts.
2. For a dedicated 1.21.1 server set `enable-command-block=true` in
   `server.properties` and restart. This setting is also checked by BuildAI.
   Do not open this capability to arbitrary multiplayer clients.
3. Generate the build in F8 and **Upload & Preview**.
4. Command-driven maps currently use **zero-degree rotation only**. Rotating
   relative command coordinates safely will require future work.
5. Lock the world location using G, then Enter.
6. Stay **inside Minecraft** for the entire review. Open
   **F8 > Placement / Export > Design QA / Warnings > Review Command Block Map**.
   This native GUI pages through every script, physical placement ordinal,
   exact local XYZ coordinate, block type, redstone mode, and wrapped command
   text, without opening a browser or external editor.
   In Minecraft chat, `/buildai commands list` and
   `/buildai commands show <index>` also display the locked world XYZ for
   each command. The chat `show` index starts at 0; the GUI uses #1 onward.
7. As a trusted OP 4, run `/buildai commands approve`. This is a separate,
   explicit choice; mere upload is never authorization to run scripts.
8. Run `/buildai confirm` to start the ordinary excavation/construction.

During physical construction the generated command blocks are inert. Once
the **cleanup** and **normalization** phases finish, the new **COMMISSION**
phase installs command text and flags into actual
`CommandBlockBlockEntity` objects. After all are programmed, BuildAI
arms only entries explicitly marked as Always Active. Commands requiring
redstone stay signal-triggered. If construction fails or is cancelled,
BuildAI attempts to disarm known installed scripts.

Command blocks are not obtainable through normal Survival crafting:
approved operator-installed map machinery is excluded from the ordinary
Survival material manifest, rather than deadlocking the build at a resource
no legitimate chest can supply.

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

The in-game reviewer shows the client blueprint. The server remains the source
of truth for permission, approved placement and world coordinates. Approving
commands still requires trusted operator level 4 and is done in Minecraft chat.
