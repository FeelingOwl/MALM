package com.feelingowl.malm.resolve;

import com.feelingowl.malm.layer.AreaLayer;
import com.feelingowl.malm.layer.LayerSettings;
import com.robertx22.mine_and_slash.database.data.DimensionConfig;
import com.robertx22.mine_and_slash.database.data.EntityConfig;
import com.robertx22.mine_and_slash.database.data.MinMax;
import com.robertx22.mine_and_slash.database.data.game_balance_config.GameBalanceConfig;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Random;

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

        if (layers.isRandomRange()) {
            MinMax window = rollWindow(active, c.min_lvl, c.max_lvl, layers.instanceSeed());
            c.min_lvl = window.min;
            c.max_lvl = window.max;
            // A secondary range swaps in for high-level players, which would throw the rolled window away.
            c.secondary_lvl_range = new MinMax(-1, -1);
        }

        c.exp_multi = active.expMulti;
        c.all_drop_multi = active.allDropMulti;
        c.mob_strength_multi = active.mobStrengthMulti;
        c.stats = new EntityConfig.SpecialMobStats();
        c.stats.stats = new ArrayList<>(active.stats.stats);
        return c;
    }

    /**
     * The window is {@code random_range_increments} levels wide, with its low end rolled uniformly so the whole window
     * fits inside [random_range_min, random_range_max]. It never crosses either bound. If the bounds are narrower than
     * the increments, the window is the whole bounded range. Missing bounds fall back to the resolved min_lvl / max_lvl,
     * passed in as {@code fallbackMin} / {@code fallbackMax}.
     *
     * Example: 10-45 with increments 4 can roll [34-38], read as "area level 36, ±2".
     */
    static MinMax rollWindow(LayerSettings s, int fallbackMin, int fallbackMax, long seed) {
        int min = s.randomRangeMin != null ? s.randomRangeMin : fallbackMin;
        int max = s.randomRangeMax != null ? s.randomRangeMax : fallbackMax;
        max = Math.min(max, GameBalanceConfig.get().MAX_LEVEL); // dimension configs default max_lvl to Integer.MAX_VALUE
        min = Math.max(1, Math.min(min, max));

        int width = s.randomRangeIncrements;
        int span = (max - min) - width;
        if (span <= 0) {
            return new MinMax(min, max);
        }
        int low = min + new Random(seed).nextInt(span + 1);
        return new MinMax(low, low + width);
    }

    private static <T> T pick(@Nullable T active, @Nullable T fallback, T base) {
        if (active != null) {
            return active;
        }
        return fallback != null ? fallback : base;
    }
}
