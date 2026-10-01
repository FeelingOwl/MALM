package com.feelingowl.malm.mixin;

import com.feelingowl.malm.resolve.ActiveLayers;
import com.feelingowl.malm.resolve.AreaResolver;
import com.robertx22.mine_and_slash.database.data.DimensionConfig;
import com.robertx22.mine_and_slash.uncommon.utilityclasses.LevelUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mob level at spawn, the player's area level, and chest/spawner loot level all run through determineLevel.
 * Its single dimension-config lookup is swapped for the layered config at {@code pos}. Instanced maps
 * return before that lookup, so they're untouched.
 */
@Mixin(value = LevelUtils.class, remap = false)
public abstract class LevelUtilsMixin {

    @Redirect(method = "determineLevel",
            at = @At(value = "INVOKE", target = "Lcom/robertx22/mine_and_slash/database/registry/ExileDB;getDimensionConfig(Lnet/minecraft/world/level/LevelAccessor;)Lcom/robertx22/mine_and_slash/database/data/DimensionConfig;"))
    private static DimensionConfig malm$layeredConfig(LevelAccessor accessor, LivingEntity en, Level world, BlockPos pos, Player nearestPlayer, boolean usevariance) {
        if (!(world instanceof ServerLevel level)) {
            return AreaResolver.atPosition(accessor, pos);
        }
        ActiveLayers layers = AreaResolver.find(level, pos);
        if (en != null) {
            // An entity is only passed when its spawn level is set. Remember the layers so its exp, loot and stats
            // stay tied to where it spawned.
            AreaResolver.rememberSpawnLayers(en, layers);
        }
        return AreaResolver.configFor(level, layers);
    }
}
