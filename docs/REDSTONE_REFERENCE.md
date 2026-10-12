# BuildAI Redstone Engineering Reference (Java 1.21.1)

The **primary, bundled guide** is now the
[BuildAI Redstone Master Handbook](../src/main/resources/assets/buildai/redstone_master_handbook.txt).
This extensive Java 1.21.1 reference covers source blocks, dust, repeaters,
comparators, memory/latches, timing, update order, quasi-connectivity,
observers, pistons, item logistics, automating crafting, rails, command
block commissioning, and circuit test scenarios. The
[machine-readable glossary](../src/main/resources/assets/buildai/redstone_reference.json)
lists the key mechanics and quirks of **59 component families** (including
some redstone-relevant entities, which are explicitly identified as entities).

## Automatic AI usage

Both of BuildAI's inference routes—connected ChatGPT and developer API
generation—load **relevant sections of the cached packaged master handbook as developer/system
context** whenever a player's new request or existing design involves redstone,
automated machines, pistons, logic circuits, sorters, command blocks, or
functional minigames. Revisions keep that context even if the immediate
revision asks to change only decorative architecture. For ordinary builds,
BuildAI sends only a compact redstone reminder to avoid unnecessary latency.

The AI is instructed to produce actual signal-bearing block geometry plus a
top-level `redstone_plan.circuits` audit manifest for each functional circuit.
Each entry documents `name`, `input`, `signalPath`, `output`, `timing`,
`reset`, and `test`. A read-only redstone QA pass flags missing real
components, controls/outputs, unsupported dust, default-faced directional
blocks, and incomplete circuit plans.

**Important:** referencing a technical handbook is not the same as training
the model, proving electrical behavior or performing a game-level simulation.
No generated redstone machine should be advertised as tested until verified in
Minecraft Java 1.21.1. Some setups need genuine inventory NBT or entities that
ordinary voxel schematics do not carry (e.g. crafter recipes, item frames).

## Version-specific correction

In Java 1.21, copper-bulb **oxidation changes brightness** (15/12/8/4),
not redstone timing. Crafters use redstone pulses and comparator output
0..9 based on occupied or disabled slots and **are not quasi-connected**.
Quasi-connectivity does affect Java pistons, droppers and dispensers.
The later 1.21.2 **experimental** dust update-order rules should not be
assumed for the 1.21.1 engine.

### Fact-check references

