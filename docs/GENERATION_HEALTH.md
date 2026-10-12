# BuildAI model request health and prompt recovery

Minecraft Java 1.21.1 | F8 > Create / AI > Generate New / Revise Existing

## What "Check Request (Safe)" really means

The active BuildAI generation continues untouched. **Check Request** performs
a second **GET https://api.openai.com/v1/models** with the currently available
account credentials, which sends **NO building prompt**, creates **NO new
blueprint**, does not cancel the original response, and does not replace a
saved file. It can report **API reachability and authorization** only.

The same screen also reports local facts from the still-active model stream:
- Whether this is **GENERATE NEW** (previous specification = null) or a
  **REVISION** (an actual previous design was included).
- Which model was requested.
- Whether the initial HTTP response was received (HTTP 200).
- Time of the last **actual SSE event** and its event type.
- Number of genuine blueprint text characters received.

**Important:** A successful GET /models does NOT mean the in-progress
response is making progress. BuildAI submits Responses API calls with
`store:false`, and therefore cannot reliably retrieve/poll the current
generation by response ID. There is no "poke the same model and make it
continue" mechanism. The button is explicitly labeled as a check, not
"Resume". The health probe can also fail for an account whose model-catalog
permissions differ from generation permissions; this does not cancel the
live response.

The previous design is never silently sent on **Generate New** even if
another blueprint was imported. Only Revise Existing includes history.

## Persistent prompt backup

Every new generation request writes its exact prompt (max 4000 characters)
**before** the network operation starts to:

`<Minecraft game directory>/buildai/last-generation-prompt.txt`

On opening a new F8 studio screen with an empty prompt field, the last
request is restored. Existing typed text is never replaced. This file
contains the player's building prompt ONLY — **no OAuth code, API key,
model access token, or previous design JSON**. Because this is local storage,
users should treat it as private. The prompt survives a normal restart,
failure, cancellation or crash. Generation failures never erase an
existing saved blueprint. It does not automatically resubmit a request:
the player explicitly chooses when to try again.

## Stall protection and user options

Streaming HTTP request headers may be accepted while the text event body
remains silent or a read blocks. The dedicated SSE watchdog is **enabled by
default** and closes requests after 8 minutes with no blueprint text, or
3 minutes of inactivity after text starts. The user can change this while
generation is running via a distinct full-width **AUTO-STOP: ON/OFF** button
in the generation monitor. This alters only the active request's silence
watchdog; it does **not** restart, resend, cancel, or lose the prompt.

- **AUTO-STOP ON (default):** automatic 8-minute pre-text / 3-minute
  mid-text silence protection.
- **AUTO-STOP OFF:** do not automatically end the ongoing OpenAI SSE request
  for silence. The user can keep waiting as long as the connection and
  model service permit; Cancel & Refine still works.
- **Turning ON again:** start a fresh full 8-minute / 3-minute grace period
  from when the toggle was re-enabled. No instantaneous cancellation just
  because the earlier elapsed time was greater than 8 minutes.
- The state is **per active request**, not a global mod preference, so new
  generations start with protection ON. The toggle is unavailable after the
  generation has already completed or failed; it cannot resurrect a closed
  network request.
- This setting does NOT override HTTP connection/request timeouts before the
  streaming body opens, network failures, server-side limits or cancellation
  by the user. It cannot force the remote model to continue reasoning.

The model may have been thinking all along: neither a zero-character counter
nor a healthy network probe proves a remote failure. When a request closes,
the original building prompt remains available to retry.

Users can:
- **Check Request (Safe):** diagnostic only, never cancels or retries.
- **Return to Game (Keep Generating):** play while the model continues.
- **Cancel & Refine:** interrupt the active worker and open the in-game
  recovery editor immediately. The original building prompt is populated
  and editable. Add optional positive clarification in a separate field
  without transmitting the previous assistant refusal as conversation context.
  Wait for the original network request to ACTUALLY close before the Start
  New button can initiate a separate independent model request. Late output
  from the cancelled attempt can never overwrite the existing blueprint.
