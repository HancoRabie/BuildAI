# BuildAI — AI design output contract (Minecraft Java 1.21.1)

The AI's job is to **design and deliver valid buildable Minecraft
geometry**, not to claim it has run a game-world simulation. Real
redstone/fluids tests are performed by the player in Minecraft, and
subsequent feedback is used to refine future designs.

## Output and construction

Every normal response is valid JSON with `name`, `width`, `height`,
`length`, and `operations` (1–16,000 compact fill, box, walls, shell,
line, ellipsoid, or organic-canopy operations). Later operations can
overwrite previous ones, including `minecraft:air` carving. The
compiler resolves valid 1.21.1 block states and builds a 3D voxel
blueprint (max 512 cells per axis, 4 million bounding cells total).
Command blocks additionally need a separately approved command plan;
inventory-bearing entities or preloaded container NBT aren't guaranteed
by block-only geometry.

## Design versus in-world validation

**Lack of an in-world simulator, prefilled hopper inventory, or tested
redstone timing is not a reason to decline generating a proposed machine.**
Generate the best achievable complete requested layout at the user's
required dimensions, use the bundled redstone handbook, and include
specific optional `limitations` strings describing any genuinely
unsupported inventory/entity setup or in-world tests.

For a one-button crop farm, physically include crop planting,
appropriate farmland, water-flow gates and channels, trigger/timer
wiring, physical collection paths, chests and requested labels as far
as supported by its data format. Where pre-filled inventory isn't
available, prefer real alternate circuits or independent crop
collection lanes instead of inventing a stocked item filter. Do not
claim automatic replanting of potatoes/carrots by pure vanilla redstone,
or falsely state that a generated machine has already been tested.

The server validates the build; optional QA inspects geometry and
engineering plan metadata but does **not** prove machine operation.
Explicitly surfaced warnings belong in the design QA screen and
never replace valid blueprint geometry.


## General blueprint acceptance and optimization review

BuildAI is a general-purpose architectural and engineering mod. After
generation it compares the blueprint with the user's original instructions.
The generic audit checks concrete requested controls/components, directional
hopper output paths and grounded XYZ evidence attached to the generated
requirement manifest. Separate specialist modules contribute targeted
architecture, redstone, fluid and other relevant mechanism warnings without
taking over the building prompt.

The generated "requirements" / "optimization_suggestions" fields are model
claims, not proof. Actual block-state coordinates are validated. Static
geometry QA can find omissions, misfaced hoppers and obvious constraints,
but cannot establish complete semantic correctness for every conceivable
request or simulate an entire Minecraft tick engine.

For measurable blockers, BuildAI may request **up to two bounded corrective
revisions** using the exact original prompt and prior generation context.
A candidate must reduce measured blockers while preserving build dimensions,
substantial existing geometry and certain functional blocks; otherwise the
last accepted blueprint is kept with warnings. Repairs are limited to designs
whose specification fits the 100K-character single-revision guard, and
there is no endless retry loop or fabricated 100% success claim. Quality
suggestions without objective blockers are reported, not automatically
applied.

An actual game-world simulation remains a separate future capability.
In-world tests are still required for redstone updates, moving pistons,
inventory-dependent dispensers, water/item trajectories, accessibility
and other dynamic behaviors. A successful static audit is **not** a
certificate that a build works when activated.

## Recovery from unwarranted refusals

If a model returns only an `{"error":"..."}` claiming it cannot
guarantee validation, preload inventories or simulate real Minecraft,
BuildAI retries **once** on the same model with the same user prompt,
scale, and previous design (when actually revising). It reuses the
already researched inspiration; it does not search again. The second
request reinforces that it should return actual geometry and specific
setup/test caveats. If that attempt also cannot produce valid geometry,
generation still fails cleanly and preserves the user's existing
blueprint and saved prompt.

If a response contains **both usable operations and model caveats**,
BuildAI compiles the operations and shows the caveats as QA warnings.
Hard engine-size, block count and operation limits are real constraints;
BuildAI does **not** silently shrink the building or claim a model
recovery overcame the technical limits.

## Model connections

Generation supports connected ChatGPT through the supported OAuth
route, or a separately billed optional developer API key. Credentials
remain client-side and are never sent to the Minecraft server.
