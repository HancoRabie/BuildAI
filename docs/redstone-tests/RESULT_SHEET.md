# BuildAI Redstone Test — Evidence & Optimization Record

Copy this sheet for **each isolated fixture and each BuildAI revision**.
Never change a baseline input prompt between baseline/candidate comparisons.

## Session metadata

| Field | Entry |
| --- | --- |
| Track / fixture | R01A (or new ID) |
| Date | |
| BuildAI GitHub commit | |
| Minecraft Java version | 1.21.1 |
| Fabric / modpack differences | |
| Test world (disposable copy) | |
| Prompt hash / exact prompt file | |
| Design JSON or schematic reference | |
| Generation elapsed / retry count | |
| Model route | |
| Status | NOT_RUN / FAIL / PASS_STATIC_ONLY / PASS_GAMEPLAY / BLOCKED_BY_TEST_SETUP |

## Physical acceptance (NO invented observations)

| Test ID | Expected outcome | Actual observation, location and evidence | Pass? |
| --- | --- | --- | --- |
| 01 | Idle state correct | | |
| 02 | North output isolated, 5 cycles | | |
| 03 | East output isolated, 5 cycles | | |
| 04 | South output isolated, 5 cycles | | |
| 05 | West output isolated, 5 cycles | | |
| 06 | All outputs ON then OFF, 5 cycles | | |
| 07 | Mixed controls don't cross-activate | | |
| 08 | Reload/reset preserves expected state | | |

Use another acceptance table for other tracks. Capture important
gameplay evidence: timestamps/ticks where instrumented, screenshots,
actual inventories, world positions, unexpected output, omitted
components and exact failures. A generated `redstone_plan` is not evidence.

## Root cause after a failure

**What failed:**  
**Expected state:**  
**Observed state:**  
**Exact XYZ / state of suspect component:**  
**Hypothesis:**  
**Narrow correction:** (knowledge / orientation resolver / specific
blueprint region / RBL module / deterministic QA rule)  
**New regression unit test:**  
**Unresolved:**  

## Before/after optimization (ONLY after gameplay pass)

| Metric | Baseline | Candidate | Regression? |
| --- | --- | --- | --- |
| Passed physical acceptance tests / total | | | |
| Repetitions passed / attempted | | | |
| Footprint X×Y×Z | | | |
| Redstone-related block count | | | |
| Total placed blocks | | | |
| Unique required material categories | | | |
| Instrumented trigger latency (ticks, if available) | | | |
| Unwanted activations | | | |
| World reload/reset issues | | | |
| Player accessibility/safety | | | |

**Keep/reject change:**  
**Evidence links/screenshots:**  
**RBL verification status:** reference_unverified / static_validated /
in_game_verified (evidence required)  

Do not promote a module to `in_game_verified` merely because the
AI labels it working or a JUnit geometry test passes. See
`docs/REDSTONE_BLUEPRINT_LANGUAGE.md`.
