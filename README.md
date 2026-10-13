# BuildAI

**Speak buildings into existence in Minecraft.** Fabric 1.21.1, Java 21.

BuildAI is a freestanding Minecraft building mod: describe a structure to an AI, get an exact voxel blueprint, inspect/position it, and have the game construct it. It also imports community-created schematics and exports AI builds as .schem.

**Status: v0.1 developer prototype. Test on a backup or disposable world only.** Features not yet complete are listed in [docs/ROADMAP.md](docs/ROADMAP.md).

## Open source

BuildAI is licensed under **GNU GPL-3.0-only**. You can inspect, fork and contribute to this project under the terms in [LICENSE](LICENSE). See [CONTRIBUTING.md](CONTRIBUTING.md) and [SECURITY.md](SECURITY.md).

The source repository is public, but **Continue with ChatGPT has not been implemented yet**. Minecraft Chat Mode now routes generation through an authorized ChatGPT account when connected, or through an optional OpenAI API key otherwise. The planned user-consented, browser-based ChatGPT sign-in and eligible ChatGPT-plan usage are documented in [docs/OPEN_SOURCE_AND_CHATGPT.md](docs/OPEN_SOURCE_AND_CHATGPT.md). Public visibility alone does not automatically connect ChatGPT accounts.

## Requirements

Minecraft Java 1.21.1, Fabric Loader 0.16.7+, Fabric API, JDK 21 for development. No WorldEdit, Litematica, ChainFrames or PinLock dependency.

For text-to-build, v0.1 optionally connects to OpenAI API using an API key and an API-billed model. This is **not Sign in with ChatGPT** and does not consume ChatGPT Plus/Pro plan messages. No key is persisted by the mod or sent to Minecraft servers.

## Set up in IntelliJ (Windows)

Your project was previously cloned to the MC-MODS folder. In IntelliJ open that BuildAI directory and run:

    git pull origin main
    .\gradlew.bat build

The Gradle bootstrap downloads Gradle 8.10.2 on first run. Install Java 21 if Gradle reports a wrong runtime. Find the remapped mod JAR under build/libs, excluding sources and dev JARs. Use that JAR in a Fabric 1.21.1 test instance together with the matching Fabric API.

A GitHub Actions build also runs automatically on each latest main commit.

## In-game guide and conversational design

Press **F8 → How To** for the built-in guide (nine topics). No internet is required to read it.

BuildAI now supports an initial AI revision session. **Generate New** compiles a complete new blueprint; **Revise Existing** takes a follow-up instruction and sends the previous AI-authored design to the model, requesting a complete revised blueprint. The revised result remains local for review until you choose **Upload Blueprint**. Imported schematics do not have an AI edit history yet.

In private **Chat Mode** enabled from F8, ordinary chat messages create a new design when there is no earlier AI design, or revise the active AI design when present. Type `new <description>` to force a fresh building, `reset` to clear the AI design, and `exit` to disable chat interception. This is still an experimental, one-previous-design workflow, not a persistent ChatGPT conversation.

**Continue with ChatGPT** now has a client-only browser OAuth implementation. It's not yet proven against a real ChatGPT login or response; validate in a test world. An optional separately billed developer API key remains the fallback. See [docs/OPEN_SOURCE_AND_CHATGPT.md](docs/OPEN_SOURCE_AND_CHATGPT.md).

## General placement appropriateness review

Before accepting an AI blueprint, BuildAI now inspects the **actual final
voxel positions**, not just the text of the AI's claimed test results.
The `PlacementAppropriatenessAudit` checks whether redstone lamps have
a geometrically plausible **adjacent** power source (or a powered conducting
block), taking Java repeater/comparator/observer direction into account;
it identifies a visible **one-block air gap** between a lamp and intended
input wiring. For functional redstone requests, disconnected lamps create a
concrete acceptance-review defect; the existing bounded model revision
path can propose a repaired COMPLETE blueprint. The checker never
silently adds dust because connecting the wrong branches can short logic
gates or independent circuits.

The same generic check also warns about dust/rails/repeaters/torches
without supporting blocks, missing wall-mounted supports, repeaters with
empty rear/input cells, actuators without a plausible direct signal, and
pistons facing immovable blocks. It checks actual block-state orientation;
model-written `redstone_plan` text alone does not satisfy the check.