- **Refine & Start Fresh:** if a model failed or was cancelled, build a NEW
  model request containing the player's original prompt plus any explicitly
  edited clarifications. This is NOT continuation of the previous response.
- **Back to BuildAI:** return to the studio without discarding progress.

Both the F8 Create/AI prompt and the recovery editor's original prompt have
a small **× clear** action at the right edge. Clearing the original prompt
also deletes the local `last-generation-prompt.txt` backup and clears the
studio's cached text, so it stays empty when F8 is reopened. The recovery
editor has a separate × for its clarification field, which clears ONLY that
clarification. The Restore Original button remains an explicit option while
reviewing the old attempt, never an automatic restoration after clearing.

A model refusal about missing inventory preloads or unverified machinery
also triggers at most ONE automatic correction request (a new HTTP request,
without any previous assistant turn). Only the category of the issue is
translated into short developer guidance; hard engine limits do not trigger
this retry. The new model attempt is still an engineering proposal to be
tested in Minecraft, never proof that it works.

Existing sessions launched with an older installed JAR cannot acquire the
new controls or prompt backup retroactively. Copy their prompt manually
before restarting, if needed.

## Staged engineering for large multi-floor farms

When a **fresh** request explicitly specifies three or more numbered farm
floors (`Floor 1:`, `Floor 2:`, `Floor 3:`) and calls for automated
harvesting/redstone, BuildAI now uses a staged engineering workflow rather
than attempting every voxel, machine and architectural feature in one
enormous model response:

1. One architectural-shell request with full human-scale footprint, floor
   decks, stairs, windows and utility shafts.
2. An independent model request for **each** numbered crop floor within its
   assigned global Y band, including preplanting and local redstone.
3. A final integration request to connect master controls and item paths
   into the ground-floor storage. Farming floors must declare genuine
   `item_output` and `trigger_input` ports with global XYZ/facing.
4. Merge stage operations in a shared global coordinate system, reject
   out-of-band floor geometry/changed dimensions, then compile and QA the
   entire blueprint. The world remains untouched until normal preview
   and explicit build confirmation.

**No repeated reference browsing:** the Java 1.21.1 redstone handbook is
read from the installed JAR **once per Minecraft process**, not once per
redstone dust piece. Model requests receive a focused technical excerpt
based on the currently relevant mechanisms. A single optional verified
inspiration search result is shared across the stages instead of repeating
web research for each floor.

**Checkpoints:** After each stage passes the contract checks, BuildAI
saves its corrected JSON in
`buildai/staged-workspaces/<request+model+rules hash>/stage-N.json`.
A stopped or failed generation with the exact same user request, model and
reference version can reuse completed stages; changed instructions start
fresh. A successful project marks those checkpoints complete, so choosing
to remake the same prompt intentionally starts a NEW independent design.
Checkpoint files contain the generated architecture JSON, no API keys,
tokens or model-conversation IDs. Don't delete the workspace if you
want an incomplete run to resume.

**Limitations:** This first stage system deliberately targets explicitly
numbered, multi-floor automated farms, not every large project. It can
make several independent model requests, so total requests and token
consumption may rise even if each stage is easier. It does not replace
actual Minecraft redstone, water-flow, inventory-NBT and item-transport
tests. Shared ports and structural QA detect some omissions but do NOT
guarantee a functioning master button or hopper chain. Revisions of an
existing completed farm still use BuildAI's previous revision flow.

## Why a prompt can be very slow before first text

The model may be spending time planning complex architecture/redstone, or
its tool/model backend or network stream may be stalled. It can emit
`response.created` and `response.in_progress` without emitting
`response.output_text.delta`. We never fabricate progress bars or
pretend that **0 blueprint characters** measures actual reasoning work.
Even a model still actively processing could have no visible text until
late. For very large requests, section-based generation can reduce the
size of a single model response without shrinking the desired build.
