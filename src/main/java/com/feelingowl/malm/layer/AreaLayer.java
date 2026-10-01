package com.feelingowl.malm.layer;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

import java.util.Set;

/**
 * One layer file. Biome and structure layers share this shape.
 *
 * @param targets    what the layer is for: biome ids/tags in malm_biomes, structure ids/tags in malm_structures
 * @param dimensions condition: only in these dimensions (empty means any dimension)
 * @param biomes     condition, structure layers only: only where the biome at the position matches (empty means any biome)
 * @param match      structure layers only: piece-precise or bounding-box containment
 */
public record AreaLayer(ResourceLocation id,
                        LayerType type,
                        HolderFilter targets,
                        Set<ResourceLocation> dimensions,
                        HolderFilter biomes,
                        int priority,
                        StructureMatch match,
                        LayerSettings settings) {

    public enum StructureMatch {
        /** Inside one of the structure's actual pieces. */
        PIECES,
        /** Anywhere inside the structure's overall bounding box. */
        BOUNDING_BOX
    }

    public boolean appliesInDimension(ResourceLocation dimension) {
        return dimensions.isEmpty() || dimensions.contains(dimension);
    }

    public boolean appliesInBiome(Holder<Biome> biome) {
        return biomes.isEmpty() || biomes.matches(biome);
    }
}