**This is not a Minecraft power or tick simulator.** Dust shape,
quasi-connectivity, weak/strong block power and complex control loops still
require testing in Minecraft. Decorative unpowered lamps are advisory, and
an output intentionally wired to existing external world circuitry can be
marked externally supplied in the prompt. Original screenshots and
benchmark schematics should be retained for reproducible comparison.

## Resilient streaming and generation monitor

The ChatGPT-connected stream is **not** treated as complete unless
`response.completed` arrives. A completed response is compiled and checked
normally. BuildAI also journals each incoming blueprint text delta into a
local recovery file at `<Minecraft game directory>/buildai/recovery/`,
flushing incrementally so an unexpected connection exit doesn't erase all
model output. This file contains only raw streamed design text, not OAuth
access tokens or credentials. Do **not** upload/share a recovery file without
checking its content.

If the stream ends **without** `response.completed`, BuildAI attempts to
compile the collected data into a normal blueprint. **Only** a parseable,
valid Minecraft blueprint can appear in BuildAI review, and it is marked
`UNVERIFIED INTERRUPTED STREAM`: it may be missing requested structures,
even when JSON and blockstate validation pass. No blocks are placed
automatically. Generic AI auto-repairs are intentionally disabled for such
drafts to prevent losing the recovered text in another long request.

If the raw output is truncated, malformed, or invalid, the existing
blueprint remains untouched. The `.json.part` recovery file is retained
locally and the error reports its location. An incomplete `.json.part` file
is **not** a valid `.schem` and must never be pasted/placed directly.
On a verified `response.completed` plus successful compilation and QA,
the temporary journal is discarded. If the stream finished without the
completion event, its output is retained even when it compiled successfully,
so you can inspect the original text. Explicit cancellation and watchdog
expiry still stop requests rather than accepting them as completed.

The generation screen uses a slimmer (12px) indeterminate progress bar,
with a concise history-based **ETA inside the bar**. ETA is an estimate,
not model-certified percent complete; it can become uncertain during
large designs. The former oversized under-statistics ETA rectangle is gone.

## Native file picking and architectural reference folder

BuildAI uses the **same LWJGL TinyFileDialogs picker as ChainFrames** for
local PNG/JPEG pixel-art images, external schematic feedback, and whole-build
style references, avoiding Java AWT's headless desktop limitation.
Go to **F8 → Feedback & Styles → Style References → Analyze a Whole Build**.
Use **Browse File(s)** to select an external `.schem`, `.schematic`,
`.litematic` or `.nbt`; multiple schematics can be selected and
analyzed together (up to 12 per batch, originals left unchanged).

The existing **Scan Folder** workflow is unchanged: it processes up to
12 supported files from `<game directory>/buildai/style-references/`.
Use its new adjacent **Open Folder** button to open exactly that folder
in Windows Explorer, macOS Finder or the Linux file manager and drop
downloaded schematics there. Return to Minecraft and press **Scan Folder**.
This does not upload the whole folder to an external site or execute
schematic commands.

After any failed generation, the monitor shows a short observed-cause
diagnosis. **Copy Failure Report** copies the actual HTTP acceptance status,
last SSE event, event age, received character count, auto-stop state,
full exception chain and any local recovery-file path. OpenAI's reported
`response.failed`/`response.incomplete` codes are extracted from SSE
events when present. An unexpected stream EOF is reported as an observation:
BuildAI cannot prove whether it was caused by OpenAI, a network gateway
or a local connection drop.

## Image upload → offline pixel art

Use **F8 → Create / AI → Upload Image > Pixel Art** to turn a local PNG,
JPG or JPEG into a vertical Minecraft block-art blueprint, without a
model request or API credits. Choose a maximum width from 16–128 blocks,
**Concrete** or **Wool**, smooth/photo or sharp/pixel resizing, and whether
transparent PNG pixels become air. The source image stays on your computer.

Click **Generate Preview**, review the approximate 16-color Minecraft
mosaic, then **Use Blueprint**. It enters the ordinary BuildAI
Placement / Export review flow and is autosaved as a new `.schem` file.
Choose **Browse Image** for a native desktop file picker; if the desktop
does not provide one, paste the local image file path in the input.
Both dimensions are capped at 128 blocks, preserving aspect ratio.
Pixel-art images are one block thick and can be very material intensive.
Read [the full guide](docs/IMAGE_TO_PIXEL_ART.md) before placing large art.

## Saving and loading builds (works in headless Minecraft)

**No Java Swing file dialog is used.** Minecraft's Fabric development JVM may be headless and cannot open `JFileChooser`, even on Windows. BuildAI now saves a unique `.schem` file **automatically whenever AI generation finishes successfully**. You can also press F8 → Placement / Export → **Save Blueprint to Library** to explicitly save an imported or generated structure.

