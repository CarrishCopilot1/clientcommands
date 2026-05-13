package net.earthcomputer.enchantpredict.command;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * NeoForge port of clientcommands {@code ClientCommandHelper}. Uses NeoForge's
 * {@link CommandSourceStack} (client-side dispatch) instead of Fabric's source.
 *
 * <p>{@code Flag} support is implemented with a thread-local map keyed by command
 * source identity, preserving the public API used by {@code CEnchantCommand}.</p>
 */
public final class ClientCommandHelper {

    private static final ThreadLocal<Map<Flag<?>, Object>> FLAG_STORE = ThreadLocal.withInitial(HashMap::new);

    private ClientCommandHelper() {}

    @SuppressWarnings("unchecked")
    public static <T> T getFlag(CommandContext<CommandSourceStack> ctx, Flag<T> flag) {
        return (T) FLAG_STORE.get().getOrDefault(flag, flag.getDefault());
    }

    public static <T> void setFlag(Flag<T> flag, T value) {
        FLAG_STORE.get().put(flag, value);
    }

    public static void clearFlags() {
        FLAG_STORE.get().clear();
    }

    public static void sendError(Component error) {
        sendFeedback(Component.literal("").append(error).withStyle(ChatFormatting.RED));
    }

    public static void sendHelp(Component help) {
        sendFeedback(Component.literal("").append(help).withStyle(ChatFormatting.AQUA));
    }

    public static void sendFeedback(String translationKey, Object... args) {
        sendFeedback(Component.translatable(translationKey, args));
    }

    public static void sendFeedback(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) {
            mc.gui.getChat().addMessage(message);
        }
    }

    public static void addOverlayMessage(Component message, int time) {
        Gui gui = Minecraft.getInstance().gui;
        gui.setOverlayMessage(message, false);
        gui.overlayMessageTime = time;
    }
}
