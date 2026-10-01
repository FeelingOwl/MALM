package com.feelingowl.malm.resolve;

import com.feelingowl.malm.layer.AreaLayer;

import javax.annotation.Nullable;

/**
 * The highest-priority matching structure layer and biome layer at a position. Either can be null.
 * The structure layer, when present, is the active one. The biome layer then only supplies level fields the
 * structure layer left out.
 *
 * @param instanceSeed identifies the structure instance (world seed + layer + structure + start chunk). Random-range
 *                     structure layers roll their level window from it, so each monument gets its own stable range.
 *                     0 when there's no structure layer.
 */
public record ActiveLayers(@Nullable AreaLayer structure, @Nullable AreaLayer biome, long instanceSeed) {

    public static final ActiveLayers NONE = new ActiveLayers(null, null, 0L);

    public boolean isEmpty() {
        return structure == null && biome == null;
    }

    @Nullable
    public AreaLayer active() {
        return structure != null ? structure : biome;
    }

    /** True when the active layer rolls a per-instance level window. */
    public boolean isRandomRange() {
        return structure != null && structure.settings().randomRangeIncrements != null;
    }
}
