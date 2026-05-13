package net.earthcomputer.enchantpredict.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

/**
 * Minimal port of clientcommands {@code CComponentUtil} — only the helpers needed
 * by the enchantment-prediction feature set are retained.
 *
 * <p>Adapted for 1.21.1 click/hover event API (action+arg constructors).</p>
 */
public final class CComponentUtil {
    private CComponentUtil() {}

    public static Component getCommandTextComponent(String translationKey, String command) {
        return getCommandTextComponent(Component.translatable(translationKey), command);
    }

    public static Component getCommandTextComponent(MutableComponent component, String command) {
        return component.withStyle(style -> style.applyFormat(ChatFormatting.UNDERLINE)
            .withColor(ChatFormatting.GREEN)
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
    }
}
