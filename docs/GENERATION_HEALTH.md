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
remains silent or a read blocks. The dedicated SSE watchdog closes requests
after 8 minutes with no blueprint text, or 3 minutes of inactivity after
text starts. The model may have been thinking all along: neither a
zero-character counter nor a healthy network probe proves a remote failure.
When it closes a silent request, the prompt remains available to retry.

Users can:
- **Check Request (Safe):** diagnostic only, never cancels or retries.
- **Return to Game (Keep Generating):** play while the model continues.
- **Cancel | Keep Prompt:** request clean cancellation; preserves the prompt
  and current saved blueprint, then allows an explicit new attempt.
- **Back to BuildAI:** return to the studio without discarding progress.

Existing sessions launched with an older installed JAR cannot acquire the
new controls or prompt backup retroactively. Copy their prompt manually
before restarting, if needed.

## Why a prompt can be very slow before first text

The model may be spending time planning complex architecture/redstone, or
its tool/model backend or network stream may be stalled. It can emit
`response.created` and `response.in_progress` without emitting
`response.output_text.delta`. We never fabricate progress bars or
pretend that **0 blueprint characters** measures actual reasoning work.
Even a model still actively processing could have no visible text until
late. For very large requests, section-based generation can reduce the
size of a single model response without shrinking the desired build.
