package net.earthcomputer.enchantpredict.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.earthcomputer.enchantpredict.Configs;
import net.earthcomputer.enchantpredict.command.arguments.ItemAndEnchantmentsPredicateArgument;
import net.earthcomputer.enchantpredict.features.EnchantmentCracker;
import net.earthcomputer.enchantpredict.features.PlayerRandCracker;
import net.earthcomputer.enchantpredict.util.CComponentUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * /cenchant command — port of clientcommands {@code CEnchantCommand}.
 *
 * <p>Registered against {@link RegisterClientCommandsEvent}. The command source is
 * NeoForge's {@link CommandSourceStack}; feedback is sent through
 * {@link ClientCommandHelper#sendFeedback(Component)} to mirror the original chat
 * output.</p>
 */
public class CEnchantCommand {

    public static final Flag<Boolean> FLAG_SIMULATE = Flag.ofFlag("simulate").build();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        var cenchant = dispatcher.register(Commands.literal("cenchant")
            .then(Commands.argument("itemAndEnchantmentsPredicate",
                    ItemAndEnchantmentsPredicateArgument.itemAndEnchantmentsPredicate(context)
                        .withEnchantmentPredicate(CEnchantCommand::enchantmentPredicate)
                        .constrainMaxLevel())
                .executes(ctx -> cenchant(ctx.getSource(),
                    ItemAndEnchantmentsPredicateArgument.getItemAndEnchantmentsPredicate(ctx, "itemAndEnchantmentsPredicate")))));
        Flag.addBooleanFlag(dispatcher, cenchant, FLAG_SIMULATE, src -> true);
    }

    private static boolean enchantmentPredicate(Item item, Holder<Enchantment> ench) {
        return ench.is(EnchantmentTags.IN_ENCHANTING_TABLE)
            && (item == Items.BOOK || ench.value().canEnchant(new ItemStack(item)));
    }

    private static int cenchant(CommandSourceStack source, ItemAndEnchantmentsPredicateArgument.ItemAndEnchantmentsPredicate predicate) throws CommandSyntaxException {
        try {
            if (!Configs.enchantingPrediction) {
                Component component = Component.translatable("commands.cenchant.needEnchantingPrediction")
                    .withStyle(ChatFormatting.RED)
                    .append(" ")
                    .append(CComponentUtil.getCommandTextComponent("commands.client.enable",
                        "/cconfig enchantpredict enchantingPrediction set true"));
                ClientCommandHelper.sendFeedback(component);
                return Command.SINGLE_SUCCESS;
            }
            if (!Configs.playerCrackState.knowsSeed() && Configs.enchCrackState != EnchantmentCracker.CrackState.CRACKED) {
                Component component = Component.translatable("commands.cenchant.uncracked")
                    .withStyle(ChatFormatting.RED)
                    .append(" ")
                    .append(CComponentUtil.getCommandTextComponent("commands.client.crack", "/ccrackrng"));
                ClientCommandHelper.sendFeedback(component);
                return Command.SINGLE_SUCCESS;
            }

            boolean simulate = ClientCommandHelper.getFlag(null, FLAG_SIMULATE);

            String taskName = EnchantmentCracker.manipulateEnchantments(
                predicate.item(),
                predicate.predicate(),
                simulate,
                result -> {
                    ClientLevel level = Minecraft.getInstance().level;
                    if (level == null) return;

                    if (result == null) {
                        ClientCommandHelper.sendFeedback(Component.translatable("commands.cenchant.failed"));
                        if (Configs.playerCrackState != PlayerRandCracker.CrackState.CRACKED) {
                            MutableComponent help = Component.translatable("commands.cenchant.help.uncrackedPlayerSeed")
                                .append(" ")
                                .append(CComponentUtil.getCommandTextComponent("commands.client.crack", "/ccrackrng"));
                            ClientCommandHelper.sendHelp(help);
                        }
                    } else {
                        if (result.itemThrows() < 0) {
                            ClientCommandHelper.sendFeedback(Component.translatable("enchCrack.insn.itemThrows.noDummy"));
                        } else {
                            ClientCommandHelper.sendFeedback(Component.translatable("enchCrack.insn.itemThrows",
                                result.itemThrows(), (float) result.itemThrows() / (Configs.itemThrowsPerTick * 20)));
                        }
                        ClientCommandHelper.sendFeedback(Component.translatable("enchCrack.insn.bookshelves", result.bookshelves()));
                        ClientCommandHelper.sendFeedback(Component.translatable("enchCrack.insn.slot", result.slot() + 1));
                        ClientCommandHelper.sendFeedback(Component.translatable("enchCrack.insn.enchantments"));
                        List<EnchantmentInstance> enchantments = new ArrayList<>(result.enchantments());
                        EnchantmentCracker.sortIntoTooltipOrder(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT), enchantments);
                        for (EnchantmentInstance ench : enchantments) {
                            ClientCommandHelper.sendFeedback(Component.literal("- ").append(Enchantment.getFullname(ench.enchantment(), ench.level())));
                        }
                    }
                }
            );

            ClientCommandHelper.sendFeedback(Component.translatable("commands.cenchant.success")
                .append(" ")
                .append(CComponentUtil.getCommandTextComponent("commands.client.cancel", "/ctask stop " + taskName)));
            return Command.SINGLE_SUCCESS;
        } finally {
            ClientCommandHelper.clearFlags();
        }
    }
}
