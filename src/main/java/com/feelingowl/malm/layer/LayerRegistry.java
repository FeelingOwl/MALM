package com.feelingowl.malm.layer;

import com.feelingowl.malm.resolve.AreaResolver;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The loaded layers. Replaced wholesale on every datapack reload. */
public final class LayerRegistry {

    /** Highest priority first; ties broken by id so the winner never depends on file load order. */
    private static final Comparator<AreaLayer> ORDER = Comparator.comparingInt(AreaLayer::priority).reversed()
            .thenComparing(l -> l.id().toString());

    private static final Map<LayerType, List<AreaLayer>> LAYERS = new EnumMap<>(LayerType.class);
    private static final Map<LayerType, Map<String, AreaLayer>> BY_ID = new EnumMap<>(LayerType.class);

    static {
        for (LayerType type : LayerType.values()) {
            LAYERS.put(type, List.of());
            BY_ID.put(type, Map.of());
        }
    }

    private LayerRegistry() {
    }

    public static synchronized void set(LayerType type, List<AreaLayer> layers) {
        List<AreaLayer> sorted = layers.stream().sorted(ORDER).toList();
        LAYERS.put(type, sorted);
        BY_ID.put(type, sorted.stream().collect(Collectors.toUnmodifiableMap(l -> l.id().toString(), Function.identity())));
        AreaResolver.clearCache();
    }

    /** Sorted highest priority first. */
    public static List<AreaLayer> get(LayerType type) {
        return LAYERS.get(type);
    }

    @Nullable
    public static AreaLayer byId(LayerType type, String id) {
        return BY_ID.get(type).get(id);
    }

    public static boolean isEmpty() {
        return LAYERS.get(LayerType.BIOME).isEmpty() && LAYERS.get(LayerType.STRUCTURE).isEmpty();
    }
}