Saved files live in the active Minecraft game directory under `buildai/blueprints/`. For IntelliJ's default Loom `runClient`, that is normally `<BuildAI project>/run/buildai/blueprints/`, not necessarily the regular launcher profile's `.minecraft` directory. F8 → **Import File / My Builds** opens a Minecraft-native list of these saved files, or allows you to paste the full path of an existing schematic file. The `.schem` is a permanent structure file, not merely a preview state; AI revision history is still in memory only.

**Important for existing sessions:** a BuildAI client launched *before* this feature was pulled does not have auto-save. Restarting it will lose an unsaved design. Use Upload & Preview → placement/confirm if you only need the structure in that disposable world; this is not equivalent to exporting a reimportable blueprint.

### Remake or revise older blueprints — always as a NEW file

Open **F8 > Create / AI > My Blueprints / Import**, select a saved
`.schem`, and choose **Remake / Revise**. BuildAI offers two distinct actions:

- **REMAKE SAME PROMPT** reruns the precise **original user prompt** from the
  source file, without reusing its prior geometry, so the AI can produce a
  completely fresh interpretation. You may edit the text before generating.
- **REVISE SAVED BUILD** takes the chosen schematic as its design reference.
  Type the requested changes and generate a complete, revised COPY, without
  modifying the original. When the older file has no saved AI operation JSON,
  BuildAI reconstructs a bounded, geometrically faithful source specification
  from its actual block states and command mapping. An exceptionally detailed
  legacy design may exceed the single-request AI context limit; in that case
  BuildAI explains the limit and does not erase any work.

Newer AI-generated `.schem` files embed the original user prompt (UTF-8)
and inert design JSON in custom BuildAI metadata. **Files saved by older
BuildAI versions did not preserve the original prompt**, so its exact words
cannot be recovered by inspecting the schematic; the Remake field is editable
so you can supply it yourself. External files also need their own prompt.
Neither Regenerate nor Revise modifies or overwrites the source .schem;
success uses the existing unique-timestamp auto-save. A failed generation
leaves the active blueprint and source file unchanged. This is a local
file-based workflow and does not modify any blocks in the existing Minecraft
world until a separately confirmed placement.

The command commissioning screen now has a clear **DONE > RETURN TO MINECRAFT**
button after successful authorization; before approval, **RETURN TO GAME
(REVIEW LATER)** leaves the server's approval pause intact. Neither action
requires Escape or sends implicit approval.

## Community Feedback — separated, versioned and reviewed

**F8 → Feedback & Styles → Community Share** has **two independent lanes**:

- **Build Preference**: subjective material choices, statue proportions,
  architectural palettes, decoration, before/after preferences. Examples
  may inform **optional personalized style retrieval** only after review;
  they never become redstone facts or global aesthetic defaults.
- **Redstone Insight**: mechanical circuit failures, truth tables,
  attached original vs corrected schematics, version-scoped reproductions.
  A contributor's "corrected and tested" selection is merely a **claim**.
  It NEVER means that BuildAI, Minecraft, or maintainers verified it.

Every exported `buildai-community-example-v2` ZIP contains `report.json`,
an anonymized source `generated.schem`, an optional `corrected.schem`
and a README. Every intake is **`quarantined`, `unverified`, and
`eligible_for_shared_learning=false`**. These flags are always false
at contributor export time, regardless of rating. GitHub issue submission
does **not** import knowledge, modify local style memory, or train a model.

The v2 report captures `schema_version`, the selected **category and
namespace**, installed BuildAI/Fabric Loader/Minecraft versions,
BuildAI's **source Git revision** embedded at compilation (or `unknown`),
optional **benchmark ID/revision**, Minecraft Edition, and `unknown`
source-generation revision when the original build cannot establish it.
ZIP hashes identify the exact exported schematics. This is version-scoped
provenance, not evidence that a submitted circuit worked.

**F8 Community Share → Build Preference / Redstone Insight → Evidence →
Prepare Local ZIP.** Players explicitly consent to public CC BY 4.0
sharing and manually attach the ZIP to the relevant GitHub issue.
The native file picker allows an optional comparison schematic.
Files are written to
`<Minecraft game directory>/buildai/community-submissions/`.
Schematic exports preserve geometry but remove original prompts, AI
operation history, arbitrary block entities, command scripts, coordinates
and sign labels. User-entered free-text notes are included, so review
them before posting. No in-mod network upload occurs.

