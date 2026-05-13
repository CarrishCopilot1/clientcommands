package net.earthcomputer.enchantpredict.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Simplified Flag implementation. The original used clientarguments to attach
 * {@code --flag} options to a command. Here we register flag-style literals
 * ({@code "--name"}) as children of the command node that consume the flag value
 * and recursively re-dispatch the rest of the line.
 *
 * <p>For the enchantment-prediction port we only need a single boolean flag
 * ({@code --simulate}), so this minimal implementation is sufficient.</p>
 */
public final class Flag<T> {
    private final String name;
    private final Supplier<T> defaultSupplier;

    private Flag(String name, Supplier<T> defaultSupplier) {
        this.name = name;
        this.defaultSupplier = defaultSupplier;
    }

    public String name() { return name; }
    public T getDefault() { return defaultSupplier.get(); }

    public static FlagBuilder<Boolean> ofFlag(String name) {
        return new FlagBuilder<>(name, () -> Boolean.FALSE);
    }

    public static final class FlagBuilder<T> {
        private final String name;
        private final Supplier<T> defaultSupplier;
        public FlagBuilder(String name, Supplier<T> def) {
            this.name = name;
            this.defaultSupplier = def;
        }
        public Flag<T> build() { return new Flag<>(name, defaultSupplier); }
    }

    /**
     * Attaches this boolean flag (e.g. {@code --simulate}) to the given command literal:
     * setting the flag in the thread-local store and then re-dispatching to the same command tree.
     */
    public static void addBooleanFlag(CommandDispatcher<CommandSourceStack> dispatcher,
                                      LiteralCommandNode<CommandSourceStack> command,
                                      Flag<Boolean> flag,
                                      Predicate<CommandSourceStack> requirement) {
        LiteralArgumentBuilder<CommandSourceStack> wrapper = Commands.literal("--" + flag.name())
            .requires(requirement)
            .redirect(command, ctx -> {
                ClientCommandHelper.setFlag(flag, Boolean.TRUE);
                return ctx.getSource();
            });
        dispatcher.getRoot().getChild(command.getName()).addChild(wrapper.build());
    }
}
