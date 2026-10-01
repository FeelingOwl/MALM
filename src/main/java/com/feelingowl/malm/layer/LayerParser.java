package com.feelingowl.malm.layer;

import com.feelingowl.malm.Malm;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.robertx22.mine_and_slash.database.data.EntityConfig;
import com.robertx22.mine_and_slash.database.data.MinMax;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads layer JSON by hand, not through Gson defaults: a field the author didn't write has to stay "unset"
 * so level fields can fall back to the layer below instead of silently becoming 1 or 100.
 */
public final class LayerParser {

    private static final Gson GSON = new Gson();

    private static final Set<String> TOP_LEVEL_KEYS = Set.of("targets", "dimensions", "biomes", "priority", "match", "display_name", "announce", "config");
    private static final Set<String> CONFIG_KEYS = Set.of(
            "min_lvl", "max_lvl", "min_lvl_area", "mob_lvl_per_distance", "scale_to_nearest_player",
            "secondary_lvl_range", "exp_multi", "all_drop_multi", "mob_strength_multi", "stats",
            "random_range_min", "random_range_max", "random_range_increments");

    private LayerParser() {
    }

    public static AreaLayer parse(ResourceLocation id, LayerType type, JsonElement element) {
        if (!element.isJsonObject()) {
            throw new JsonParseException("layer file must be a JSON object");
        }
        JsonObject json = element.getAsJsonObject();
        warnUnknownKeys(id, "", json, TOP_LEVEL_KEYS);

        HolderFilter targets = filter(stringList(json, "targets"), type == LayerType.BIOME);
        if (targets.isEmpty()) {
            throw new JsonParseException("'targets' must list at least one id or #tag");
        }

        HolderFilter biomes = HolderFilter.ANY;
        if (json.has("biomes")) {
            if (type != LayerType.STRUCTURE) {
                Malm.LOGGER.warn("[malm] {}: 'biomes' only applies to structure layers (use 'targets' here), ignoring it", id);
            } else {
                biomes = filter(stringList(json, "biomes"), true);
            }
        }

        Set<ResourceLocation> dimensions = new HashSet<>();
        if (json.has("dimensions")) {
            for (String dim : stringList(json, "dimensions")) {
                dimensions.add(location(dim));
            }
        }

        int priority = json.has("priority") ? json.get("priority").getAsInt() : 0;

        AreaLayer.StructureMatch match = AreaLayer.StructureMatch.PIECES;
        if (json.has("match")) {
            if (type != LayerType.STRUCTURE) {
                Malm.LOGGER.warn("[malm] {}: 'match' only applies to structure layers, ignoring it", id);
            } else {
                match = parseMatch(json.get("match").getAsString());
            }
        }

        LayerSettings settings = json.has("config") ? parseSettings(id, json.getAsJsonObject("config")) : new LayerSettings();
        if (type != LayerType.STRUCTURE && settings.randomRangeIncrements != null) {
            // A random window is rolled per structure instance. A biome has no instances to roll for.
            Malm.LOGGER.warn("[malm] {}: random_range_* only applies to structure layers, ignoring it", id);
            settings.randomRangeMin = null;
            settings.randomRangeMax = null;
            settings.randomRangeIncrements = null;
        }

        if (json.has("display_name")) {
            JsonElement name = json.get("display_name");
            // A plain string, or a text component such as {"translate": "..."} / {"text": "...", "color": "aqua"}.
            settings.displayName = name.isJsonPrimitive()
                    ? Component.literal(name.getAsString())
                    : Component.Serializer.fromJson(name);
        }
        if (json.has("announce")) {
            settings.announce = json.get("announce").getAsBoolean();
        }

        return new AreaLayer(id, type, targets, Set.copyOf(dimensions), biomes, priority, match, settings);
    }

    private static HolderFilter filter(List<String> entries, boolean biomeRegistry) {
        Set<ResourceLocation> ids = new HashSet<>();
        List<TagKey<?>> tags = new ArrayList<>();
        for (String entry : entries) {
            if (entry.startsWith("#")) {
                ResourceLocation tag = location(entry.substring(1));
                tags.add(biomeRegistry ? TagKey.create(Registries.BIOME, tag) : TagKey.create(Registries.STRUCTURE, tag));
            } else {
                ids.add(location(entry));
            }
        }
        return new HolderFilter(Set.copyOf(ids), List.copyOf(tags));
    }

