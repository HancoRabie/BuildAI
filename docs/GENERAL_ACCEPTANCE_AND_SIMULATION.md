# BuildAI: requirement-first acceptance and functional testing

BuildAI is an architecture **and** engineering authoring tool. A farm, castle,
automated sorting machine, puzzle room, calculator, or redstone game is just
a request, not a distinct product mode.

## User-request contract

1. Preserve the original player request (scope, features, scale, exclusions).
2. Produce the complete block-state blueprint with reversible source JSON.
3. Build a concise requirement ledger: requirement, real XYZ evidence,
   verification type, unresolved assumptions, and in-game acceptance test.
   Never equate a model-written "implemented" with actual satisfaction.
4. Perform deterministic static checks: geometry, structural support, dimensions,
   inventory block facing, directed item routes, redstone topology/controls,
   fluids, doors, command security and exposed world-state dependencies.
   Add new domain rules as independent modules, not as a new whole-product mode.
5. Report fatal / unresolved / optimization-only findings distinctly.
6. When there are independently measurable failures, make bounded targeted
   corrections; preserve the player's intact original and avoid repeated
   external research. Do not accept a model's revision because it asserts
   success. Do not invent optional features, mobs, worker mods or entities.
7. Re-run the checker after each accepted revision. Stop on pass, non-improvement,
   revision budget or explicit user cancel, and preserve warnings.

## Status of implemented source

- Generic prompt/voxel audit: initial static implementation.
- Specialist modules: initial architecture, fluid, farm-mechanism checks;
  redstone/command checks also occur in the main compile/QA pipeline.
- Model repair: at most two guarded corrections for single-response
  blueprints <=100K specification characters. Fully assembled staged
  blueprints are audited but not automatically rewritten as a monolith:
  doing so would risk losing cross-stage interface contracts.
- Improvement suggestions: surfaced in QA, never automatically treated as
  true or as authority to override the prompt.
- Rechecking: done on each proposed corrective replacement.
- **Real Minecraft tick simulation: NOT IMPLEMENTED.**

The initial checker is intentionally partial. It can miss requirements such
as "four usable towers" or "the exact puzzle solution" even when every block
ID is valid. This is not production-grade semantic requirement coverage.

## Simulation design (future engineering work, not a completed capability)

Never implement "simulation" as an LLM describing what it imagines will
happen. Build a sandbox executor using the *actual Minecraft 1.21.1* server
block/update behavior:

- Run on a disposable dedicated test level, generated flat test dimension,
  or isolated throwaway world copy. Never run destructive experiments in a
  player's real save. Preserve the existing command-block approval boundary.
- Place the approved blueprint with all intended fluid sources, block states,
  tile-entity configurations and optional explicitly requested inventories.
- Derive test procedures from the player request: toggle an input, run ticks,
  observe piston/door/crafter/redstone responses, verify physical connections,
  detect item transfer into the intended destination, and test reset/repeat.
  Distinguish static architectural pass from mechanisms requiring world ticks.
- Treat missing villagers, inventory supplies, external APIs, plugin worlds
  and mod-provided behaviors as unavailable unless explicitly configured.
  Don't fabricate a worker to bypass a vanilla limitation.
- Log exact inputs, test conditions, observable states, failure positions and
  tick counts; only then is "tested in Minecraft" a supported claim.
- Repair targeted failing modules without dropping valid geometry.
  Re-run static audit AND relevant actual sandbox tests on the candidate.
- Use explicit work budgets: maximum runs/revisions, material/voxel size,
  wall-clock limits, and user cancellation. "Until satisfactory" means until
  the measurable acceptance criteria pass **or** the bounded budget ends,
  never an infinite paid-model loop.
- Present optimization-only suggestions for player review. Cosmetic changes
  or risky, unverifiable redstone optimizations should not automatically
  overwrite a functioning design.

Until this test harness exists, the UI must say **static audit only** and
clearly list tests to perform in a real Minecraft world.
