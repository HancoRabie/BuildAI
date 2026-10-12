# BuildAI RBL — Redstone Blueprint Language v0.1
**Edition:** Minecraft Java 1.21.1 (Fabric).  
**Status:** Bundled notation and small reusable reference circuit library; **no world-verified modules yet**.

## Why this exists

BuildAI is a general-purpose architect and redstone engineer. Re-deriving a
known button, gate, latch or clock from natural language wastes inference,
and can create redundant/reversed blocks. RBL adds a **canonical circuit
catalog** usable alongside the existing Redstone Master Handbook.

The inspiration is [Sledger721's 2011 redstone programming languages
discussion](https://www.minecraftforum.net/forums/minecraft-java-edition/redstone-discussion-and/332089-redstone-programming-languages):
common syntax, reusable libraries and standardized capabilities. The
thread's proposed in-game computers/OS/compiler were not an implemented
Java 1.21.1 solution, and **RBL does not claim to be those things**.

### Two views of the same mechanism

1. **Visual layer:** short, appealing emoji+ASCII Y-layer grids.
2. **Canonical layer:** the exact block ID, ALL block-state properties, XYZ,
   ports, inputs, timing, expected output, reset and test cases. Generated
   blueprint JSON and this voxel data are authoritative; emoji is not.

BuildAI does **not** need to decode the emoji diagram as if it were image
recognition. The mod parses canonical JSON directly and verifies the picture
matches it. A prompt receives a small relevant excerpt from the cached
catalog, then ordinary blueprint JSON operations as before.

### Geometry convention (not optional)

- Origin is minimum local XYZ; `+X` east, `+Z` south, `+Y` up.
- Diagram columns go west→east; rows north→south; each named layer is Y.
- Tokens are separated by whitespace. One token represents **one voxel**
  no matter how many Unicode code points it renders as.
- `.` = AIR; **omitting** cells also means AIR in a template.
- **All wires/repeaters/button mounts require real support below or beside.**
- An arrow after a directional symbol is the **physical output / moving
  direction**, not a Minecraft blockstate property copied literally.
- `>` east (+X); `<` west (-X); `^` north (-Z); `v` south (+Z).
- Repeater/comparator `facing` names the **rear INPUT** in Java 1.21.1.
  Hence `R2>` expands to
  `minecraft:repeater[facing=west,delay=2,locked=false,powered=false]`.
  The comparator shares that rear-input convention. An observer's arrow
  marks its **red output**; its sensed face is opposite. Piston arrow marks
  its extension, and dispenser/dropper arrows mark their physical output.
- Repeaters R1/R2/R3/R4 have **1–4 redstone ticks** of delay, equal to
  **2/4/6/8 game ticks** at nominal 20 TPS. `C0` compares; `C1` subtracts.
  C0/C1 are *modes*, not "two tick" comparator delays.

## Starter symbol palette

| Symbol | Canonical meaning |
|---|---|
| `🟥` or `RS` | `minecraft:redstone_block`; constant power |
| `🔥` or `D` | `minecraft:redstone_wire`; dust, **not actual fire** |
| `⬛` or `S` | Supporting full block; choose actual block material explicitly |
| `.` or `AIR` | `minecraft:air` |
| `L` | Lever; include attachment face and powered state |
| `B` | Button; include attachment face, facing and material |
| `R1>` ... `R4>` | Repeater with 1..4 redstone tick delay, output EAST |
| `C0>` | Comparator in compare mode, output EAST |
| `C1>` | Comparator in subtract mode, output EAST |
| `O>` | Observer with red output facing EAST |
| `P>` / `SP>` | Ordinary/sticky piston extending EAST |
| `H↓` | Hopper output vertically down |
| `💡` | Redstone lamp |
| `~` | Water SOURCE block, not inventory bucket |

This is a **starter palette**. Other orientations and blocks must first have
exact version-scoped canonical mappings and tests. Don't guess glyphs.

## Reference example: manually controlled lamp

```text
id: rbl.lever_repeater_lamp.1
Y=1:   L  R1>  💡
Y=0:   ⬛  ⬛   ⬛

input:  press/toggle lever at (0,1,0)
output: lamp at (2,1,0)
output direction: east
expected: ON while lever is ON, OFF after signal removal / lamp delay
```

Canonical cells:
- `(0,0,0)`, `(1,0,0)`, `(2,0,0)`: `minecraft:stone`
- `(0,1,0)`: `minecraft:lever[face=floor,facing=north,powered=false]`
- `(1,1,0)`: `minecraft:repeater[facing=west,delay=1,locked=false,powered=false]`
- `(2,1,0)`: `minecraft:redstone_lamp[lit=false]`

The canonical JSON in `src/main/resources/assets/buildai/redstone_blueprint_library.json`
is source-of-truth. The Java class
`src/main/java/com/hanco/buildai/blueprint/RedstoneBlueprintLibrary.java`
reads that library **once per Minecraft process**, checks layer/XYZ/palette
agreement, supplies relevant circuit excerpts to the existing model context,
and can expand a named template into a real `Blueprint` object.

A second reference uses a stone button and R2 delay. Both are currently
**reference_unverified**, because compiling/validating geometry cannot
prove redstone action after ticks. These are NOT advertised as verified.

## Verification levels — no fake passing labels

1. **Reference/unverified:** clear standard notation and source for block
   properties; no execution evidence. Default for new circuit diagrams.
2. **Static validated:** exact XYZ/palette/diagram correspondence,
   resource parsing, dimensions, properties and blockstate checks pass.
   *Still not proof of actual machine operation.*
3. **In-game verified:** execute in a disposable Java 1.21.1 world (no
   unrequested mods/datapacks), record test inputs, version, states/tick
   outcomes, repetitions, power-loss and reset evidence. Only then may the
   library set `status=in_game_verified` with the evidence record.

Future library entries should include:
- `id`, version, exact voxel matrix + physical XYZ
- named input/output ports and connector positions
- idle/on/off/reset state and timing contracts
- item inventories, entities and player setup if needed
- test vectors (including false and edge cases)
- measured build/latency/material costs for optimizations
- limits, quirks (Java QC, water, updates, chunk unload, rotations)
- source and real world-test evidence

## Safe composition and rotation

Do **not** splice two modules together based only on their picture.
Composition must align matching ports, keep supports/clearances,
calculate redstone input/output strength, detect shorts/cross-talk,
preserve state and add any required inventory. Directional blockstate
rotation MUST use component-specific state transformations, not just
rotate the `>` character. Retest after rotation.

The first version supplies *exact reference templates to the AI*,
and an API that expands a named template. It does **not yet**:
- let model output RBL and invoke a full RBL-to-blueprint compiler,
- automatically stamp/combine modules from an AI plan,
- simulate a design in Java engine ticks or certify gameplay,
- generate endless auto-optimizations.

Those would be separate implementation milestones, with the real
in-game simulator as the correctness gate. We should promote exact
tested structures (including your proven sugar-cane farm) only after
their real 3D schematics/blockstates and test runs are captured.

## Keeping inference efficient

The core RedstoneKnowledge handbook remains cached. For non-redstone
prompts, the catalog is not loaded into model context. For a redstone
request, supply this notation header + **up to two keyword-relevant
exact modules**, not the entire library. If nothing matches, retain the
normal handbook and engineer the player's requested mechanism. No web
browsing or repeated "what does redstone dust do?" on each placement.
