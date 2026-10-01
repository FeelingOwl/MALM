package com.feelingowl.malm.resolve;

import com.feelingowl.malm.Malm;
import com.feelingowl.malm.layer.AreaLayer;
import com.feelingowl.malm.layer.LayerRegistry;
import com.feelingowl.malm.layer.LayerType;
import com.robertx22.library_of_exile.dimension.MapDimensions;
import com.robertx22.mine_and_slash.database.data.DimensionConfig;
import com.robertx22.mine_and_slash.database.registry.ExileDB;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Answers "which layers apply here" and "what DimensionConfig should Mine and Slash use here". */
public final class AreaResolver {

    /** Entity persistent-data key holding the layers a mob spawned in. */
    public static final String SPAWN_LAYERS_KEY = Malm.MODID + ":layers";

    private record CacheKey(ResourceLocation dimension, String structure, String biome) {
    }

    private record CacheEntry(DimensionConfig base, DimensionConfig config) {
    }

    private static final Map<CacheKey, CacheEntry> CACHE = new ConcurrentHashMap<>();

    private AreaResolver() {
    }

    public static void clearCache() {
        CACHE.clear();
    }

    // ---- finding layers ----

    public static ActiveLayers find(ServerLevel level, BlockPos pos) {
        if (LayerRegistry.isEmpty() || MapDimensions.isMap(level)) {
            // Instanced maps (Dungeon Realm, Harvest, Obelisks) run their own level logic, so layers stay out of them.
            return ActiveLayers.NONE;
        }
        ResourceLocation dim = level.dimension().location();
        Holder<Biome> biome = level.getBiome(pos);
        return new ActiveLayers(findStructureLayer(level, pos, dim, biome), findBiomeLayer(dim, biome));
    }

    @Nullable
    private static AreaLayer findBiomeLayer(ResourceLocation dim, Holder<Biome> biome) {
        for (AreaLayer layer : LayerRegistry.get(LayerType.BIOME)) {
            if (layer.appliesInDimension(dim) && layer.targets().matches(biome)) {
                return layer;
            }
        }
        return null;
    }

    @Nullable
    private static AreaLayer findStructureLayer(ServerLevel level, BlockPos pos, ResourceLocation dim, Holder<Biome> biome) {
        List<AreaLayer> layers = LayerRegistry.get(LayerType.STRUCTURE);
        if (layers.isEmpty()) {
            return null;
        }
        // getChunkNow never loads or generates. Structure references live in the chunk being checked,
        // so there's nothing to find in a chunk that isn't loaded yet.
        LevelChunk chunk = level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
        if (chunk == null) {
            return null;
        }
        Map<Structure, LongSet> refs = chunk.getAllReferences();
        if (refs.isEmpty()) {
            return null;
        }
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);

        for (AreaLayer layer : layers) {
            // Conditions first: they're cheap, and the containment check below is not.
            if (!layer.appliesInDimension(dim) || !layer.appliesInBiome(biome)) {
                continue;
            }
            for (Map.Entry<Structure, LongSet> ref : refs.entrySet()) {
                if (ref.getValue().isEmpty() || !layer.targets().matches(registry.wrapAsHolder(ref.getKey()))) {
                    continue;
                }
                if (contains(level, pos, ref.getKey(), layer.match())) {
                    return layer;
                }
            }
        }
        return null;
    }

    private static boolean contains(ServerLevel level, BlockPos pos, Structure structure, AreaLayer.StructureMatch match) {
        StructureStart start = match == AreaLayer.StructureMatch.PIECES
                ? level.structureManager().getStructureWithPieceAt(pos, structure)
                : level.structureManager().getStructureAt(pos, structure);
        return start.isValid();
    }

    // ---- building configs ----

    public static DimensionConfig configFor(Level level, ActiveLayers layers) {
        DimensionConfig base = ExileDB.getDimensionConfig(level);
        if (layers.isEmpty()) {
            return base;
        }
        CacheKey key = new CacheKey(level.dimension().location(), idOf(layers.structure()), idOf(layers.biome()));
        CacheEntry entry = CACHE.get(key);
        // Mine and Slash's own dimension configs reload separately from ours; rebuild if the base changed.
        if (entry == null || entry.base() != base) {
            entry = new CacheEntry(base, EffectiveConfig.build(base, layers));
            CACHE.put(key, entry);
        }
        return entry.config();
    }

    /** For anything positional: chest loot, the player's area level, level lookups without an entity. */
    public static DimensionConfig atPosition(LevelAccessor world, BlockPos pos) {
        if (world instanceof ServerLevel level) {
            return configFor(level, find(level, pos));
        }
        return ExileDB.getDimensionConfig(world);
    }

    /** For a mob: the layers it spawned in, falling back to where it is now for mobs from before this mod. */
    public static DimensionConfig forEntity(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return ExileDB.getDimensionConfig(entity.level());
        }
        ActiveLayers stored = readSpawnLayers(entity);
        if (stored != null) {
            return configFor(level, stored);
        }
        return atPosition(level, entity.blockPosition());
    }

    // ---- remembering spawn layers ----

    public static void rememberSpawnLayers(LivingEntity entity, ActiveLayers layers) {
        CompoundTag tag = new CompoundTag();
        tag.putString("structure", idOf(layers.structure()));
        tag.putString("biome", idOf(layers.biome()));
        entity.getPersistentData().put(SPAWN_LAYERS_KEY, tag);
    }

    /** Null when the entity has no record. A layer deleted by a reload since then reads as "no layer". */
    @Nullable
    public static ActiveLayers readSpawnLayers(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(SPAWN_LAYERS_KEY, Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag tag = data.getCompound(SPAWN_LAYERS_KEY);
        return new ActiveLayers(
                LayerRegistry.byId(LayerType.STRUCTURE, tag.getString("structure")),
                LayerRegistry.byId(LayerType.BIOME, tag.getString("biome")));
    }

    private static String idOf(@Nullable AreaLayer layer) {
        return layer == null ? "" : layer.id().toString();
    }
}
