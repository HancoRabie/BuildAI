# BuildAI whole-schematic architectural references

## Purpose
Whole-build style references let solo server builders use compatible schematic
files as **opt-in architectural inspiration** without having to annotate every
example with signs.

This is not copying a source world, training an LLM, nor a computer-vision
assessment. It derives a bounded text report from Minecraft voxel states and
feeds the report into subsequent ChatGPT or API-key blueprint prompts.

## Use in Minecraft (Fabric 1.21.1)
1. Open **F8 > Feedback & Styles > STYLE REFERENCES > ANALYZE A WHOLE BUILD**.
2. Select a saved blueprint or paste the full path of a local
   `.schem`, `.schematic`, `.litematic` or `.nbt` file.
3. Click **ANALYZE FILE**. The analysis is read-only and
   does not change the current blueprint or world.
4. To batch import, put up to 12 schematic files in the active game's
   `buildai/style-references/` directory and click **SCAN FOLDER**.
   The folder is created the first time you request a folder scan.
5. The compact **reference summary fits the screen without scrolling**.
   **COPY REPORT** copies the full technical report for detailed inspection.
6. Select a saved reference and press **USE & CREATE**. BuildAI promotes that
   reference for the next AI building prompt, then opens the BuildAI creator.
   Up to two most-recent enabled references are included in prompt context.
   The original schematic files are not uploaded to OpenAI.

Note: IntelliJ's `runClient` game directory is typically
`<project>/run/`; the regular Minecraft launcher uses the particular instance
game directory. Files and saved references do not automatically follow between
instances.

## What the scanner actually measures
- Whole-blueprint dimensions, occupied blocks and unique block IDs
- Ranked material counts and different block-family detailing counts
- Upper-bounds slab/stair usage and most-occupied height layer
- Up to four small, exact **relative 3D block-state motifs** centered around
  interesting, air-exposed structural/decorative blocks

The model gets these statistics as untrusted, user-approved reference context,
not as authoritative engineering instructions. Source sign/entity/command data
is not executed or forwarded. The analysis is stored under
`buildai/style-memory.json` and isn't committed to GitHub.

## Honest limits and next iteration
- A block count doesn't tell the AI why a design looks good, where the real
  roof or front facade starts, which areas are intentionally asymmetric, or how
  furniture should be arranged. Some extracted motifs may come from unhelpful
  locations. Inspect the report; don't take a high count as proof of quality.
- Model prompt budgets limit detail transmitted (currently up to two reports
  of 3,800 characters each). Additional references remain locally saved but
  aren't simultaneously presented.
- Complex decorative patterns, room relationships and redstone cannot be
  automatically reconstructed or verified from a short descriptive report.
- Stronger future analysis would combine deterministic 3D geometry segmentation
  (wall/roof/opening/motif boundaries) with **user-approved multi-angle renders**
  or user-provided screenshots for visual reasoning. That work is not included
  in this initial implementation.
- The source schematic may include blocks from other mods; those are only
  reference observations, not guaranteed usable in vanilla building output.
- Only use source schematics whose creators permit your planned reference/reuse
  and respect original authors' sharing/license terms.

## Existing sign-labeled decor references
They remain supported through **Learn Labeled Decorations from Schematic**
inside Feedback & Styles. The new whole-build analyzer is independent and
requires no signs. Both kinds of reference can influence future generations.

## Per-build feedback in the same studio

F8 > Feedback & Styles > MY BUILD FEEDBACK contains a separate project picker,
star rating, comment field and **SAVE FEEDBACK** action. Switching to another
file loads its existing rating and comment, or a blank note for a new build;
unsaved edits are cached separately while this screen is open. To review an
external schematic, select OTHER FILE, paste the path and click USE THIS FILE.
Changing the path alone cannot redirect a save.

New feedback records use normalized full local schematic paths, so two files
with identical filenames in different folders cannot share comments. Older
filename-only feedback is displayed for original BuildAI saved builds with a
legacy warning; saving that review again attaches the exact project path.
Only the newest review per full path is kept on subsequent saves.
