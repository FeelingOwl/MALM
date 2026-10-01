package com.feelingowl.malm.mixin;

import com.feelingowl.malm.resolve.AreaResolver;
import com.robertx22.mine_and_slash.capability.entity.EntityData;
import com.robertx22.mine_and_slash.database.data.DimensionConfig;
import com.robertx22.mine_and_slash.database.data.mercenary.entity.MercenaryEntity;
import com.robertx22.mine_and_slash.event_hooks.my_events.OnMobDeathDrops;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Kill exp uses the {@code exp_multi} of the layer the victim spawned in. */
@Mixin(value = OnMobDeathDrops.class, remap = false)
public abstract class OnMobDeathDropsMixin {

    @Redirect(method = "GiveExp",
            at = @At(value = "INVOKE", target = "Lcom/robertx22/mine_and_slash/database/registry/ExileDB;getDimensionConfig(Lnet/minecraft/world/level/LevelAccessor;)Lcom/robertx22/mine_and_slash/database/data/DimensionConfig;"))
    private static DimensionConfig malm$victimLayerConfig(LevelAccessor world, LivingEntity victim, Player killer, EntityData killerData,
                                                          EntityData mobData, float multi, MercenaryEntity mercKiller) {
        return AreaResolver.forEntity(victim);
    }
}
