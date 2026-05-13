package net.earthcomputer.enchantpredict.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.earthcomputer.enchantpredict.task.TaskManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.StringArgumentType;
import net.minecraft.network.chat.Component;

/**
 * /ctask command — minimal port that supports {@code list} and {@code stop <id>},
 * matching the operations referenced from the cracker overlay/messages.
 */
public class CTaskCommand {

    private static final SuggestionProvider<CommandSourceStack> TASK_SUGGESTOR =
        (ctx, builder) -> SharedSuggestionProvider.suggest(TaskManager.getTaskNames(), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ctask")
            .then(Commands.literal("list").executes(CTaskCommand::list))
            .then(Commands.literal("stop")
                .then(Commands.argument("id", StringArgumentType.string())
                    .suggests(TASK_SUGGESTOR)
                    .executes(ctx -> stop(StringArgumentType.getString(ctx, "id"))))));
    }

    private static int list(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        int count = TaskManager.getTaskCount();
        if (count == 0) {
            ClientCommandHelper.sendFeedback(Component.translatable("commands.ctask.list.empty"));
        } else {
            ClientCommandHelper.sendFeedback(Component.translatable("commands.ctask.list.header", count));
            for (String name : TaskManager.getTaskNames()) {
                ClientCommandHelper.sendFeedback(Component.literal(" - " + name));
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int stop(String name) {
        TaskManager.removeTask(name);
        ClientCommandHelper.sendFeedback(Component.translatable("commands.ctask.stop", name));
        return Command.SINGLE_SUCCESS;
    }
}
