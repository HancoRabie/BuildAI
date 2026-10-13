# BuildAI Redstone Mastery Benchy — Java Fabric 1.21.1

## Purpose and rules
This is a **PLAYER-PROMPTED IN-GAME TEST**, NOT a mod test runner or a Java
redstone simulator. Use the exact same prompt before and after engineering
changes, save an original screenshot/schematic of every attempt, and manually
evaluate results against stated behavior in Minecraft Java 1.21.1.

Eight engineering pillars, **three separate stations each** (foundation,
applied, mastery). Each pillar is generated in ONE small, self-contained
blueprint, not one 24-station response. Label all stations with ACTUAL sign
block-entity text using the usual BuildAI mechanism. No command blocks,
reliance on unconfigured inventories, modded behavior, villager automation
or decoration that obscures circuits. Repeat at least 3 cycles per station.

**Score each station /5:**
1. Delivered requested parts, accessible labels/controls and expected shape.
2. Correct directional blockstates, support and actual power/physical path.
3. All explicitly named input combinations produce correct outputs.
4. Reset/retrigger/reload work independently.
5. Efficient, clean routing without shorts, wasted mechanisms or vast pads.

**Each pillar /15, complete suite /120.** Record generation reliability
separately (request accepted, first text arrived, complete SSE, compiled,
placed) and wall time; a model stall is NOT an observed redstone failure.
An unverified static blueprint or a self-claimed QA pass is not a game pass.

All prompts start with this common header (include verbatim):

> Minecraft Java 1.21.1: build EXACTLY THREE SEPARATE redstone stations in
> one compact blueprint, stations marked 1–3 and physically/electrically
> isolated, with four-line actual sign text, independently reachable labeled
> inputs, visible wiring, observable lamps/items/actuators, and correct
> reset. Do not merge stations or use mods/commands. No giant testing floor.
> Place only the necessary supported blocks, labels and walkways. Deliver
> physical circuits, not diagrams. Flag any uncertain behavior rather than
> claiming Minecraft tests were run. Inspect input-output direction,
> supporting power blocks, unintended power coupling and truth-table OFF
> cases before finishing.

The station requirements below can be pasted under the header unchanged.
Keep prompts one pillar at a time to fit BuildAI's <=4000 character field.

### P1 — SIGNAL STRENGTH & DIRECTIONAL TRANSMISSION

1. **Long line.** One lever powers one lamp via >=25 consecutive dust
   positions; restore strength with directional repeater(s). 5 on/off cycles
   and no power failure at the far end.
2. **Cardinal isolation.** Four own lever→repeater→lamp channels N/E/S/W,
   delays 1/2/3/4 redstone ticks. Any one channel may be toggled without
   affecting others; all ON simultaneously, then one OFF.
3. **Independent crossover.** Two perpendicular signal paths A and B cross
   in the same compact footprint and each span >=15 blocks. Test 00→00,
   10→10, 01→01, 11→11, reset to 00 with NO cross-power.

### P2 — COMBINATIONAL BOOLEAN LOGIC

1. **3-input AND.** Levers A,B,C: output lamp ON only for 111, OFF in
   every other of eight cases. The output cannot be held on by an
   uncontrolled torch; trace power of each torch's support block.
2. **XOR.** A,B levers: 00=OFF,01=ON,10=ON,11=OFF. Repeat 11 after each
   single-input case. Do NOT connect wires into an OR fork and call it XOR.
3. **Two-output selector.** Separate trigger button T and selector S.
   S=0: T pulses lamp A ONLY; S=1: T pulses lamp B ONLY. Both OFF at idle,
   release/reset, switch selection while T released, five cycles. An
   unconditional Y-split to both lamps is wrong.

### P3 — EDGES, PULSE LENGTH & CONDITIONING

1. **Edge detector.** Lever powers a pulse output ONLY on OFF→ON,
   not while held, and no pulse on ON→OFF (label a lamp or note block).
   Reset when lever goes off, 3 cycles.
2. **Duration extender.** Stone button produces lamp ON for >=3 seconds
   measured in ordinary Java ticks, then automatically OFF. Five cycles;
   never stuck ON; don't mistake 1–4 repeater ticks for 3 seconds.