For maintainers, `scripts/review_community_feedback.py validate` performs
**offline schema, namespace, trust-label, file-list, SHA-256 and size
checks**. It does NOT establish redstone correctness. A separate
maintainer-written review decision with the exact ZIP checksum,
rights check and independently reproduced Minecraft evidence is required
before `promote` may output a **curated redstone insight**.
Style preferences have their own separate curation destination and
cannot be treated as verified electrical knowledge. A benchmark held out
for evaluation must never be promoted into training examples.
GitHub maintainer permissions, protected branches and independent
reviewer accountability remain necessary—local review JSON alone is not
cryptographic authorization.

The submission and review tooling are a moderation foundation, **not**
a deployed AI-training pipeline or a production upload server.
See [CONTRIBUTING.md](CONTRIBUTING.md) and
[Redstone Mastery Benchy](docs/REDSTONE_MASTERY_BENCHY.md).


## Adaptive builder feedback and executable minigame systems

**F8 > Feedback & Styles > My Build Feedback** stores reviews against the
selected full schematic path. Repeated updates now keep the *history* of
feedback on that build (up to the most recent 120 reviews overall), while
selecting a project loads its latest rating and note. Every future generation
gets an evidence-based topical style profile distilled from the stored review
history, plus concrete recent comments: roof treatment, materials, scale,
natural detail, function, etc. This is **local preference context**, not
machine-learning fine-tuning or proof the model will always obey it.
The newest user prompt remains authoritative.

**Working command-block arena prototype**: an AI-generated blueprint can
include command-block cells and a separate bounded list of actual Java 1.21.1
commands. Command metadata survives client/server transport and local
`.schem` exports as inert metadata. The owner first locks the unrotated
location and starts the physical build with `/buildai confirm`. BuildAI
**always waits after cleanup and normalization** at
`WAITING_COMMAND_APPROVAL` for any command-bearing blueprint, regardless
of how small or fast the build was. It opens a native Minecraft review
showing the server's actual numbered command-to-block mapping. There is no
timer: you can review now or later, and the command blocks remain inert.

A trusted level-4 operator who owns the project chooses either
`/buildai commands approve all`, a subset such as
`/buildai commands approve 1,2,4,7`, or the shorthand
`/buildai commands 1,2,4,7`. The GUI offers equivalent Approve All,
Mark/Unmark, and Approve Marked controls. The server installs only the
approved scripts and clears unapproved command cells. Command-driven systems need the
server's command-block setting enabled and trusted op-4 authorization.

A real functional CTF arena still requires hands-on acceptance testing of
teams, loadouts, flag carrying/return, scoring, win states and resets. The
first version has a CTF quality checklist, bounded scripting and a limited
safe command allowlist, **not** a full minigame simulator. See
[docs/COMMAND_BLOCKS.md](docs/COMMAND_BLOCKS.md) before enabling this on a
server. Command-driven maps cannot yet rotate after AI generation.

## Automatic real-web architectural inspiration

**F8 > Create / AI > Online Inspiration** defaults to ON. For every new
design or revision, BuildAI first *attempts* one genuine hosted Responses
API `web_search` operation for existing Minecraft build references.
Research asks for three or more different builders/examples across at least
two websites. Only responses with a real completed `web_search_call`, three
distinct HTTPS source URLs across two domains, and at least four reusable
architectural techniques count as sufficiently diverse research.
The research model must output generic MASSING, DEPTH, MATERIALS, DETAILS,
LANDSCAPE, and ACCESS principles instead of selecting a build to imitate.

**Player authorship is the rule.** The player's prompt controls the structure's
identity, scale, design features, footprint and purpose. The generator receives
ONLY the abstract craft techniques; **reference titles and URLs are withheld
from the blueprint-generation prompt** (but kept as clickable citations for
the player). It is explicitly instructed not to replicate another builder's
signature roof, layout, exterior silhouette or detailed block composition.
This is a design-direction safeguard, not automated proof of originality.
The strict JSON blueprint format remains unchanged.

If web search is unavailable on the connected ChatGPT model/workspace or
developer API key, times out, or lacks enough independent references or
clear transferable principles, the build continues without it. Progress explicitly reports unavailable research;
BuildAI does not claim that unsourced model recollections are web research.
Successful searches show clickable source links in Minecraft chat, also
available from **Placement / Export > Design QA / Warnings**.

