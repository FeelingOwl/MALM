package com.feelingowl.malm.layer;

public enum LayerType {
    BIOME("malm_biomes"),
    STRUCTURE("malm_structures");

    /** Datapack folder under {@code data/<namespace>/}. The folder alone decides the layer type. */
    public final String folder;

    LayerType(String folder) {
        this.folder = folder;
    }
}
