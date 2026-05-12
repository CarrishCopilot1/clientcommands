package net.earthcomputer.enchantpredict;

import net.earthcomputer.enchantpredict.command.CConfigCommand;
import net.earthcomputer.enchantpredict.command.CEnchantCommand;
import net.earthcomputer.enchantpredict.command.CTaskCommand;
import net.earthcomputer.enchantpredict.command.CrackRNGCommand;
import net.earthcomputer.enchantpredict.event.ClientLevelEvents;
import net.earthcomputer.enchantpredict.features.EnchantmentCracker;
import net.earthcomputer.enchantpredict.features.PlayerRandCracker;
import net.earthcomputer.enchantpredict.task.ItemThrowTask;
import net.earthcomputer.enchantpredict.task.TaskManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Mod entry point. Wires NeoForge lifecycle events to the ported subsystems
 * ({@link TaskManager}, {@link ClientLevelEvents}, {@link PlayerRandCracker},
 * {@link EnchantmentCracker}) and registers the client commands.
 */
@Mod(value = EnchantPredictMod.MOD_ID, dist = Dist.CLIENT)
public class EnchantPredictMod {

    public static final String MOD_ID = "enchantpredict";

    public EnchantPredictMod(IEventBus modBus, ModLoadingContext loadingContext) {
        loadingContext.registerConfig(ModConfig.Type.CLIENT, Configs.SPEC);
        modBus.addListener(EnchantPredictMod::onConfigEvent);

        TaskManager.init();
        ItemThrowTask.init();
        PlayerRandCracker.registerEvents();
        EnchantmentCracker.registerEvents();
    }

    private static void onConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() == Configs.SPEC) {
            Configs.load();
        }
    }

    @EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
    public static class GameEvents {

        @SubscribeEvent
        public static void onRegisterCommands(RegisterClientCommandsEvent event) {
            CEnchantCommand.register(event.getDispatcher(), event.getBuildContext());
            CrackRNGCommand.register(event.getDispatcher());
            CTaskCommand.register(event.getDispatcher());
            CConfigCommand.register(event.getDispatcher());
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            TaskManager.tick();
        }

        @SubscribeEvent
        public static void onLevelLoad(LevelEvent.Load event) {
            if (event.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel cl) {
                ClientLevelEvents.LOAD_LEVEL.invoker().onLoad(cl);
            }
        }

        @SubscribeEvent
        public static void onLevelUnload(LevelEvent.Unload event) {
            if (event.getLevel() instanceof net.minecraft.client.multiplayer.ClientLevel) {
                // NeoForge doesn't tell us if it's a disconnect vs dimension switch;
                // approximate using whether the local player is still connected.
                var mc = net.minecraft.client.Minecraft.getInstance();
                boolean isDisconnect = mc.getConnection() == null || !mc.getConnection().getConnection().isConnected();
                ClientLevelEvents.UNLOAD_LEVEL.invoker().onUnload(isDisconnect);
            }
        }
    }
}
