# BuildAI whole-schematic architectural references

## Purpose
Whole-build style references let solo server builders use compatible schematic
files as **opt-in architectural inspiration** without having to annotate every
example with signs.

This is not copying a source world, training an LLM, nor a computer-vision
assessment. It derives a bounded text report from Minecraft voxel states and
feeds the report into subsequent ChatGPT or API-key blueprint prompts.

## Use in Minecraft (Fabric 1.21.1)
1. Open **F8 > Feedback & Styles > Analyze Whole Builds / Reference Library**.
2. Select a saved blueprint or paste the full path of a local
   `.schem`, `.schematic`, `.litematic` or `.nbt` file.
3. Click **Analyze & Save Selected Schematic**. The analysis is read-only and
   does not change the current blueprint or world.
4. To batch import, put up to 12 schematic files in the active game's
   `buildai/style-references/` directory and click **Scan Reference Folder**.
   The folder is created the first time you request a folder scan.
5. Read the scrollable analysis report inside the reference screen.
6. Future AI building requests incorporate up to two latest enabled reports
   together with other approved feedback. The original schematic files are not
   uploaded to OpenAI.

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