Online inspiration is a separate request that may increase generation time,
ChatGPT plan usage or API-key costs. It is **text-grounded research** rather
than guaranteed inspection of online images, and it does not imply permission
to copy somebody else's build. Toggle it OFF to use local style references
without web searches. Your preference is stored client-side at
`buildai/inspiration-settings.json`.

## Long-running ChatGPT generation and ETA

The BuildAI generation monitor now reports real elapsed time, output character count and average received characters per second. It never invents a fixed percent complete or a countdown from the unobservable model output length. After **two or more completed comparable generations**, BuildAI can display an approximate **remaining-time range** calibrated from local completed durations, always labeled rough. If the request exceeds that range, the UI returns to `remaining unknown`. Until enough samples exist, the UI shows `learning` rather than presenting a misleading clock.

The local history at `buildai/generation-timings.tsv` contains only generation categories, elapsed seconds and character counts—not prompts, OpenAI tokens, blueprints or account data.

## Minimized generation and multiplayer status reliability

Click **Return to Game (Keep Generating)** to close the large generation modal without stopping the AI request. The normal game remains playable; a compact nonblocking HUD shows the actual generation stage, elapsed time, and received characters. Successful completion keeps the blueprint and autosaves it, with a brief HUD confirmation. F8 reopens the controls without restarting the request.

The server's optional `buildai:project_status` packet limits long AI-generated names and material descriptions before encoding; progress packets are best-effort and should never pause construction. If another packet issue occurs, please provide `run/logs/latest.log` along with the screenshot. The prototype still requires careful world backups and does not recover active construction journals after a process restart.

## Local feedback and sign-labeled decoration style packs

F8 → **Feedback & Styles** lets players select a previously saved design, assign a 1–5 rating, and describe changes they liked or disliked. These notes are saved under `buildai/style-memory.json` in the local Minecraft game directory, never committed to GitHub. Only user-saved entries influence future ChatGPT/API prompts. This is **retrieval of preferences in a later prompt**, not fine-tuning or a modification of OpenAI's model.

To teach BuildAI a decorative style, create a normal `.schem` decoration pack with individual examples separated in the world. Put a sign directly in front of each model; its first line is `[BuildAI]`, and its second line is a short label such as `Chair`, `Table`, `Fountain`, `Bookshelf`, or `Chandelier`. A sign containing just one of the recognized object names also works. In **Feedback & Styles**, select the saved blueprint or paste the full path to an external file, then click **Learn Labeled Decorations from Schematic**. For each sign it recognizes, BuildAI samples nearby non-air block states and stores a compact, user-approved style reference to help future designs use similar detailing when requested.

Currently sign extraction works for **Sponge `.schem` and vanilla `.nbt`**, provided the file actually retains sign block-entity text. Imported Litematica files may be opened for review, but automatic sign extraction from `.litematic` is not implemented yet. Crops are bounded to local neighborhoods near each label; this first iteration does **not** infer perfect object boundaries, learn textures outside the crop, or guarantee exact multi-block copies. Schematic content is treated as untrusted data and no embedded commands, inventories, or entities are executed. Users should only import/reference content they have rights to reuse.

## Launch BuildAI directly from IntelliJ

Open the local `BuildAI` project (not ChainFrames), set **Gradle JVM to Java 21**, and run these in the IntelliJ Terminal:

```powershell
git pull origin main
.\gradlew.bat --version
.\gradlew.bat clean build
.\gradlew.bat runClient
```

The last command starts a separate **Minecraft 1.21.1 Fabric development client** with BuildAI loaded. No manual JAR copying is needed for this development test. The first launch may download the Minecraft/Gradle dependencies. If Java 21 is missing, select it in IntelliJ **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JVM**.

For the safest immediate smoke test, create a **brand-new Creative world** and press **F8 → Load Demo Cottage**. The demo produces real blueprint blocks with no OpenAI account, API key, internet, or extra building mods. Select **Export Blueprint as .schem** to verify serialization; **Upload Blueprint** to preview it. Pick X/Z with a left click, sneak-right-click the chosen surface block, then inspect collision warnings carefully. Only in a disposable world run `/buildai confirm` (server operator permission is required in this prototype).

**ChatGPT sign-in is now implemented in source but NOT yet live user-verified**. Open F8 → Continue with ChatGPT; approve plan usage in the browser and return to Minecraft. If login or account entitlements fail, use the optional OpenAI API-key fallback or offline demo.

## Seamless in-game building workflow

