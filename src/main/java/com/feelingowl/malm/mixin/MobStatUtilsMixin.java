package com.feelingowl.malm.mixin;

import com.feelingowl.malm.resolve.AreaResolver;
import com.robertx22.mine_and_slash.capability.entity.EntityData;
import com.robertx22.mine_and_slash.database.data.DimensionConfig;
import com.robertx22.mine_and_slash.uncommon.stat_calculation.MobStatUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** {@code mob_strength_multi} and extra {@code stats} come from the layer the mob spawned in. */
@Mixin(value = MobStatUtils.class, remap = false)
public abstract class MobStatUtilsMixin {

    private static final String GET_DIMENSION_CONFIG =
            "Lcom/robertx22/mine_and_slash/database/registry/ExileDB;getDimensionConfig(Lnet/minecraft/world/level/LevelAccessor;)Lcom/robertx22/mine_and_slash/database/data/DimensionConfig;";

    @Redirect(method = "getWorldMultiplierStats", at = @At(value = "INVOKE", target = GET_DIMENSION_CONFIG))
    private static DimensionConfig malm$strengthConfig(LevelAccessor world, LivingEntity en) {
        return AreaResolver.forEntity(en);
    }

    @Redirect(method = "getMobConfigStats", at = @At(value = "INVOKE", target = GET_DIMENSION_CONFIG))
    private static DimensionConfig malm$statsConfig(LevelAccessor world, LivingEntity entity, EntityData unitdata) {
        return AreaResolver.forEntity(entity);
    }
}
