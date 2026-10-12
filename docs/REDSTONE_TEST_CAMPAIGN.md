# BuildAI Redstone Engineering — Targeted Test Campaign

**Target:** Minecraft Java Edition 1.21.1 (Fabric).
**Purpose:** Improve BuildAI's ability to design **any** requested redstone
mechanism, one capability at a time, without overfitting to farms, adding
unrequested entities/mods, or confusing model claims with physical proof.

## Principles

1. **One branch per campaign, one isolated behavior per fixture.**
   Test the weakest primitive first before introducing multiple mechanisms.
   The baseline generation prompt states OUTCOMES, not detailed RBL diagrams.
   A separate reference build can establish Minecraft feasibility.
2. **Two different scorecards:** (A) compile/static audit and (B) real
   Minecraft 1.21.1 gameplay observations. Passing A does not pass B.
   A screenshot of a correct-looking design is not proof the circuit runs.
3. **Deterministic fixtures:** same request text, game version, input
   sequence, world rules, orientation and test conditions before and after
   a code/knowledge adjustment. Save exact AI JSON/.schem and RBL modules.
4. **Measure correctness before optimization.** Only simplify the design
   once all behavior assertions pass. Compare block count, material variety,
   footprint, response delay where instrumented, robustness and accessibility.
5. **Narrow fixes:** don't rewrite the entire architecture when one
   repeater or clock is wrong. Patch the relevant rule, adapter, reusable
   module, deterministic validator, or model guidance. Rerun the ORIGINAL
   baseline prompt to detect real improvements and avoid prompt overfitting.
6. **No false certifications.** A static geometry assertion proves only a
   static fact. Mark a module `in_game_verified` ONLY after observed tests
   in actual 1.21.1 Minecraft with recorded evidence.
7. **Bounded experiments:** at most three candidate revisions per fixture;
   stop on worse results, exhausted budget or user cancellation. Keep the
   best demonstrably functional baseline, not the latest AI output.
8. **Hold out novel prompts:** after a fix passes the fixture, rerun it
   rotated and reworded, so we verify generalization instead of a memorized
   schematic. Do not hand the target schematic to the model in the prompt.

## Prioritized branches

| Track | Isolated fixture | Advanced stress test | Definition of pass |
| --- | --- | --- | --- |
| R01 Orientation/power | Four independent cardinal lever→repeater→lamp circuits | 15-block wires, signal regeneration, crossings, rotated copies | Every input controls only its own output, all four directions, supports stable |
| R02 Boolean gates | Separate AND, OR, XOR circuits with two inputs | NOR/NAND/XNOR; cascaded gates | Every truth-table combination produces correct output |
| R03 Timing/pulses | Adjustable delay and manual reset | Pulse extender, monostable, clock start/stop, short-pulse conditioning | Measured tick behavior and reset match written contract |
| R04 Persistent memory | Copper-bulb toggle and explicit RS latch | Debounced input, set/reset priority, reload | Output persists/clears correctly and never double-toggles |
| R05 Pistons/motion | Two-block sliding piston barrier | Flush door, multiple piston phases, sticky vs ordinary behavior | Block movement succeeds, reversible sequence, no trapped player |
| R06 Observers/events | One watched block→single pulse→indicator | Rapid neighbor updates, chained observers and piston response | No missed events or false retriggers under defined conditions |
| R07 Item transport | Hopper→chest with manual input | Comparator fullness, sorting, crafter/dropper/dispensers | Physical inventories and output counts match declared recipe |
| R08 Fluids/farming | Contained water gate→hopper | Triggered flush, 8-block flow limits, stacked drains | Drops/items reach destination; water resets with no escape |
| R09 Rails/vehicles | Powered launch and safe stop | Station arrival detection and routing | Cart physically traverses and stops at expected locations |
| R10 Integrated mechanisms | 2–3 tested modules linked by explicit ports | Multi-floor machine, fault injection, partial resets | End-to-end behavior passes without losing independent module tests |

