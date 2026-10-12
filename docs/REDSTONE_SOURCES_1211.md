# BuildAI Redstone Source Compatibility Audit — Minecraft Java 1.21.1

**Target:** Java Edition 1.21.1 (Fabric), with no later redstone experimental features.
**Purpose:** Select useful engineering ideas from older Java discussions and
a modern Bedrock tutorial without contaminating the model's Java reference.

## Sources examined

1. [Minecraft Forum: Redstone Guide v1.21](https://www.minecraftforum.net/forums/minecraft-java-edition/redstone-discussion-and/350815-redstone-guide-v1-21).
   This is an *author-maintained guide revision*, posted August 12, 2011,
   NOT a Minecraft 1.21 game-version tutorial. Read the principal guide,
   circuit sections and supporting discussion.
2. [Microsoft Learn: A Guide to Redstone](https://learn.microsoft.com/en-us/minecraft/creator/documents/redstoneguide?view=minecraft-bedrock-stable).
   Explicitly BEDROCK Edition. Read the fundamentals, timing and components.
3. Related forum discussions:
   [comparator basics](https://www.minecraftforum.net/forums/minecraft-java-edition/redstone-discussion-and/351270-redstone-comparator),
   [hopper clock inventory timings](https://www.minecraftforum.net/forums/minecraft-java-edition/redstone-discussion-and/349173-hopper-time-help),
   [stability of clock designs](https://www.minecraftforum.net/forums/minecraft-java-edition/redstone-discussion-and/348368-redstone-clocks),
   [detecting completed minecart unloading](https://www.minecraftforum.net/forums/minecraft-java-edition/redstone-discussion-and/2256498-timer-help).
   Treat these as historical learning examples, not verified current schematics.
4. Cross-checks:
   [Minecraft Wiki: quasi-connectivity](https://minecraft.wiki/w/Tutorial:Quasi-connectivity),
   [Minecraft Wiki: redstone tips](https://minecraft.wiki/w/Tutorial:Redstone_tips),
   [Minecraft Wiki: redstone repeater](https://minecraft.wiki/w/Redstone_Repeater),
   [Mojang: Java 1.21.2 experimental redstone](https://feedback.minecraft.net/hc/en-us/articles/31261174284557-Minecraft-Java-Edition-1-21-2-Bundles-of-Bravery).

## Accepted as concepts, but implemented using Java 1.21.1 semantics

| Concept | Applicable rule |
| --- | --- |
| Boolean logic | Verify six two-input truth tables (AND/NAND/OR/NOR/XOR/XNOR). |
| Signal transmission | Dust strength 15..0 attenuates; distinguish binary activation from analog strength. |
| Directional diode | Java repeater/comparator blockstate `facing` points to rear/input, physical output opposite. |
| Delay and buffering | 1..4 redstone ticks = 2..8 game ticks; short pulses may stretch or be suppressed, not a long general hold timer. |
| Torch inversion | Power the attachment block to switch an attached torch off; account for propagation and burnout. |
| RS memory | Check set/reset priority, persistence, simultaneous-input behavior, reboot state. |
| Monostable/one-shot | Trigger once, hold for defined time, reset deterministically and consider retriggers. |
| T flip-flop | One complete pulse changes state exactly once; consider a copper bulb if suitable. |
| Clocks | Choose start/stop, duty cycle, timer items and reload behavior. Do not default to continuous loops. |
| Comparator | Compare: rear if rear >= strongest side else 0; subtract: max(0, rear-strongest side). |
| Hopper timer | Typical enabled hopper transfers one item per 8 game ticks; actual stocked inventory is required. |
| Event-driven machines | Where possible detect actual completion/empty/full state rather than guessing an arbitrary delay. |

## Rejected, corrected or explicitly not imported

- **Forum “v1.21” != Java 1.21.1.** Antique 2011 block placement descriptions,
  button time assumptions, power flow diagrams and clock designs are not
  version-validated simply because their label resembles the game version.
- **Button duration:** 2011 article's 0.9-second universal button advice is
  obsolete. Java stone/polished blackstone button: 20 game ticks (1 second);
  wooden: 30 game ticks (1.5 seconds), at 20 TPS.
- **Fading dust:** reduced signal is NOT only an aesthetic difference.
  Analog comparators, detectors and threshold logic distinguish signal strengths.
- **Bedrock piston animation:** do not use Bedrock statements such as
  “1 tick extend, 0 tick retract” as an exact Java tick-event model.
  Java piston/QC/update sequences need engine-level verification.
- **Pressure plate mob weight:** no generic analog output difference between
  a creeper and iron golem on ordinary stone/wood pressure plates. Weighted
  plates, not basic plates, give analog counts of triggering entities/items.
- **Java QC:** pistons, dispensers and droppers can activate through the
  space above them and sometimes await a separate block update; the crafter
  does not use this behavior. Bedrock mechanisms must not be substituted.
- **1.21.2 experiments:** newer *optional experimental* dust update order was
  introduced after 1.21.1; don't claim it is enabled in this target.
- **Inventory-dependent machines:** hoppers, droppers and dispensers have
  real items/slots, not just blocks. A dispenser with no bone meal or bucket
  is not a growing or water-release mechanism. Don't invent villagers,
  worker mods or unrelated features to fill gaps.

## Operational use in BuildAI

- The cached file `src/main/resources/assets/buildai/redstone_master_handbook.txt`
  is read once per Minecraft process and its relevant portions provided to
  model requests classified as redstone/engineering. The model does not
  browse the historical forum or Microsoft site for every dust placement.
- The supplementary `src/main/resources/assets/buildai/redstone_reference.json`
  carries selected component-specific rules and version provenance.
- A non-redstone architecture request still gets only a compact basic hint.
- Review redstone as `requested outcome -> real input -> conductive path ->
  logic/timing -> output mechanism -> reset`; report unsupported assumptions.
  A static audit CANNOT prove in-world function.
- The Minecraft Forum as a whole contains many unrelated sections and
  thousands of threads; this was a **targeted review** of the referenced
  article and relevant redstone discussions, not an exhaustive review of
  every page on that website.