BuildAI keeps building creation and design inside Minecraft. The one-time authentication step must use the **system browser** according to the official native-client OAuth guidance; it cannot securely render the ChatGPT password form in a Minecraft WebView.

- F8 → **Continue with ChatGPT** displays a Minecraft status modal and opens OpenAI's secure browser login.
- After OpenAI redirects to BuildAI's local callback, the mod verifies authorization and attempts to restore focus to Minecraft. Whether Windows allows automatic foreground focus depends on OS settings.
- Future sessions reuse/refresh the account authorization when eligible; players do **not** need to sign in for every blueprint.
- Enter a prompt in F8 → **Generate New**, or enable **Chat Mode** and request/revise a design in Minecraft chat. AI uses the connected ChatGPT plan when eligible, with the developer API option as a fallback.
- The AI's generated geometry **becomes an in-memory BuildAI blueprint immediately**, without saving a file, using a file picker, or opening an external download.
- On success, the GUI automatically opens **Placement / Export**, with **Inspect Blueprint Layers** for a top-down cross-section and the existing **Upload & Preview** action for the full 3D ghost and surface/XYZ placement.
- Exporting a `.schem` file remains entirely optional.

Actual OAuth sign-in and the complete generate/preview/build flow still require in-game testing. Do not claim this code makes a fully functional chat account available without user consent and eligible plan access.

## Troubleshooting ChatGPT browser launch on Windows

In IntelliJ's Fabric development client, Java may report that AWT desktop/browser integration is unsupported even with Brave or another default browser installed. BuildAI now tries the Windows default-browser handlers directly after `Desktop.browse`.

If your selected default browser doesn't open, keep the Minecraft **Connect with ChatGPT** screen open and click **Copy Sign-in Link**. Paste that temporary URL into your preferred browser (Brave, Firefox, etc.). The original local callback remains listening and should finish login automatically when OpenAI redirects back to BuildAI. Alternatively click **Open Browser** to retry. The URL is unique to the sign-in attempt: do not post it publicly, and don't try a previously copied link after its sign-in session expires.

This only repairs local browser launching; account eligibility and OpenAI permission still need successful live testing.

## Live in-game AI generation status

When you press **Generate New** or **Revise Existing**, BuildAI opens a Minecraft-native generation monitor. It reports the **real** phases of generation: checking account/model availability, opening the HTTPS request, receiving model responses, compiling JSON operations into voxels, and validating block IDs. For streaming ChatGPT responses it also counts design characters actually received; developer API key calls report their milestones when the response arrives.

The monitor shows elapsed time and the time since the most recent activity. Its moving progress stripe is **indeterminate**, not a fabricated completion percentage. No claim is made about the model's internal chain of thought, research or unobservable steps. You can hide the monitor and play while generation runs; reopen F8 and press Generate to return to that same active monitor. A successful generation opens Placement / Export with the completed blueprint, while an error leaves the previous blueprint intact and displays the failure.

**Scale limitation for testing:** the current parametric compiler permits up to 96 blocks along each axis and 250,000 bounding cells. The prompt intentionally targets buildings around 48 × 32 × 48 or smaller; a historically faithful, furnished full-size White House may exceed the present geometry or model-output budget. A lengthy or failed generation is not evidence that a large building has been verified or rendered.

## ChatGPT login (test implementation)

In a disposable Minecraft 1.21.1 Fabric world, press **F8 → Continue with ChatGPT**. BuildAI opens your system browser on OpenAI's authorization site. Sign in with your own account and allow **ChatGPT plan usage**, then return to Minecraft. The F8 interface should display the connected account. You can then type a building request into F8 → Generate New, or turn on Chat Mode and type building requests into ordinary Minecraft chat. Follow-up prompts revise the active blueprint; type `new <prompt>` to start over, `reset` to clear the draft, or `exit` to return to normal chat.

The flow uses dynamic OAuth client registration with a stable local host ID, loopback PKCE, cryptographic ID-token verification, authorized plan scopes, account-model discovery, and streaming Responses API requests with `store:false`. **This has compiled, but real login and inference require in-game acceptance testing by a user with eligible ChatGPT Plus or Pro access.** User credentials are not sent through the Minecraft server. The mod saves authorized OAuth credentials in an owner-restricted local file under `~/.buildai/`; users can disconnect in F8 and revoke plan usage from ChatGPT Settings. Do not share that file or your ChatGPT password.

## Full-scale designs and normal-player placement

