package com.feelingowl.malm.mixin;

import com.feelingowl.malm.resolve.ActiveLayers;
import com.feelingowl.malm.resolve.AreaResolver;
import com.robertx22.mine_and_slash.database.data.DimensionConfig;
import com.robertx22.mine_and_slash.database.data.MinMax;
import com.robertx22.mine_and_slash.uncommon.datasaving.Load;
import com.robertx22.mine_and_slash.uncommon.levels.LevelInfo;
import com.robertx22.mine_and_slash.uncommon.utilityclasses.LevelUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mob level at spawn, the player's area level, and chest/spawner loot level all run through determineLevel.
 * Its single dimension-config lookup is swapped for the layered config at {@code pos}. Instanced maps
 * return before that lookup, so they're untouched.
 */
@Mixin(value = LevelUtils.class, remap = false)
public abstract class LevelUtilsMixin {

    /** Layers found by the redirect, for the RETURN hook of the same call. determineLevel doesn't recurse. */
    @Unique
    private static final ThreadLocal<ActiveLayers> malm$current = new ThreadLocal<>();

    @Inject(method = "determineLevel", at = @At("HEAD"))
    private static void malm$reset(LivingEntity en, Level world, BlockPos pos, Player nearestPlayer, boolean usevariance,
                                   CallbackInfoReturnable<LevelInfo> cir) {
        malm$current.remove();
    }

    @Redirect(method = "determineLevel",
            at = @At(value = "INVOKE", target = "Lcom/robertx22/mine_and_slash/database/registry/ExileDB;getDimensionConfig(Lnet/minecraft/world/level/LevelAccessor;)Lcom/robertx22/mine_and_slash/database/data/DimensionConfig;"))
    private static DimensionConfig malm$layeredConfig(LevelAccessor accessor, LivingEntity en, Level world, BlockPos pos, Player nearestPlayer, boolean usevariance) {
        if (!(world instanceof ServerLevel level)) {
            return AreaResolver.atPosition(accessor, pos);
        }
        ActiveLayers layers = AreaResolver.find(level, pos);
        malm$current.set(layers);
        if (en != null) {
            // An entity is only passed when its spawn level is set. Remember the layers so its exp, loot and stats
            // stay tied to where it spawned.
            AreaResolver.rememberSpawnLayers(en, layers);
        }
        return AreaResolver.configFor(level, layers);
    }

    /**
     * Random-range structures: Mine and Slash would clamp its distance-based level into the rolled window, so almost
     * every mob would sit on one edge. Instead, mobs spread uniformly across the window, and level lookups without a
     * mob (the player's area level, chest loot) use its centre. This overrides scale-to-nearest-player, because the
     * structure's rolled range is the explicit intent.
     */
    @Inject(method = "determineLevel", at = @At("RETURN"))
    private static void malm$randomRangeLevel(LivingEntity en, Level world, BlockPos pos, Player nearestPlayer, boolean usevariance,
                                              CallbackInfoReturnable<LevelInfo> cir) {
        ActiveLayers layers = malm$current.get();
        malm$current.remove();
        if (layers == null || !layers.isRandomRange()) {
            return;
        }
        DimensionConfig config = AreaResolver.configFor(world, layers);
        int min = config.min_lvl;
        int max = config.max_lvl;
        int level = en != null ? min + en.getRandom().nextInt(max - min + 1) : (min + max) / 2;

        LevelInfo info = cir.getReturnValue();
        info.set(LevelInfo.LevelSource.BIOME, level); // Mine and Slash has no "structure" source, BIOME is the unused one
        if (en != null) {
            var entityConfig = Load.Unit(en).getEntityConfig();
            info.capToRange(LevelInfo.LevelSource.ENTITY_CONFIG, new MinMax(entityConfig.min_lvl, entityConfig.max_lvl));
        }
    }
}
