package com.feelingowl.malm.command;

import com.feelingowl.malm.layer.AreaLayer;
import com.feelingowl.malm.layer.LayerRegistry;
import com.feelingowl.malm.layer.LayerType;
import com.feelingowl.malm.resolve.ActiveLayers;
import com.feelingowl.malm.resolve.AreaResolver;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.robertx22.library_of_exile.dimension.MapDimensions;
import com.robertx22.mine_and_slash.database.data.DimensionConfig;
import com.robertx22.mine_and_slash.uncommon.utilityclasses.LevelUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.List;
import java.util.stream.Collectors;

/** {@code /malm here} explains which layers apply where you stand. {@code /malm list} lists what's loaded. */
public final class MalmCommand {

    private MalmCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("malm")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("here").executes(MalmCommand::here))
                .then(Commands.literal("list").executes(MalmCommand::list)));
    }

    private static int here(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        BlockPos pos = BlockPos.containing(src.getPosition());
        ServerPlayer player = src.getPlayer();

        ActiveLayers layers = AreaResolver.find(level, pos);
        DimensionConfig config = AreaResolver.configFor(level, layers);

        var biomeKey = level.getBiome(pos).unwrapKey().map(k -> k.location().toString()).orElse("?");
        var structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<String> structures = level.structureManager().getAllStructuresAt(pos).keySet().stream()
                .filter(s -> level.structureManager().getStructureWithPieceAt(pos, s).isValid())
                .map(s -> String.valueOf(structureRegistry.getKey(s)))
                .collect(Collectors.toList());

        send(src, "§6[MALM] §fDimension: §e" + level.dimension().location()
                + (MapDimensions.isMap(level) ? " §7(instanced map, layers disabled)" : ""));
        send(src, "§fBiome: §e" + biomeKey);
        send(src, "§fStructures here: §e" + (structures.isEmpty() ? "none" : String.join(", ", structures)));
        send(src, "§fStructure layer: " + describe(layers.structure()));
        send(src, "§fBiome layer: " + describe(layers.biome()));
        send(src, "§fActive: §a" + (layers.active() == null ? "dimension config (" + config.GUID() + ")" : layers.active().id()));
        if (layers.isRandomRange()) {
            var s = layers.structure().settings();
            send(src, "§fLevels: §e" + config.min_lvl + "-" + config.max_lvl + " §7(rolled for this "
                    + "structure instance: " + (s.randomRangeIncrements) + " wide, inside random_range "
                    + (s.randomRangeMin != null ? s.randomRangeMin : "fallback") + "-"
                    + (s.randomRangeMax != null ? s.randomRangeMax : "fallback") + "; mobs spread across it)");
        } else {
            send(src, "§fLevels: §e" + config.min_lvl + "-" + config.max_lvl
                    + " §7(min_lvl_area " + config.min_lvl_area + ", per distance " + config.mob_lvl_per_distance
                    + ", scale to player " + config.scale_to_nearest_player + ")");
        }
        send(src, "§fMultipliers: §eexp x" + config.exp_multi + ", loot x" + config.all_drop_multi + ", mob strength x" + config.mob_strength_multi
                + " §7(" + config.stats.stats.size() + " extra stat(s))");
        if (player != null) {
            send(src, "§fArea level for you: §e" + LevelUtils.determineLevel(null, level, pos, player, false).getLevel());
        }
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        for (LayerType type : LayerType.values()) {
            List<AreaLayer> layers = LayerRegistry.get(type);
            send(ctx.getSource(), "§6[MALM] §f" + type.folder + ": §e" + layers.size());
            for (AreaLayer layer : layers) {
                send(ctx.getSource(), "§7 - " + layer.id() + " (priority " + layer.priority() + ")");
            }
        }
        return 1;
    }

    private static String describe(@Nullable AreaLayer layer) {
        return layer == null ? "§7none" : "§e" + layer.id() + " §7(priority " + layer.priority() + ")";
    }

    private static void send(CommandSourceStack src, String text) {
        src.sendSuccess(() -> Component.literal(text), false);
    }
}
