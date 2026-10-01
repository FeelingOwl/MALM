package com.feelingowl.malm;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server config: {@code <world>/serverconfig/malm-server.toml}. */
public final class MalmConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue SHOW_AREA_TITLES;
    public static final ForgeConfigSpec.BooleanValue SHOW_TITLE_ON_LEAVE;
    public static final ForgeConfigSpec.BooleanValue LEVEL_RANGE_AS_SUBTITLE;
    public static final ForgeConfigSpec.IntValue CHECK_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue REANNOUNCE_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.IntValue FADE_IN_TICKS;
    public static final ForgeConfigSpec.IntValue STAY_TICKS;
    public static final ForgeConfigSpec.IntValue FADE_OUT_TICKS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("area_titles");
        SHOW_AREA_TITLES = b.comment("Show a \"Name [min-max]\" title when the active layer at a player's position changes.")
                .define("show_area_titles", true);
        SHOW_TITLE_ON_LEAVE = b.comment("Also show a title when leaving every layer, back to the dimension's own level range.")
                .define("show_title_on_leave", true);
        LEVEL_RANGE_AS_SUBTITLE = b.comment("Put the [min-max] level range in the subtitle instead of on the title line.")
                .define("level_range_as_subtitle", false);
        CHECK_INTERVAL_TICKS = b.comment("How often each player's position is checked, in ticks.")
                .defineInRange("check_interval_ticks", 10, 1, 200);
        REANNOUNCE_COOLDOWN_SECONDS = b.comment("Don't show the same area's title again within this many seconds. Stops repeats when walking along a border.")
                .defineInRange("reannounce_cooldown_seconds", 30, 0, 3600);
        FADE_IN_TICKS = b.defineInRange("fade_in_ticks", 10, 0, 200);
        STAY_TICKS = b.defineInRange("stay_ticks", 50, 0, 600);
        FADE_OUT_TICKS = b.defineInRange("fade_out_ticks", 15, 0, 200);
        b.pop();
        SPEC = b.build();
    }

    private MalmConfig() {
    }
}
