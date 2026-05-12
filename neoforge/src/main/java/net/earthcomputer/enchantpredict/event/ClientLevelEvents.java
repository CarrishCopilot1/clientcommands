package net.earthcomputer.enchantpredict.event;

import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Mirrors {@code net.earthcomputer.clientcommands.event.ClientLevelEvents} so ported
 * features can subscribe to level load / unload using the same API.
 *
 * <p>The mod entry point ({@code EnchantPredictMod}) wires NeoForge's
 * {@code LevelEvent.Load} / {@code LevelEvent.Unload} into these events.</p>
 */
public final class ClientLevelEvents {
    private ClientLevelEvents() {}

    @FunctionalInterface
    public interface LoadLevel {
        void onLoad(ClientLevel level);
    }

    @FunctionalInterface
    public interface UnloadLevel {
        void onUnload(boolean isDisconnect);
    }

    public static final Event<LoadLevel> LOAD_LEVEL = Event.createArrayBacked(LoadLevel.class, listeners -> level -> {
        for (LoadLevel l : listeners) {
            l.onLoad(level);
        }
    });

    public static final Event<UnloadLevel> UNLOAD_LEVEL = Event.createArrayBacked(UnloadLevel.class, listeners -> isDisconnect -> {
        for (UnloadLevel l : listeners) {
            l.onUnload(isDisconnect);
        }
    });
}
