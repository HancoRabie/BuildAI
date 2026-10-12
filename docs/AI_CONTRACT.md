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


## Post-generation farm function audit and guarded repair

For automated crop farm designs, BuildAI runs a static geometry audit after
the model completes its proposed blueprint and **before** any world placement.
It checks requested crop blocks, farmland below root crops, nearby water
release and piston evidence, harvest controls, hopper/storage presence, and
whether dispensers would require unpopulated inventories. Ordinary irrigation
water at soil level does not satisfy the plant-level flush requirement.

If the audit finds measurable blockers, BuildAI can attempt **one** model
revision with the original player/stage prompt unchanged and targeted
engineering findings supplied separately. To prevent repeated expensive
requests, only a single revision is attempted, only for specifications up to
100,000 characters, and with the previously obtained inspiration reused.
A revised result is accepted only when it reduces known blockers, preserves
dimensions and all previously planted crop counts, and retains at least 70%
of the original solid-block count. Independent staged modules are also
revalidated against previously completed interfaces before acceptance.
Otherwise the original blueprint remains intact with actionable warnings.
This is NOT proof of Minecraft redstone or fluid mechanics.

A static schematic cannot show fluid flow after a piston retracts or prove
item trajectories, so the audit cannot certify that every item actually
reaches a hopper. Test in a disposable world; a real post-placement
redstone/fluid simulation is still a future release feature.

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
