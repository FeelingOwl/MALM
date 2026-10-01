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

    private record CacheKey(ResourceLocation dimension, String structure, String biome, long instance) {
    }

    private static final int MAX_CACHE_SIZE = 4096;

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
        StructureMatch structure = findStructureLayer(level, pos, dim, biome);
        AreaLayer biomeLayer = findBiomeLayer(dim, biome);
        if (structure == null) {
            return new ActiveLayers(null, biomeLayer, 0L);
        }
        return new ActiveLayers(structure.layer(), biomeLayer, instanceSeed(level, structure));
    }

    private record StructureMatch(AreaLayer layer, ResourceLocation structureId, StructureStart start) {
    }

    /** Stable per structure instance: same world, layer and structure start always give the same seed. */
    private static long instanceSeed(ServerLevel level, StructureMatch match) {
        long seed = level.getSeed();
        seed = seed * 31 + match.layer().id().hashCode();
        seed = seed * 31 + match.structureId().hashCode();
        seed = seed * 31 + match.start().getChunkPos().toLong();
        return seed == 0 ? 1 : seed; // 0 means "no structure instance"
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
    private static StructureMatch findStructureLayer(ServerLevel level, BlockPos pos, ResourceLocation dim, Holder<Biome> biome) {
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
                Holder<Structure> holder = registry.wrapAsHolder(ref.getKey());
                if (ref.getValue().isEmpty() || !layer.targets().matches(holder)) {
                    continue;
                }
                StructureStart start = layer.match() == AreaLayer.StructureMatch.PIECES
                        ? level.structureManager().getStructureWithPieceAt(pos, ref.getKey())
                        : level.structureManager().getStructureAt(pos, ref.getKey());
                if (start.isValid()) {
                    ResourceLocation id = holder.unwrapKey().map(k -> k.location()).orElse(layer.id());
                    return new StructureMatch(layer, id, start);
                }
            }
        }
        return null;
    }

    // ---- building configs ----

    public static DimensionConfig configFor(Level level, ActiveLayers layers) {
        DimensionConfig base = ExileDB.getDimensionConfig(level);
        if (layers.isEmpty()) {
            return base;
        }
        // Only random-range layers differ per structure instance; every other config is shared by all instances.
        long instance = layers.isRandomRange() ? layers.instanceSeed() : 0L;
        CacheKey key = new CacheKey(level.dimension().location(), idOf(layers.structure()), idOf(layers.biome()), instance);
        CacheEntry entry = CACHE.get(key);
        // Mine and Slash's own dimension configs reload separately from ours; rebuild if the base changed.
        if (entry == null || entry.base() != base) {
            if (CACHE.size() > MAX_CACHE_SIZE) {
                // Random-range entries grow with every structure instance explored. Rebuilding one is cheap.
                CACHE.clear();
            }
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
        tag.putLong("instance", layers.instanceSeed());
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
                LayerRegistry.byId(LayerType.BIOME, tag.getString("biome")),
                tag.getLong("instance"));
    }

    private static String idOf(@Nullable AreaLayer layer) {
        return layer == null ? "" : layer.id().toString();
    }
}
