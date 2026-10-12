# BuildAI Redstone Engineering Reference (Java 1.21.1)

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

## BuildAI post-construction redstone normalization

Once all blocks have been placed and temporary scaffolding removed,
BuildAI runs three bounded passes inside the existing NORMALIZE phase:

1. Recalculate vanilla glass-pane/fence/wall/iron-bar connections.
2. Run Minecraft Java 1.21.1's `getStateForNeighborUpdate` on **each dust
   position from all six neighboring directions**. Only connections on
   existing `minecraft:redstone_wire` blocks are changed. The vanilla game
   retains control of power levels and any needed follow-on updates.
3. Only **after all wiring has been repaired**, call native redstone-neighbor
   notifications on sources/devices: dust, torch, repeater, comparator,
   lever/button/pressure plate, observer, powered rails, detector rails,
   lamps, pistons, droppers, hoppers, dispensers, and related vanilla blocks.

The build performs no intentional redstone command-block execution before
command approval: unapproved scripts have not yet been programmed.
After the redstone passes, command-bearing projects enter the separate
`WAITING_COMMAND_APPROVAL` phase as before. Repeating this normalization
on an already completed/commissioned project **returns to COMPLETE** and
never requests a new approval selection.

Manual `/buildai normalize` also scans nearby old builds (20 blocks
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
