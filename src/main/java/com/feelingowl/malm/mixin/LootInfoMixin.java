package com.feelingowl.malm.mixin;

import com.feelingowl.malm.resolve.AreaResolver;
import com.robertx22.mine_and_slash.database.data.DimensionConfig;
import com.robertx22.mine_and_slash.database.registry.ExileDB;
import com.robertx22.mine_and_slash.loot.LootInfo;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** {@code all_drop_multi}: from the killed mob's spawn layer, or from the loot position (chests, spawners, blocks). */
@Mixin(value = LootInfo.class, remap = false)
public abstract class LootInfoMixin {

    @Redirect(method = "gatherLootMultipliers",
            at = @At(value = "INVOKE", target = "Lcom/robertx22/mine_and_slash/database/registry/ExileDB;getDimensionConfig(Lnet/minecraft/world/level/LevelAccessor;)Lcom/robertx22/mine_and_slash/database/data/DimensionConfig;"))
    private DimensionConfig malm$layeredLootConfig(LevelAccessor world) {
        LootInfo self = (LootInfo) (Object) this;
        if (self.mobKilled != null) {
            return AreaResolver.forEntity(self.mobKilled);
        }
        if (self.pos != null) {
            return AreaResolver.atPosition(world, self.pos);
        }
        return ExileDB.getDimensionConfig(world);
    }
}