    private static LayerSettings parseSettings(ResourceLocation id, JsonObject config) {
        warnUnknownKeys(id, "config.", config, CONFIG_KEYS);
        LayerSettings s = new LayerSettings();

        if (config.has("min_lvl")) s.minLvl = config.get("min_lvl").getAsInt();
        if (config.has("max_lvl")) s.maxLvl = config.get("max_lvl").getAsInt();
        if (config.has("min_lvl_area")) s.minLvlArea = config.get("min_lvl_area").getAsInt();
        if (config.has("mob_lvl_per_distance")) s.mobLvlPerDistance = config.get("mob_lvl_per_distance").getAsInt();
        if (config.has("scale_to_nearest_player")) s.scaleToNearestPlayer = config.get("scale_to_nearest_player").getAsBoolean();
        if (config.has("secondary_lvl_range")) {
            JsonObject range = config.getAsJsonObject("secondary_lvl_range");
            s.secondaryLvlRange = new MinMax(range.get("min").getAsInt(), range.get("max").getAsInt());
        }

        if (config.has("exp_multi")) s.expMulti = config.get("exp_multi").getAsFloat();
        if (config.has("all_drop_multi")) s.allDropMulti = config.get("all_drop_multi").getAsFloat();
        if (config.has("mob_strength_multi")) s.mobStrengthMulti = config.get("mob_strength_multi").getAsFloat();

        if (config.has("stats")) {
            JsonElement stats = config.get("stats");
            // Accept Mine and Slash's own shape {"stats": [...]} as well as a bare array.
            if (stats.isJsonArray()) {
                JsonObject wrapped = new JsonObject();
                wrapped.add("stats", stats);
                stats = wrapped;
            }
            s.stats = GSON.fromJson(stats, EntityConfig.SpecialMobStats.class);
            if (s.stats.stats == null) {
                s.stats.stats = new ArrayList<>();
            }
        }

        if (config.has("random_range_min")) s.randomRangeMin = config.get("random_range_min").getAsInt();
        if (config.has("random_range_max")) s.randomRangeMax = config.get("random_range_max").getAsInt();
        if (config.has("random_range_increments")) s.randomRangeIncrements = config.get("random_range_increments").getAsInt();

        if (s.randomRangeIncrements == null && (s.randomRangeMin != null || s.randomRangeMax != null)) {
            Malm.LOGGER.warn("[malm] {}: random_range_min/max do nothing without config.random_range_increments", id);
        }
        if (s.randomRangeIncrements != null && s.randomRangeIncrements < 0) {
            throw new JsonParseException("config.random_range_increments can't be negative");
        }
        if (s.randomRangeMin != null && s.randomRangeMax != null && s.randomRangeMin > s.randomRangeMax) {
            throw new JsonParseException("config.random_range_min (" + s.randomRangeMin + ") is above config.random_range_max (" + s.randomRangeMax + ")");
        }

        if (s.minLvl != null && s.maxLvl != null && s.minLvl > s.maxLvl) {
            throw new JsonParseException("config.min_lvl (" + s.minLvl + ") is above config.max_lvl (" + s.maxLvl + ")");
        }
        if (s.mobLvlPerDistance != null && s.mobLvlPerDistance <= 0) {
            throw new JsonParseException("config.mob_lvl_per_distance must be above 0");
        }
        return s;
    }

    private static AreaLayer.StructureMatch parseMatch(String value) {
        try {
            return AreaLayer.StructureMatch.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new JsonParseException("'match' must be \"pieces\" or \"bounding_box\", got \"" + value + "\"");
        }
    }

    private static List<String> stringList(JsonObject json, String key) {
        if (!json.has(key)) {
            throw new JsonParseException("missing '" + key + "'");
        }
        JsonElement el = json.get(key);
        List<String> out = new ArrayList<>();
        if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            for (JsonElement e : arr) {
                out.add(e.getAsString());
            }
        } else {
            out.add(el.getAsString());
        }
        return out;
    }

    private static ResourceLocation location(String s) {
        ResourceLocation loc = ResourceLocation.tryParse(s);
        if (loc == null) {
            throw new JsonParseException("invalid id \"" + s + "\"");
        }
        return loc;
    }

    private static void warnUnknownKeys(ResourceLocation id, String prefix, JsonObject json, Set<String> known) {
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            if (!known.contains(entry.getKey())) {
                Malm.LOGGER.warn("[malm] {}: unknown field '{}{}', ignoring it", id, prefix, entry.getKey());
            }
        }
    }
}