3. **One-shot/debouncer.** A lever ON produces exactly ONE short output
   pulse, not a clock; holding ON produces no repeats, release re-arms.
   Deliberately test short accidental input toggles and reset.

### P4 — CLOCKS & DELIBERATE SCHEDULING

1. **Gated clock.** Switch starts a flashing output lamp and stops it.
   >=5 visible flashes while enabled, stable OFF after stop, 3 cycles.
2. **Frequency divider.** Independent input button pulses: first press
   remains OFF, second toggles division output ON, third stays ON, fourth
   returns OFF. Implement 2:1 pulse count, not pulse extension.
3. **3-stage timed outputs.** One trigger starts lamps A→B→C in visibly
   distinct order; they return to OFF in reverse C→B→A automatically.
   Repeated trigger after reset produces identical order, no overlap faults.

### P5 — PERSISTENT LOGIC & MEMORY

1. **RS memory.** SET and RESET buttons; output remains ON after SET
   release, OFF after RESET release; define deterministic RESET priority.
2. **T toggle.** One distinct button press toggles persistent state once.
   Four presses return OFF; no double-toggle on held button/release.
   Copper bulb is permitted.
3. **2-bit up counter.** Single button input increments lamp bits
   00→01→10→11→00. Separate RESET restores 00 from any state.
   Four steps and reset tested twice; store real state, not timed animation.

### P6 — ANALOG SIGNALS & COMPARATORS

1. **Chest fullness.** Ordinary chest with comparator output to lamps:
   empty = no power, nonempty = detectable power, removing contents
   resets to OFF. Opening an empty NORMAL chest must not power a lamp.
2. **Subtraction.** Two analog input strengths from adjustable signal
   sources, comparator in SUBTRACT mode. Confirm output corresponds to
   max(rear−side,0), including side stronger than rear.
3. **Three thresholds.** Fill meter from a player-supplied inventory:
   0 bulbs lit when empty; 1 at low, 2 at medium, 3 at high; higher
   fill may not extinguish earlier thresholds. Specify manual setup.

### P7 — MOVEMENT & BLOCK-UPDATE DEPENDENCIES

1. **Piston physics.** Two independent controls: normal piston pushes
   movable block but does not retrieve; sticky piston pushes AND pulls.
   Five complete cycles without disturbing other station.
2. **Observer update.** Trapdoor toggled by player triggers short lamp
   pulse through correctly oriented observer. Both open and close
   cause pulse; pulse then stops, no permanent clock.
3. **2×2 piston door.** Controllable opening, player-walkable passage,
   returns to completely sealed 2×2 surface, flush as feasible.
   Three open/close cycles, no stuck heads/ghost blocks.

### P8 — ITEM LOGISTICS & AUTO-CRAFTING

1. **Hopper lock.** Chest→hopper→chest; load 5 items while hopper is
   powered/locked: 0 transfer. Remove lock: exactly all 5 transfer.
2. **Two-item sorting.** Player loads five of each item, e.g. stone
   and dirt, into one input. Two labeled chests receive exactly their
   matching items, no cross-contamination; document filter-item setup.
3. **Crafter with controlled pulse.** Manually load a valid recipe
   (3 oak planks in one row to produce 6 oak slabs); exactly one
   button trigger produces exactly one craft, output collected by hopper
   and chest, no double craft on a held pulse, reset for second batch.

## Benchmark discipline
- **Version baseline:** name prompt ID, BuildAI commit, model/account mode,
  generation elapsed, number of stations present, schematic, screenshots.
- Never report untested mechanisms as passes. Mark PASS / FAIL / PARTIAL /
  MISSING / NOT TESTED, with input sequence and observed output.
- When a test fails, diagnose circuit path/torch support/logic formula
  from real world observations first. Improve reusable redstone engineering
  instructions/static-risk detection, not a benchmark-specific hardcoded build.
- Re-run the identical failed pillar prompt after each meaningful change.
- Keep earlier circuits, source, screenshots for apples-to-apples comparison.
- If generation stalls: log as MODEL DELIVERY failure independently of
  redstone functionality, keep local raw draft and check diagnosis.
