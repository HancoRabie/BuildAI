# R01A — Cardinal repeater output, support and input isolation

**Status:** `NOT_RUN` (no manual or automatic Minecraft gameplay trial recorded).  
**Minecraft edition:** Java 1.21.1 vanilla mechanics; Fabric BuildAI installed.  
**Branch under test:** R01 / direction, support, repeaters, output routing.  
**Complexity budget:** Small, one-floor fixture; no clock, hopper, pistons or command blocks.

## Pinned user prompt — paste EXACTLY into BuildAI

> Build a small, neat Minecraft Java 1.21.1 redstone testing station with
> four COMPLETELY INDEPENDENT lever-controlled signal paths. Position one
> path facing north, one east, one south and one west. Each path must
> contain its own lever input, correctly oriented redstone repeater and
> redstone lamp output at the far end. Use repeater delays of 1, 2, 3 and
> 4 redstone ticks for north, east, south and west, respectively. Provide
> adequate solid supports and space between circuits. Keep levers
> accessible and the lamps clearly visible. The levers should start OFF.
> A lever must switch ON only its matching lamp, then switch that lamp
> OFF when reset. All four should also work at the same time without
> cross-triggering. Make the build compact and attractive but prioritize
> functional signal routing. Do not use command blocks, observers,
> pistons, dispensers, clocks, wireless redstone or modded components.
> After generating the blueprint, review all four real input/output
> paths, redstone orientation, repeater delay, support and isolation;
> report unresolved uncertainties, without claiming to have simulated it.

**Why this wording?** It specifies behavior and four output headings
without spelling out the corresponding `facing` values or providing the
answer schematic. That tests BuildAI's ability to DERIVE placements.

## What the build must contain

- 4 unique levers, 4 unique repeaters (one of each delay 1/2/3/4),
  4 lamps, supporting solid blocks and any genuinely required conductors.
- Lamps physically lie NORTH/EAST/SOUTH/WEST of their respective input
  relative to each path's own local start, not all pointing east.
- Independent conductors: no accidental parallel cross-power, no short
  circuits, no unsupported dust, no extraneous timed mechanisms.
- Correct Java 1.21.1 directional state on every repeater according to
  intended output, **verified from actual voxel geometry**, not labels.
- Observed lever ON/OFF reliably controls its own lamp in Minecraft.
- Optional railings/lighting must not power, obstruct or touch wiring.

## Static preflight (before testing in-game)

1. Confirm the blueprint compiles under Java 1.21.1 and contains a
   real solid support below each floor-mounted repeater/dust.
2. Inspect each repeater's position `(x,y,z)`, its rear input
   and forward lamp/dust path; `facing` refers to the rear INPUT.
3. Verify N output uses `facing=south`, E→west, S→north, W→east.
   These are **test expectations**, NOT supplied in the model prompt.
4. Verify actual `delay=1,2,3,4` for N/E/S/W, not a button label.
5. Confirm the source and destination aren't merely decorative
   unconnected blocks. A cable drawn in `redstone_plan` is not a wire.

## Gameplay acceptance sequences

Run in a disposable Java 1.21.1 world with default tick rate. Record
the exact version/mod list, BuildAI commit and test origin. Complete
all trials before modifying the finished build.

| Sequence | Input actions | Required physical outcome |
| --- | --- | --- |
| Idle | All four levers OFF | All four lamps OFF |
| North | North ON/OFF, other levers OFF, repeat 5× | Only north lamp toggles |
| East | East ON/OFF, others OFF, repeat 5× | Only east lamp toggles |
| South | South ON/OFF, others OFF, repeat 5× | Only south lamp toggles |
| West | West ON/OFF, others OFF, repeat 5× | Only west lamp toggles |
| Parallel | Set all four ON; then all four OFF, repeat 5× | All four ON and then OFF; no missed input |
| Isolation | Keep N and W ON; toggle E and S independently 5× | N and W retain states; no cross-activation |
| Recovery | Reload the disposable test world with all controls OFF | Correct OFF state restored after normal updates |

**Note:** Lever transitions through repeaters happen on Minecraft game
ticks; human-visible observation is insufficient to certify exact 2–8
game-tick timing. Timing is checked at STATIC delay properties here.
Precise latency measurements belong in track R03 when we have a
real instrumented tick test runner.

## Scoring

**Functional gate:** every input/output/parallel/isolation observation
passes on all repetitions. Any failure = `FAIL`; static-only pass =
`PASS_STATIC_ONLY`. Record world-evidence separately.

**Optimization only AFTER functional pass:** compare total redstone
components, full occupied footprint, solid supports, material types and
accessibility. A smaller build fails optimization if any lamp misses
a transition or any hidden cross-power appears.

**Suggested guardrail:** Avoid adding complex clocks, observers or
memory for a test that requires four straightforward independent switches.

## What to bring back here

- Full screenshot of front/top/back (all wiring visible), including
  lever labels or clearly identifying locations.
- Brief note identifying any incorrect lamp or repeater facing.
- PASS/FAIL for each of the 8 sequences above.
- Optional BuildAI generation log, final blueprint JSON/schematic,
  warnings and timings.

We then adjust **R01 only**, rerun this pinned prompt unchanged,
and run a fresh, rotated/rewritten holdout before promoting an RBL
module to `in_game_verified`. The static inspector alone cannot do that.
