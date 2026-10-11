# BuildAI F8 GUI / Flow Acceptance Checklist (Fabric 1.21.1)

Use a **new disposable Creative world** opened with `gradle runClient`. A passing Gradle build confirms compilation, not real click-through acceptance.

## First screen — Create / AI

| Control | Expected behavior / verified by inspection |
|---|---|
| Create / AI | Selects Create tab; keeps draft description and temporary API-key text through tab changes. |
| Placement / Export | Switches to placement controls without discarding the draft. |
| How To | Opens offline guide; Previous/Next and Scroll up/down navigate all text. Back returns to the originating BuildAI screen. |
| Account / Login Help | Opens topic 2 of the How To guide, explicitly explaining that ChatGPT account login is **not implemented**. This is help, not a fake sign-in. |
| API key option | Shows/hides temporary OpenAI developer API key field. The key is held in client memory only. API access may be billed separately. |
| Generate New | Requires description and API key; asynchronously generates a full blueprint and preserves failure state without replacing the existing one. |
| Revise Existing | Requires an earlier AI-generated design, description and API key; sends previous primitive design and accepts a complete replacement candidate. |
| Import File | Opens a Swing-native file chooser; validates imported format and exact block states; cancellation doesn't destroy the current draft. |
| Load Demo Cottage | Loads a bounded 15×11×13 offline demo, no login or key needed. |
| Chat Mode OFF/ON | Rejects enabling without configured API key; when on, ordinary chat prompts go privately to the generator. `exit` disables it and `reset` clears the active design. Never intercept slash commands. |
| Hide Preview | Hides only client-side ghost rendering. Does **not** cancel server project or erase physical blocks. |
| Close | Returns to Minecraft without executing or changing the project. |

## Second screen — Placement / Export

| Control | Expected behavior / verified by inspection |
|---|---|
| Surface Y - / + | Requires a blueprint; adjusts blueprint ground reference in valid 0..height-1 range; retains it across F8 reopens. |
| Upload & Preview | Requires a valid blueprint and server capability; sends compressed data in chunks; **waits for server acceptance** before enabling ghost selection. Rejects duplicate pending requests. |
| Export Blueprint as .schem | Requires a blueprint; uses Swing event thread for dialog, background for file encoding; no silent overwriting of an existing .schem. |
| Hide Preview | Dismisses only the ghost grid, never an existing building. |
| Cancel Project | Sends the authenticated server command `/buildai cancel`; partial construction is kept. Server reports result in chat. |
| Build commands in How To | Opens command documentation and supports scrolling. |
| Close | Returns to game; leaves any active server project untouched. |

## Server validation

- The server must reject oversized or unsupported blueprints and show failure, *without* enabling selection on the client.
- Test in Creative: upload demo, left-click horizontal corner, sneak-right-click ground block, see collision count, then run `/buildai confirm` in the disposable world.
- Test `/buildai status` and verify no input items consumed in Creative.
- After finish, preview grid must disappear; remaining panes/iron bars are normalized.
- Test `/buildai hide`, `/buildai cancel`, `/buildai undo` and `/buildai normalize` under safe conditions.
- In Survival on an operator-enabled disposable world, link a chest marked `BuildAI`, confirm materials are consumed once, check pause/resume after shortages and the nearby material HUD. The prototype is not yet crash-safe.

## Known limits; don't misrepresent them

- **ChatGPT account login is not implemented.** Open-source/public GitHub status alone doesn't confer OAuth authentication. Current AI requires OpenAI developer API billing.
- JUnit tests run on the blueprint's platform-neutral geometry/packet portions. Vanilla Minecraft bootstrap in ordinary JUnit bypasses Fabric's access wideners; NBT export/import and actual world modification require a Fabric runtime integration test.
- Actual in-game click-through, account requests, import dialogs, network permission errors, entity NBT handling, rollback after crashes and redstone machine functionality need separate manual/integration acceptance. Compilation alone is not proof of runtime behavior.
