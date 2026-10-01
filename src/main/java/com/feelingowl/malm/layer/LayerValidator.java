package com.feelingowl.malm.layer;

import com.feelingowl.malm.Malm;
import com.robertx22.mine_and_slash.database.OptScaleExactStat;
import com.robertx22.mine_and_slash.database.registry.ExileDB;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.util.Optional;

/**
 * Warns about ids that don't exist. Runs once tags are bound (TagsUpdatedEvent). Before that, #tags can't be checked.
 * Warnings only: a layer for a mod that isn't installed is harmless and simply never matches.
 */
public final class LayerValidator {

    private LayerValidator() {
    }

    public static void validate(RegistryAccess access) {
        Registry<?> biomes = access.registryOrThrow(Registries.BIOME);
        Registry<?> structures = access.registryOrThrow(Registries.STRUCTURE);
        for (LayerType type : LayerType.values()) {
            for (AreaLayer layer : LayerRegistry.get(type)) {
                checkFilter(layer, "targets", layer.targets(), type == LayerType.BIOME ? biomes : structures);
                checkFilter(layer, "biomes", layer.biomes(), biomes);
                check(layer, access);
            }
        }
    }

    private static void checkFilter(AreaLayer layer, String field, HolderFilter filter, Registry<?> registry) {
        for (ResourceLocation id : filter.ids()) {
            if (!registry.containsKey(id)) {
                Malm.LOGGER.warn("[malm] {}: {} has unknown id '{}'", layer.id(), field, id);
            }
        }
        for (TagKey<?> tag : filter.tags()) {
            if (!hasTag(registry, tag)) {
                Malm.LOGGER.warn("[malm] {}: {} tag '#{}' doesn't exist or is empty", layer.id(), field, tag.location());
            }
        }
    }

    private static void check(AreaLayer layer, RegistryAccess access) {
        Registry<?> dimensions = access.registryOrThrow(Registries.LEVEL_STEM);
        for (ResourceLocation dim : layer.dimensions()) {
            if (!dimensions.containsKey(dim)) {
                Malm.LOGGER.warn("[malm] {}: unknown dimension '{}'", layer.id(), dim);
            }
        }
        for (OptScaleExactStat stat : layer.settings().stats.stats) {
            if (!ExileDB.Stats().isRegistered(stat.stat)) {
                Malm.LOGGER.warn("[malm] {}: unknown Mine and Slash stat '{}'", layer.id(), stat.stat);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean hasTag(Registry registry, TagKey tag) {
        Optional<HolderSet.Named<?>> set = registry.getTag(tag);
        return set.isPresent() && set.get().size() > 0;
    }
}
