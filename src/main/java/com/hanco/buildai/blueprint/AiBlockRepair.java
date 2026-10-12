package com.hanco.buildai.blueprint;

import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Best-effort recovery for AI-authored states ONLY. A model may invent an
 * optional property (e.g. waterlogged=true on seagrass) or a block variant
 * (e.g. cut_sandstone_stairs). Neither should discard a costly whole design.
 *
 * This is intentionally NOT applied to imported schematics or server uploads:
 * those inputs remain strictly validated by BlockStates.validate.
 */
public final class AiBlockRepair {
    private AiBlockRepair() {}

    public record Result(String block, String warning) {}

    public static Result repair(String raw) {
        try {
            BlockStates.parse(raw);
            return new Result(raw, null);
        } catch (IllegalArgumentException invalid) {
            // Recover only expected invalid AI block/property descriptions.
            // Geometry, malformed JSON, and incompatible packet data still fail.
        }

        int opening = raw.indexOf('[');
        String originalId = opening < 0 ? raw : raw.substring(0, opening);
        Identifier id = Identifier.tryParse(originalId);
        if (id == null) throw new IllegalArgumentException("Invalid AI block identifier: " + originalId);

        List<String> changes = new ArrayList<>();
        String replacementId = originalId;
        if (!Registries.BLOCK.containsId(id)) {
            replacementId = substitute(originalId);
            changes.add("nonexistent block replaced");
        }

        BlockState defaults = Registries.BLOCK.get(Identifier.of(replacementId)).getDefaultState();
        Map<String, String> retained = new LinkedHashMap<>();
        if (opening >= 0) {
            if (!raw.endsWith("]")) throw new IllegalArgumentException("Malformed AI block state: " + raw);
            String properties = raw.substring(opening + 1, raw.length() - 1);
            for (String field : properties.split(",")) {
                if (field.isBlank()) continue;
                String[] kv = field.split("=", 2);
                if (kv.length != 2 || kv[0].isBlank() || kv[1].isBlank()) {
                    changes.add("malformed property removed: " + field);
                    continue;
                }
                Property<?> property = defaults.getBlock().getStateManager().getProperty(kv[0]);
                if (property == null) {
                    changes.add("unsupported property removed: " + field);
                } else if (!validValue(property, kv[1])) {
                    changes.add("invalid value reset to default: " + field);
                } else if (retained.containsKey(kv[0])) {
                    changes.add("duplicate property removed: " + field);
                } else {
                    retained.put(kv[0], kv[1]);
                }
            }
        }

        StringBuilder repaired = new StringBuilder(replacementId);
        if (!retained.isEmpty()) {
            repaired.append('[');
            for (Map.Entry<String, String> entry : retained.entrySet()) {
                if (repaired.charAt(repaired.length() - 1) != '[') repaired.append(',');
                repaired.append(entry.getKey()).append('=').append(entry.getValue());
            }
            repaired.append(']');
        }

        // A bad repair is still a real error, never an unchecked state.
        BlockStates.parse(repaired.toString());
        String warning = "AI block auto-repaired: " + raw + " -> " + repaired +
            " (" + String.join("; ", changes) + "). Inspect its placement.";
        return new Result(repaired.toString(), warning);
    }

    private static <T extends Comparable<T>> boolean validValue(Property<T> property, String value) {
        return property.parse(value).isPresent();
    }

    private static boolean exists(String block) {
        Identifier id = Identifier.tryParse(block);
        return id != null && Registries.BLOCK.containsId(id);
    }

    /**
     * Preserve geometry before appearance. When a sandstone stair variant is
     * invented, another stair is safer than an opaque full cube. If no precise
     * counterpart exists, prefer a real structural block over leaving a hole.
     */
    private static String substitute(String wanted) {
        return suggestAlternative(wanted,AiBlockRepair::exists);
    }

    /** Pure shape/material choice, injectable for unit tests outside Fabric. */
    static String suggestAlternative(String wanted,java.util.function.Predicate<String> exists) {
        if (!wanted.startsWith("minecraft:")) return "minecraft:stone_bricks";
        String path = wanted.substring("minecraft:".length());

        // Invented cut-sandstone variants are common in model output.
        String withoutCut = "minecraft:" + path.replace("cut_sandstone", "sandstone")
            .replace("cut_red_sandstone", "red_sandstone");
        if (!withoutCut.equals(wanted) && exists.test(withoutCut)) return withoutCut;

        String shape = null;
        if (path.endsWith("_stairs")) {
            shape = path.contains("red_sandstone") ? "red_sandstone_stairs"
                : path.contains("sandstone") ? "sandstone_stairs" : "stone_brick_stairs";
        } else if (path.endsWith("_slab")) {
            shape = path.contains("red_sandstone") ? "red_sandstone_slab"
                : path.contains("sandstone") ? "sandstone_slab" : "stone_brick_slab";
        } else if (path.endsWith("_wall")) {
            shape = "stone_brick_wall";
        } else if (path.endsWith("_fence_gate")) {
            shape = "oak_fence_gate";
        } else if (path.endsWith("_fence")) {
            shape = "oak_fence";
        } else if (path.endsWith("_trapdoor")) {
            shape = "oak_trapdoor";
        } else if (path.endsWith("_door")) {
            shape = "oak_door";
        } else if (path.endsWith("_pane")) {
            shape = "glass_pane";
        } else if (path.endsWith("_leaves")) {
            shape = "oak_leaves";
        } else if (path.endsWith("_log")) {
            shape = "oak_log";
        } else if (path.endsWith("_planks")) {
            shape = "oak_planks";
        } else if (path.endsWith("_pressure_plate")) {
            shape = "stone_pressure_plate";
        } else if (path.endsWith("_button")) {
            shape = "stone_button";
        } else if (path.endsWith("_sign")) {
            shape = "oak_sign";
        }
        if (shape != null && exists.test("minecraft:" + shape)) return "minecraft:" + shape;

        // Fall back to the nearest existing base material, e.g. an invented
        // cut_sandstone_column -> cut_sandstone, without changing location.
        String stem = path;
        while (stem.contains("_")) {
            stem = stem.substring(0, stem.lastIndexOf('_'));
            if (exists.test("minecraft:" + stem)) return "minecraft:" + stem;
        }
        return "minecraft:stone_bricks";
    }
}
