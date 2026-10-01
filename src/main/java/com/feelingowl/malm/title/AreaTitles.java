package com.feelingowl.malm.title;

import com.feelingowl.malm.MalmConfig;
import com.feelingowl.malm.layer.AreaLayer;
import com.feelingowl.malm.resolve.ActiveLayers;
import com.feelingowl.malm.resolve.AreaResolver;
import com.robertx22.library_of_exile.dimension.MapDimensions;
import com.robertx22.mine_and_slash.database.data.DimensionConfig;
import com.robertx22.mine_and_slash.database.data.MinMax;
import com.robertx22.mine_and_slash.database.data.game_balance_config.GameBalanceConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Shows "Name [min-max]" when the active layer at a player's position changes: entering a structure or biome
 * layer, or leaving all layers back to the dimension's own range. Titles are vanilla packets, so this is server
 * side only and players don't need anything extra installed.
 */
public final class AreaTitles {

    private static final class State {
        /** Area the player was last announced (or silently baselined) in. */
        @Nullable String current;
        /** A new area seen on the previous check. It must hold for two checks before it's announced. */
        @Nullable String pending;
        /** Area key -> game time it was last announced, for the re-announce cooldown. */
        final Map<String, Long> lastShown = new HashMap<>();
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private AreaTitles() {
    }

    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (!MalmConfig.SHOW_AREA_TITLES.get() || player.tickCount % MalmConfig.CHECK_INTERVAL_TICKS.get() != 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        State state = STATES.computeIfAbsent(player.getUUID(), u -> new State());

        if (MapDimensions.isMap(level)) {
            // Instanced maps announce themselves. Forget the area so coming back out counts as a change.
            state.current = "map";
            state.pending = null;
            return;
        }

        ActiveLayers layers = AreaResolver.find(level, player.blockPosition());
        String key = level.dimension().location() + "|" + (layers.active() == null ? "" : layers.active().id());

        if (state.current == null) {
            // First check after login: take the current area as the baseline instead of announcing it.
            state.current = key;
            return;
        }
        if (key.equals(state.current)) {
            state.pending = null;
            return;
        }
        // Debounce: a pieces-precise structure border can flip for a single check while walking through gaps.
        if (!key.equals(state.pending)) {
            state.pending = key;
            return;
        }
        state.current = key;
        state.pending = null;
        announce(player, level, layers, key, state);
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    public static void clear() {
        STATES.clear();
    }

    private static void announce(ServerPlayer player, ServerLevel level, ActiveLayers layers, String key, State state) {
        AreaLayer active = layers.active();
        if (active != null ? !active.settings().announce : !MalmConfig.SHOW_TITLE_ON_LEAVE.get()) {
            return;
        }
        long now = level.getGameTime();
        Long last = state.lastShown.get(key);
        if (last != null && now - last < MalmConfig.REANNOUNCE_COOLDOWN_SECONDS.get() * 20L) {
            return;
        }
        state.lastShown.put(key, now);

        Component name = active != null ? layerName(active) : Component.literal(prettify(level.dimension().location().getPath()));
        DimensionConfig config = AreaResolver.configFor(level, layers);
        Component range = rangeText(config.getLevelRangeFor(player));

        MutableComponent title = Component.empty().withStyle(ChatFormatting.GOLD).append(name);
        Component subtitle = Component.empty();
        if (MalmConfig.LEVEL_RANGE_AS_SUBTITLE.get()) {
            subtitle = range;
        } else {
            title.append(" ").append(range);
        }

        player.connection.send(new ClientboundSetTitlesAnimationPacket(
                MalmConfig.FADE_IN_TICKS.get(), MalmConfig.STAY_TICKS.get(), MalmConfig.FADE_OUT_TICKS.get()));
        // The subtitle is sent first: the client shows it together with the next title. An empty one clears a stale subtitle.
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    private static Component layerName(AreaLayer layer) {
        if (layer.settings().displayName != null) {
            return layer.settings().displayName;
        }
        String path = layer.id().getPath();
        return Component.literal(prettify(path.substring(path.lastIndexOf('/') + 1)));
    }

    private static Component rangeText(MinMax range) {
        int max = Math.min(range.max, GameBalanceConfig.get().MAX_LEVEL);
        int min = Math.max(1, Math.min(range.min, max));
        String text = min == max ? "[" + min + "]" : "[" + min + "-" + max + "]";
        return Component.literal(text).withStyle(ChatFormatting.YELLOW);
    }

    /** "ocean_monument" -> "Ocean Monument". */
    static String prettify(String path) {
        StringBuilder sb = new StringBuilder();
        for (String word : path.split("[_\\-]+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return sb.toString();
    }
}
