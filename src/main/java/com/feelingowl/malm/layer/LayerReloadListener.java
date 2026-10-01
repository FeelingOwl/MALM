package com.feelingowl.malm.layer;

import com.feelingowl.malm.Malm;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Loads {@code data/<ns>/malm_biomes/} or {@code data/<ns>/malm_structures/}. Server side only. */
public class LayerReloadListener extends SimpleJsonResourceReloadListener {

    private final LayerType type;

    public LayerReloadListener(LayerType type) {
        super(new Gson(), type.folder);
        this.type = type;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        List<AreaLayer> layers = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
            try {
                layers.add(LayerParser.parse(entry.getKey(), type, entry.getValue()));
            } catch (Exception e) {
                Malm.LOGGER.error("[malm] Skipping {} layer {}: {}", type.folder, entry.getKey(), e.getMessage());
            }
        }
        LayerRegistry.set(type, layers);
        Malm.LOGGER.info("[malm] Loaded {} {} layer(s)", layers.size(), type.folder);
    }
}
