package com.feelingowl.malm.resolve;

import com.feelingowl.malm.layer.AreaLayer;
import com.feelingowl.malm.layer.LayerSettings;
import com.robertx22.mine_and_slash.database.data.DimensionConfig;
import com.robertx22.mine_and_slash.database.data.EntityConfig;
import com.robertx22.mine_and_slash.database.data.MinMax;

import javax.annotation.Nullable;
import java.util.ArrayList;

/** Builds the DimensionConfig that Mine and Slash is handed for a position. The registry entry is never mutated. */
public final class EffectiveConfig {

    private EffectiveConfig() {
    }

    /**
     * Structure > Biome > Dimension. Multipliers and stats come from the active layer only and never stack.
     * Level fields the active layer leaves out fall back to the next layer down.
     */
    public static DimensionConfig build(DimensionConfig base, ActiveLayers layers) {
        AreaLayer activeLayer = layers.active();
        if (activeLayer == null) {
            return base;
        }
        LayerSettings active = activeLayer.settings();
        LayerSettings fallback = layers.structure() != null && layers.biome() != null ? layers.biome().settings() : null;

        DimensionConfig c = new DimensionConfig();
        c.dimension_id = base.dimension_id;
        c.mob_tier = base.mob_tier;

        c.min_lvl = pick(active.minLvl, fallback == null ? null : fallback.minLvl, base.min_lvl);
        c.max_lvl = pick(active.maxLvl, fallback == null ? null : fallback.maxLvl, base.max_lvl);
        c.min_lvl_area = pick(active.minLvlArea, fallback == null ? null : fallback.minLvlArea, base.min_lvl_area);
        c.mob_lvl_per_distance = pick(active.mobLvlPerDistance, fallback == null ? null : fallback.mobLvlPerDistance, base.mob_lvl_per_distance);
        c.scale_to_nearest_player = pick(active.scaleToNearestPlayer, fallback == null ? null : fallback.scaleToNearestPlayer, base.scale_to_nearest_player);
        MinMax secondary = pick(active.secondaryLvlRange, fallback == null ? null : fallback.secondaryLvlRange, base.secondary_lvl_range);
        c.secondary_lvl_range = new MinMax(secondary.min, secondary.max);

        // A structure's min_lvl can land above a max_lvl inherited from below. MinMax.capNumber
        // misbehaves on an inverted range, so the range is widened up to the explicit min.
        if (c.max_lvl < c.min_lvl) {
            c.max_lvl = c.min_lvl;
        }

        c.exp_multi = active.expMulti;
        c.all_drop_multi = active.allDropMulti;
        c.mob_strength_multi = active.mobStrengthMulti;
        c.stats = new EntityConfig.SpecialMobStats();
        c.stats.stats = new ArrayList<>(active.stats.stats);
        return c;
    }

    private static <T> T pick(@Nullable T active, @Nullable T fallback, T base) {
        if (active != null) {
            return active;
        }
        return fallback != null ? fallback : base;
    }
}