BuildAI no longer asks the AI to compress a building into the former 48×32×48 target. Generated architecture uses approximate real-world proportions at roughly **one horizontal Minecraft block per meter**, with a 1.8-block-tall avatar as human-scale reference. It must **not silently downscale** to fit a prompt budget. Full historical accuracy still requires measurements and in-world review.

The single-blueprint engine currently accommodates up to **4,000,000 cells** and up to 512 blocks along any axis (subject to the volume cap), with palette run-length upload compression. This is a **per-project runtime safety limit**, not unlimited megastructure support; unbounded architectures require future sectioned generation, streaming construction and persistent project journals. Oversized projects now report a clear error instead of claiming to have generated a miniature at full scale.

BuildAI never uses an artificial observer/spectator camera. After **Upload & Preview**, close F8 and fly/walk normally. The ghost follows your player until you press **G**. G freezes the entire XYZ exactly where the preview currently appears; **no aimed-at block or ground target is required**. While the ghost is frozen, **LEFT/RIGHT** arrows move it horizontally relative to the camera heading captured at G, **UP/DOWN** changes vertical position, **CTRL+LEFT/RIGHT** rotates the structure in 90-degree steps, and **CTRL+UP/DOWN** or the mouse wheel moves it forward/back. Rotation stays centered. Press **Enter** to validate and lock the final XYZ on the server, or **R** to reset the preview. Only then use **Confirm & Build** or `/buildai confirm`. Server permission, loaded-chunk and protected block checks are unchanged. Command-driven builds remain unrotatable until their command coordinates are safely transformed. `/buildai hide` hides the ghost without canceling the project.

**Per-confirm construction modes:** The F8 **Confirm & Build** button and plain `/buildai confirm` still use FULL excavation of the entire blueprint volume (including blank/AIR cells) before construction. Use `/buildai confirm excavate:no` for **terrain-preserving overlay** (only placed blueprint blocks replace terrain). Use **`/buildai confirm hollow:yes`** for **interior-only clearing**, which automatically disables full-volume excavation: it identifies AIR cells between standing blueprint walls: either a room bounded on both X/Z axes, or a tunnel/corridor with long, continuous, parallel side walls. **It no longer requires a blueprint floor or ceiling** to clear a jungle-temple passage or mountainside corridor. It removes *only pre-existing world blocks in those interior-air cells*, preserving exterior terrain, existing air and ALL non-air blueprint positions (walls, furniture, lights, interior decorations). Planned non-air blueprint positions still replace any overlapping old terrain. If the blueprint lacks enough wall-defined interior space (e.g., a standalone sculpture, disconnected pillars or isolated facade), confirmation refuses the hollow mode **without changing the world**; it does not guess and excavate a large cuboid. The preflight rejects bedrock, block entities/containers, and unloaded chunks anywhere the operation would edit. Interior-only clearing does not run the full-site excavation cinematic. `hollow:yes excavate:yes` is rejected as conflicting.
**No foundation:** Add `foundation:no` to any compatible mode, e.g., **`/buildai confirm foundation:no hollow:yes`** for a house embedded in existing terrain, or `/buildai confirm foundation:no excavate:no` for a mountainside sculpture. The omission checker now follows **all contiguous, connected footprint-wide platform layers** from the lowest occupied blueprint layer, even when the platform is smaller than the overall design bounding box and the layers switch materials (such as dirt topped with grass blocks). It no longer stops at four layers or demands that 85% of the platform be made of the same material. Optional material hints use **`/buildai confirm foundation:no:grass_block,dirt,stonebrick,bedrock hollow:yes`**, where unqualified IDs default to `minecraft:` and `stonebrick` aliases to `stone_bricks`. Hints ADD to recognized foundation materials; they are **not permission to delete every matching block in the structure**. The detector requires broad, dense, connected and spatially consistent lower platforms and refuses uncertain statue feet, decorative patches, redstone/mechanical floors and irregular construction. It intentionally does not globally erase dirt/grass walls or floors. If no recognizable base is found, BuildAI warns rather than removing ambiguous architecture. For best results, also request **"do not generate any foundation, base, or footprint-wide platform"** in the AI prompt. Foundation omissions affect what BuildAI **places**; hollowing affects which *existing world* blocks are cleared. `foundation:no` alone never removes an already-existing dirt overhang left from another build or natural hillside terrain; those require a suitable excavation mode or manual restoration.

