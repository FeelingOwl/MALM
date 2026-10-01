package com.feelingowl.malm.layer;

import com.robertx22.mine_and_slash.database.data.EntityConfig;
import com.robertx22.mine_and_slash.database.data.MinMax;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/**
 * The {@code config} block of a layer file, using Mine and Slash's DimensionConfig field names.
 *
 * Level fields are nullable: a null means the layer didn't set it, and the value falls back to the next
 * layer down (structure -> biome -> dimension). Multipliers and stats never fall back. They belong to this
 * layer alone, and a missing one is 1.0 / no extra stats.
 */
public class LayerSettings {

    // ---- presentation (top-level layer fields, not part of the DimensionConfig "config" block) ----

    /** Shown in the area title. Null means it's derived from the layer file name. */
    @Nullable public Component displayName;
    /** Whether entering this layer shows a title. */
    public boolean announce = true;

    // ---- DimensionConfig fields ----

    @Nullable public Integer minLvl;
    @Nullable public Integer maxLvl;
    @Nullable public Integer minLvlArea;
    @Nullable public Integer mobLvlPerDistance;
    @Nullable public Boolean scaleToNearestPlayer;
    @Nullable public MinMax secondaryLvlRange;

    // ---- random range (structure layers only) ----
    // Each structure instance rolls a window `randomRangeIncrements` levels wide, somewhere inside
    // [randomRangeMin, randomRangeMax]. Setting the increments turns the mode on. A missing min or max falls back to the
    // resolved min_lvl / max_lvl chain (this layer, then biome, then dimension).

    @Nullable public Integer randomRangeMin;
    @Nullable public Integer randomRangeMax;
    @Nullable public Integer randomRangeIncrements;

    public float expMulti = 1F;
    public float allDropMulti = 1F;
    public float mobStrengthMulti = 1F;
    public EntityConfig.SpecialMobStats stats = new EntityConfig.SpecialMobStats();
}
