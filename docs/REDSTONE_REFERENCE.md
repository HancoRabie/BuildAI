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