**Suggested sequence:** R01A→R01B→R02A→R03A→R04A→R05A→R06A→R07A→R08A→R09A→R10A. If any baseline fails, repair that track before adding complexity.

## Four-stage acceptance per fixture

**Stage A — Generate.** Use the exact pinned player prompt with no attached
solution schematic. Record generation route (staged/single), timing, number
of model correction calls, final saved blueprint and any audit warnings.

**Stage B — Static examination.** Count essential blocks; compare the
original request against actual placed XYZs; check direction/support,
input isolation, outputs, state/mode and optional inventory prerequisites.
This is the level automated Java/JUnit + RBL validation can exercise now.

**Stage C — In-game test.** Place in a disposable Java 1.21.1 test world.
Use ONLY actual available mechanics. Perform each defined input sequence
at least five times, inspect outputs and reset, and record exceptions.
Tick precision, fluid movement, entity/item paths and chunk behavior
require actual engine observation. Manual tests are acceptable interim
evidence; automatic Minecraft tick execution is **NOT implemented** yet.

**Stage D — Optimize.** Only after stages B+C pass, set a measurable target:
fewer blocks, smaller footprint, faster valid response, fewer unsupported
materials, less idle activity, cleaner wiring. Preserve every behavior.
Compare baseline and candidate, retest all inputs; keep the change only if
the score improves without functional regression.

### Evidence and scoring

| Metric | Required evidence | Status |
| --- | --- | --- |
| Blueprint valid | Successful Minecraft block-state validation | static |
| Prompt satisfied | Individual requirements with XYZ and observed outcomes | static + gameplay |
| Correct controls/outputs | Inputs used, physical output observations and false-trigger checks | gameplay |
| Repeat/reset | Five correct cycles minimum, plus at least one conflicting input test where applicable | gameplay |
| Tick-specific timing | Measured game ticks/TPS under actual test conditions | gameplay, pending instrumented harness |
| Inventory/fluid transport | Before/after actual item counts and leak inspection | gameplay |
| Resource cost | Canonical material count, bounding dimensions, footprint | static |
| Optimization accepted | Same tests pass, measured cost improvement, no new assumptions | static + gameplay |

Outcome labels: `PASS_GAMEPLAY`, `PASS_STATIC_ONLY`, `FAIL`,
`BLOCKED_BY_TEST_SETUP`, `NOT_RUN`. A claimed AI success is NEVER a
status source.

## Feedback loop into BuildAI

```text
Pinned baseline prompt
   ↓
Generate and save actual JSON/.schem
   ↓
Static QA + manual Minecraft gameplay tests
   ↓
Record exact symptom / world position / expected vs observed
   ↓
Isolate the failed redstone branch
   ↓
Targeted rule, RBL template, validator or module change
   ↓
Run JUnit + original baseline + rotated/rewritten holdout
   ↓
In-game retest (same environment, five cycles)
   ↓
Keep the best passing build; optional optimization retest
```

**Do NOT attempt an unlimited model correction loop.** Existing
`BuildQualityReview` has bounded static corrections, not in-game simulation.
A separate disposable-world tick harness remains a future feature described
in `docs/GENERAL_ACCEPTANCE_AND_SIMULATION.md`.

## Campaign files

- [R01A directional routing](redstone-tests/R01A_DIRECTIONAL_ROUTING.md)
  — first prompt, expected outcomes and hands-on test protocol.
- [Reusable result sheet](redstone-tests/RESULT_SHEET.md)
  — measured pass/fail recording before updating the RBL library.
- [RBL notation and verified status contract](REDSTONE_BLUEPRINT_LANGUAGE.md)
  — exact glyphs, ports and canonical voxel states.
- [Java 1.21.1 reference](REDSTONE_REFERENCE.md) — supporting mechanics.

### Baseline already known

The user's 8-plant sugar-cane observer/piston farm generated a much cleaner
enclosure and visible harvesting hardware than an earlier version. Its
screenshots do NOT independently prove 100% drop capture or five-cycle
reliability, and the user's comment notes a similar machine worked before
the latest redstone library. Record this as **qualitative design progress**,
not evidence that R08 is certified.
