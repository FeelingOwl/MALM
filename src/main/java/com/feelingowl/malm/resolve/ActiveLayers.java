package com.feelingowl.malm.resolve;

import com.feelingowl.malm.layer.AreaLayer;

import javax.annotation.Nullable;

/**
 * The highest-priority matching structure layer and biome layer at a position. Either can be null.
 * The structure layer, when present, is the active one. The biome layer then only supplies level fields the
 * structure layer left out.
 */
public record ActiveLayers(@Nullable AreaLayer structure, @Nullable AreaLayer biome) {

    public static final ActiveLayers NONE = new ActiveLayers(null, null);

    public boolean isEmpty() {
        return structure == null && biome == null;
    }

    @Nullable
    public AreaLayer active() {
        return structure != null ? structure : biome;
    }
}