**Normal-camera placement, cinematic excavation, and large-blueprint construction still require actual Minecraft click-through testing.** A crash after `/buildai confirm` is not diagnosed by the Gradle `runClient` exit status alone: please supply the timestamped file in `run/crash-reports/` for the client stack trace. Passing CI does not prove live renderer stability or safe large-world construction. Use a disposable backed-up world. This prototype cannot safely recover ongoing projects after restart.

## First in-world test (creative, throwaway world)

1. Press **F8** to open BuildAI.
2. Enter an OpenAI developer API key (optional if importing rather than generating) and describe an ornate house. Click **Generate with AI**.
3. Or choose **Import File** to select .schem / .schematic / .litematic / .nbt using a desktop file picker.
4. Adjust **Surface Y** to the local blueprint layer intended to match the terrain surface. Use **Export .schem** to save generated build files to disk.
5. Click **Upload Blueprint**, close GUI, aim at a block and **left-click** to mark horizontal X/Z. See green/red outline guidance.
6. **Sneak-right-click** your desired surface block to lock absolute Y. An existing-volume collision report appears in chat.
7. Inspect the outline and only on a disposable world run **/buildai confirm**. Construction excavates overlapping blocks without drops, then builds Y-layer by Y-layer.
8. Use **/buildai status**, **/buildai cinematic**, **/buildai fast**, **/buildai cancel**, **/buildai undo**, and (for operators level 4 only) **/buildai undo force**.

This prototype restricts world modification to **operators** until claim/permission checks are implemented. **Safe Undo** now prechecks affected cells for protected block entities and then restores the original block states in bounded server-tick batches. It recognizes command blocks belonging to the generated design. If new inventory/script block entities conflict, **Force Undo (OP4)** is available through an explicit in-game confirmation and can destroy those new contents. Neither mode refunds supplies or recovers construction journals after server restarts. Undo is not a substitute for world backups.

## Survival supplies

Mark a chest with a sign that reads **BuildAI** on any line. After selecting the building's surface, **sneak-right-click the chest** to link it. Add materials; they are consumed as each block is placed. Additional marked chests can be linked. Nearby chest HUD shows material totals and two progress bars.

Use **/buildai materials** to see consumed + available / required per material. When items run out, construction pauses until more appear.

Eligible whole-block family replacements, slab-to-slab, stair-to-stair, pane-to-pane, and similar exact shape families can be configured with:

    /buildai substitute minecraft:stone minecraft:cobblestone

Glass-block-to-pane is allowed only at glass positions with an original glass block immediately above or below. The compiler keeps horizontal skylight glass as full blocks. Invalid swaps are rejected. Fluid swaps are disabled until proper Survival accounting is tested.

**WARNING:** The approved build bounding volume is excavated after /confirm, including any nonair blocks in what should be interior space. It will refuse bedrock and existing block entities, and prints warnings for other existing blocks.

## Architecture

- Format adapters -> canonical blockstate blueprint.
- AI generator -> limited JSON geometry operations -> same canonical blueprint.
- Deterministic 3-axis voxel scan, canonical material accounting and shape-family substitution.
- Client file import/AI generation -> chunked compressed upload -> authoritative server validation.
- Selected X/Z + world surface block -> world-space alignment.
- Server tick engine -> staged excavation -> progressive material-consuming placement -> staging cleanup.

See [docs/AI_CONTRACT.md](docs/AI_CONTRACT.md) and [docs/REDSTONE_REFERENCE.md](docs/REDSTONE_REFERENCE.md). For any AI request involving **redstone, automated machines, pistons, observers, hoppers, crafters, redstone circuits or command-block games**, BuildAI loads its full bundled [Java 1.21.1 Redstone Master Handbook](src/main/resources/assets/buildai/redstone_master_handbook.txt) as mandatory technical model context. The [machine-readable glossary](src/main/resources/assets/buildai/redstone_reference.json) documents 59 component families and important quirks. Both connected ChatGPT and API-key paths use the same reference; revisions retain it for existing redstone builds. Future circuits are expected to include a `redstone_plan.circuits` engineering manifest (input, signalPath, output, timing, reset, test), checked by read-only Design QA for missing mechanisms, unsupported dust and default-facing components. **This is informed prompting and structural inspection, not trained model weights or proof a redstone contraption works**. Final validation still requires Minecraft 1.21.1 gameplay.

## Safety notes

Never use a valuable world for v0.1. Imported files are capped, unknown blocks rejected, and command-bearing entities/inventories stripped. Run a backup before testing. Do not expect a partially built project to resume after server restart until persistence is implemented.
