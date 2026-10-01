package com.feelingowl.malm;

import com.feelingowl.malm.command.MalmCommand;
import com.feelingowl.malm.layer.LayerReloadListener;
import com.feelingowl.malm.layer.LayerType;
import com.feelingowl.malm.layer.LayerValidator;
import com.feelingowl.malm.resolve.AreaResolver;
import com.feelingowl.malm.title.AreaTitles;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(Malm.MODID)
public class Malm {

    public static final String MODID = "malm";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** Mixin targets. Loading them (without initializing) applies our mixins now, see {@link #onCommonSetup}. */
    private static final String[] MIXIN_TARGETS = {
            "com.robertx22.mine_and_slash.uncommon.utilityclasses.LevelUtils",
            "com.robertx22.mine_and_slash.event_hooks.my_events.OnMobDeathDrops",
            "com.robertx22.mine_and_slash.loot.LootInfo",
            "com.robertx22.mine_and_slash.uncommon.stat_calculation.MobStatUtils",
    };

    public Malm() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(Malm::onCommonSetup);
        MinecraftForge.EVENT_BUS.addListener(Malm::onAddReloadListeners);
        MinecraftForge.EVENT_BUS.addListener(Malm::onTagsUpdated);
        MinecraftForge.EVENT_BUS.addListener(Malm::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(Malm::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(AreaTitles::onPlayerTick);
        MinecraftForge.EVENT_BUS.addListener(AreaTitles::onLogout);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, MalmConfig.SPEC);
    }

    /**
     * Mixins only apply when their target class loads, which for these can be deep into gameplay. Loading them here
     * means a Mine and Slash update that moved a hooked call fails at startup (defaultRequire = 1), instead of the
     * first time a mob spawns.
     */
    private static void onCommonSetup(FMLCommonSetupEvent event) {
        for (String target : MIXIN_TARGETS) {
            try {
                Class.forName(target, false, Malm.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("[malm] Mine and Slash class " + target + " is missing; this Mine and Slash version isn't supported", e);
            }
        }
        LOGGER.info("[malm] Hooked into Mine and Slash level areas");
    }

    private static void onAddReloadListeners(AddReloadListenerEvent event) {
        for (LayerType type : LayerType.values()) {
            event.addListener(new LayerReloadListener(type));
        }
    }

    private static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            LayerValidator.validate(event.getRegistryAccess());
        }
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        MalmCommand.register(event.getDispatcher());
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        AreaResolver.clearCache();
        AreaTitles.clear();
    }
}