- [Minecraft Java 1.21 official release notes](https://feedback.minecraft.net/hc/en-us/articles/27547857163917-Minecraft-Java-Edition-1-21-Tricky-Trials)
- [Official Minecraft comparator explanation](https://www.minecraft.net/en-us/article/taking-inventory--redstone-comparator)
- [Minecraft Wiki Java quasi-connectivity tutorial](https://minecraft.wiki/w/Tutorial:Quasi-connectivity)
- [Technical Minecraft Wiki quasi-connectivity](https://techmcdocs.github.io/pages/GameMechanics/QuasiConnectivity/)
- [Minecraft Java 1.21.2 experimental redstone changes](https://feedback.minecraft.net/hc/en-us/articles/31261174284557-Minecraft-Java-Edition-1-21-2-Bundles-of-Bravery)

---


Reference notes for AI machine design; not a substitute for function tests.

| Item | Engineering rule |
|---|---|
| Redstone dust | Power 0-15, attenuates per step, directional connections. |
| Repeater | Directional, power refresh, 1-4 ticks, side locking. |
| Comparator | Compare/subtract and container fullness; facing matters. |
| Observer | Observes a specific face, outputs a pulse at the opposite face. |
| Piston | Push up to 12 movable blocks; sticky retracts one; cannot move bedrock. |
| Hopper | Outputs in facing direction; redstone locks transfer. |
| Dispenser / Dropper | Power pulses trigger actions; behavior differs by item. |
| Redstone torch | Inverts power; burns out on rapid oscillation. |
| Lever / button / pressure plate | Constant vs temporary activation. |
| Rails | Normal, detector, powered, activator have different functions. |
| Doors, stairs and slabs | Must preserve hinge, facing, shape, half and fluid flags. |

## Validation tiers
1. Parseable block states and 3-axis voxel coverage
2. Dependency checks, support and clearance
3. Redstone logic consistency and orientation checks
4. Functional in-game simulation with test scenarios

The initial BuildAI code is not a Tier 4 redstone simulator. Validation must never be misrepresented as proof machinery works.

## Mechanical redstone + fluid interactions: piston floodgates

The bundled [Redstone Master Handbook](../src/main/resources/assets/buildai/redstone_master_handbook.txt)
now includes a dedicated advanced section on piston-controlled water gates,
crop flushing, fluid tick propagation and automation timing, based on
established technical documentation. This information is **compiled into
BuildAI**: it does not need to browse an external website or solve CAPTCHA
challenges while generating.

An **extended piston head is a physical barrier**, never a pipe. A functional
harvest gate must define BOTH states: **CLOSED** (head/plug occupies channel
throat, source contained upstream) and **OPEN** (head retracted, physically
open cell between the retained source and the irrigated/harvested channel).
A sticky piston may retract one movable plug, while an ordinary piston
only withdraws its head. If idle should be closed, an inverted/latching
control can hold a piston extended until harvest is triggered.

Water in the Overworld flows about **one horizontal block per 5 game ticks**.
A button pulse and a repeater's 2–8-game-tick propagation delay cannot be
assumed to produce a long enough flood by themselves. Design an explicit
**hold-open timer** (pulse extender, configured hopper timer, latch/clock,
or manual lever) plus a reset after downstream drops have reached collection.
The AI is required to produce an inert `fluid_gate_plan` with:
`source`, `gate`, `closedState`, `openState`, `path`,
`holdOpenTicks`, `reset`, `collection`, `testing`.

BuildAI's conservative **FluidGateAudit** flags impossible water-source
cells directly in a piston's extension path, absent open/closed state plans,
and very short hold-open times. It never silently rotates a piston or claims
that the resulting machine passed actual in-game fluid simulation.

### High-value technical source links

- [Minecraft Wiki — Water gate tutorial](https://minecraft.wiki/w/Tutorial:Water_gate)
- [Minecraft Wiki — Crop farming and piston water harvest](https://minecraft.wiki/w/Tutorial:Crop_farming)
- [Minecraft Wiki — Redstone troubleshooting and timing](https://minecraft.wiki/w/Tutorial:Redstone_tips)
- [Minecraft Wiki — Water flow mechanics](https://minecraft.wiki/w/Water)
- [Technical Minecraft Wiki — Piston lifecycle and movement](https://techmcdocs.github.io/pages/Blocks/Piston/)
- [Technical Minecraft advanced mechanics reference](https://www.techmc.wiki/en)

These are research references for maintainers, not sources BuildAI must access
during generation. Keep the requested building original; adapt principles.

## Fluid accounting and normalization (Creative vs Survival)

World-placeable `minecraft:water` and `minecraft:lava` are *fluid source
blocks*, not their filled-bucket inventory item IDs. BuildAI now installs
them as a dedicated **third normalization pass**, after the physical building
and glass/fence/dust connections and before final redstone signal updates.

- **Creative:** water and lava source blocks cost zero bucket items.
- **Survival water:** requires precisely **two filled water buckets per
  blueprint**, regardless of the number of water source cells. This models
  starting a renewable two-bucket water supply. On first placement, BuildAI
  swaps both filled buckets in a marked supply chest for empty buckets.
- **Survival lava:** requires **one lava bucket per lava source cell**.
  Each consumed full bucket becomes an empty bucket in its chest slot.
- If supplies are missing, BuildAI waits until the required buckets appear,
  then continues **the paused fluid-normalization pass**, not excavation.
- Manually re-running `/buildai normalize` on a completed build never
  consumes more buckets or refills fluids.
- Actual automatic dispenser gameplay requires separate bucket inventory
  and block-entity data, even when the static build's source water was funded.

Java 1.21.1 repeater/comparator blockstate `facing` identifies their
**rear/input**, not their output. Therefore a repeater receiving power from
the WEST and sending it EAST needs `minecraft:repeater[facing=west]`.
BuildAI repairs only demonstrably reversed direct source→repeater→device
patterns and otherwise warns rather than guessing at complex circuitry.

For roller coasters, most track should be normal `minecraft:rail`;
powered rails are intermittent boosters according to terrain, minecart
loading and desired ride speed. A separate redstone power block under
every rail is not necessary. The rail QA flags excessive power use unless
the player explicitly requests fully powered track.

## BuildAI post-construction redstone normalization

Once all blocks have been placed and temporary scaffolding removed,
BuildAI runs three bounded passes inside the existing NORMALIZE phase:

1. Recalculate vanilla glass-pane/fence/wall/iron-bar connections.
2. Run Minecraft Java 1.21.1's `getStateForNeighborUpdate` on **each dust
   position from all six neighboring directions**. Only connections on
   existing `minecraft:redstone_wire` blocks are changed. The vanilla game
   retains control of power levels and any needed follow-on updates.
3. Place contained source water/lava positions with their correct Creative
   or Survival bucket budget; allow Survival to wait for marked supply chests.
4. Only **after all wiring and fluids are placed**, call native redstone-neighbor
   notifications on sources/devices: dust, torch, repeater, comparator,
   lever/button/pressure plate, observer, powered rails, detector rails,
   lamps, pistons, droppers, hoppers, dispensers, and related vanilla blocks.

The build performs no intentional redstone command-block execution before
command approval: unapproved scripts have not yet been programmed.
After the redstone passes, command-bearing projects enter the separate
`WAITING_COMMAND_APPROVAL` phase as before. Repeating this normalization
on an already completed/commissioned project **returns to COMPLETE** and
never requests a new approval selection.

Manual `/buildai normalize` SKIPS the fluid placement pass entirely.
It also scans nearby old builds (20 blocks
horizontally and 14 vertically) and repairs eligible dust, then refreshes
vanilla signal neighbors. Note that manual normalizing existing **live**
mechanisms may cause pulses or temporarily retrigger machines. Use on a
test copy if that would be destructive.

### Explicit limits

Normalization **does not create a missing wire between separate pieces**,
fix misoriented repeaters/comparators/observers/pistons or adjust their
delays, remove faulty redstone loops, guarantee a circuit is logically
functional, or automatically configure command block scripts. Unsupported
and modded redstone devices are not assumed to behave like vanilla. A
complete timing/functional test still needs real Minecraft gameplay.
