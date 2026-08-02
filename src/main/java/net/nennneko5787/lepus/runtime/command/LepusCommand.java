package net.nennneko5787.lepus.runtime.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.nennneko5787.lepus.Lepus;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.registry.ActivePacks;
import net.nennneko5787.lepus.runtime.addon.PackSummary;
import net.nennneko5787.lepus.runtime.addon.WorldActivation;
import net.nennneko5787.lepus.runtime.registry.WorldLedger;
import net.nennneko5787.lepus.core.ui.TextView;
import net.nennneko5787.lepus.core.ui.ViewModel;
import net.nennneko5787.lepus.runtime.ui.Views;

/**
 * {@code /lepus}. SC-280 §7, SC-120 §8.
 *
 * <p>Every management operation is a command first and the screen calls it (SC-280 §7). That is what
 * gives a dedicated server the full feature set with no client UI, and what stops the screen from
 * growing semantics of its own.
 *
 * <p><b>Permissions split by effect, not by command.</b> The read-only queries need none: they
 * report what is installed and what this world bound, which is what a player needs when their blocks
 * look wrong, and refusing it to non-operators makes the commonest support question unanswerable by
 * the person asking it. The mutating ones need level 2, because pack order decides what every player
 * in the world sees.
 */
@SpecImpl({"SC-280", "SC-120"})
public final class LepusCommand {

    /** Suggests pack names from what is actually installed, so nobody types a UUID. */
    private static final SuggestionProvider<CommandSourceStack> INSTALLED_PACKS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    Lepus.addons().packs().stream().map(LepusCommand::handleOf).toList(),
                    builder);

    /**
     * Suggests LOGICAL identifiers, which is the only kind a command may name.
     *
     * <p>Constitution rule 12: a slot appears in chunk storage and the ledger and nowhere else -
     * not in a command. /setblock lepus:block_16/0037 works and is exactly what this exists to
     * stop anyone needing to type.
     */
    private static final SuggestionProvider<CommandSourceStack> BOUND_BLOCKS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    WorldLedger.current()
                            .map(ledger -> ledger.bindings().stream()
                                    .map(net.nennneko5787.lepus.core.registry.BlockLedger
                                            .Binding::logicalId)
                                    .toList())
                            .orElse(List.of()),
                    builder);

    private LepusCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(Lepus.MOD_ID)
                .executes(context -> show(context.getSource(), packsView()));

        root.then(Commands.literal("packs")
                .executes(context -> show(context.getSource(), packsView())));

        root.then(Commands.literal("pool").executes(context ->
                show(context.getSource(), Views.pool(Lepus.blockPool(), WorldLedger.current()))));

        root.then(Commands.literal("enable")
                .requires(LepusCommand::mayManagePacks)
                .then(Commands.argument("pack", StringArgumentType.greedyString())
                        .suggests(INSTALLED_PACKS)
                        .executes(context -> enable(context, true))));

        root.then(Commands.literal("disable")
                .requires(LepusCommand::mayManagePacks)
                .then(Commands.argument("pack", StringArgumentType.greedyString())
                        .suggests(INSTALLED_PACKS)
                        .executes(context -> enable(context, false))));

        root.then(Commands.literal("place")
                .requires(LepusCommand::mayManagePacks)
                .then(Commands.argument("block", StringArgumentType.greedyString())
                        .suggests(BOUND_BLOCKS)
                        .executes(context -> place(context,
                                net.minecraft.core.BlockPos.containing(
                                        context.getSource().getPosition())))));

        root.then(Commands.literal("order")
                .requires(LepusCommand::mayManagePacks)
                .then(Commands.argument("position", IntegerArgumentType.integer(1))
                        .then(Commands.argument("pack", StringArgumentType.greedyString())
                                .suggests(INSTALLED_PACKS)
                                .executes(LepusCommand::order))));

        dispatcher.register(root);
    }

    /**
     * Level 2, spelled with the name Minecraft now uses for it.
     *
     * <p>CommandSourceStack.hasPermission(int) is gone on both supported versions: permissions are a
     * PermissionSet tested by a named PermissionCheck, and Commands.LEVEL_GAMEMASTERS is what used
     * to be level 2. Both versions agree exactly, so this is not a version divergence - it is an
     * API that moved, and a numeric literal written from memory would have compiled on neither.
     */
    private static boolean mayManagePacks(CommandSourceStack source) {
        return Commands.LEVEL_GAMEMASTERS.check(source.permissions());
    }

    /**
     * Places a bound block. SC-120 6, and the only legitimate way to do so today.
     *
     * <p>There was none. A bound block has no item and no creative entry yet (SC-170 is M3), so the
     * only way to put one in the world was /setblock with a POOL SLOT id - which works, and which
     * constitution rule 12 forbids: a slot is chunk storage and the ledger, never a command.
     *
     * <p>State index zero. Choosing a state needs Bedrock state NAMES rather than the index, which
     * is the same rule again in a smaller place, and is worth doing properly rather than by exposing
     * the number.
     */
    private static int place(CommandContext<CommandSourceStack> context,
            net.minecraft.core.BlockPos pos) {
        String logicalId = StringArgumentType.getString(context, "block");
        var binding = WorldLedger.current().flatMap(ledger -> ledger.binding(logicalId));
        if (binding.isEmpty()) {
            return message(context, "no block called \"" + logicalId + "\" is bound in this world."
                    + " /lepus pool lists what is.");
        }
        var block = Lepus.blockPool().block(binding.get().slot());
        if (block.isEmpty()) {
            // SCE-4013 territory: the ledger remembers a slot this build did not register.
            return message(context, logicalId + " is bound to " + binding.get().slot()
                    + ", which is outside the registered pool. Raise lepus.blockPool."
                    + binding.get().slot().sizeClass() + " and restart.");
        }
        context.getSource().getLevel().setBlockAndUpdate(pos, block.get().defaultBlockState());
        return message(context, "placed " + logicalId + " at "
                + pos.getX() + " " + pos.getY() + " " + pos.getZ());
    }

    private static ViewModel packsView() {
        return Views.packs(Lepus.addons(), WorldActivation.known());
    }

    private static int enable(CommandContext<CommandSourceStack> context, boolean on) {
        String handle = StringArgumentType.getString(context, "pack");
        Optional<PackSummary> pack = find(handle);
        if (pack.isEmpty()) {
            return notInstalled(context, handle);
        }
        PackSummary found = pack.get();
        Optional<ActivePacks> updated = WorldActivation.update(active ->
                on ? active.enable(found.id(), versionOf(found)) : active.disable(found.id()));
        if (updated.isEmpty()) {
            return noWorld(context);
        }
        // Says where it landed, not just that it worked. "Enabled" alone leaves a user to open the
        // list again to find out what it now overrides.
        String where = updated.get().orderOf(found.id())
                .map(position -> " at position " + (position + 1) + " of " + updated.get().size()
                        + "; the last position wins")
                .orElse("");
        return message(context, (on ? "enabled " : "disabled ") + handleOf(found) + where);
    }

    private static int order(CommandContext<CommandSourceStack> context) {
        String handle = StringArgumentType.getString(context, "pack");
        int position = IntegerArgumentType.getInteger(context, "position");
        Optional<PackSummary> pack = find(handle);
        if (pack.isEmpty()) {
            return notInstalled(context, handle);
        }
        if (!WorldActivation.current().isEnabled(pack.get().id())) {
            return message(context, handleOf(pack.get())
                    + " is not enabled in this world, so it has no position to move");
        }
        // Positions are 1-based for the user and 0-based inside; a position past the end clamps to
        // the end, because "move it to the top" is naturally typed as a large number.
        Optional<ActivePacks> updated =
                WorldActivation.update(active -> active.moveTo(pack.get().id(), position - 1));
        if (updated.isEmpty()) {
            return noWorld(context);
        }
        return message(context, "moved " + handleOf(pack.get()) + " to position "
                + (updated.get().orderOf(pack.get().id()).orElse(0) + 1)
                + " of " + updated.get().size() + "; the last position wins");
    }

    /**
     * Finds a pack by name or by identity, case-insensitively.
     *
     * <p>Names first, because that is what the suggestion list offers and what a user reads on the
     * screen. Identity as a fallback so that two packs sharing a display name are still reachable.
     */
    private static Optional<PackSummary> find(String handle) {
        List<PackSummary> installed = Lepus.addons().packs();
        return installed.stream()
                .filter(pack -> handleOf(pack).equalsIgnoreCase(handle))
                .findFirst()
                .or(() -> installed.stream()
                        .filter(pack -> pack.id().toString().equalsIgnoreCase(handle))
                        .findFirst());
    }

    private static String handleOf(PackSummary pack) {
        return pack.name().isEmpty() ? pack.id().toString() : pack.name();
    }

    private static net.nennneko5787.lepus.core.format.value.SemanticVersion versionOf(
            PackSummary pack) {
        return net.nennneko5787.lepus.core.format.value.SemanticVersion
                .tryParse(pack.version())
                .orElse(net.nennneko5787.lepus.core.format.value.SemanticVersion.ZERO);
    }

    private static int notInstalled(CommandContext<CommandSourceStack> context, String handle) {
        return message(context, "no installed add-on called \"" + handle
                + "\". /lepus packs lists what is installed.");
    }

    private static int noWorld(CommandContext<CommandSourceStack> context) {
        return message(context, "no world is loaded, so there is nothing to enable packs for");
    }

    private static int message(CommandContext<CommandSourceStack> context, String text) {
        context.getSource().sendSuccess(() -> Component.literal("[Lepus] " + text), false);
        return 1;
    }

    /**
     * Prints a view.
     *
     * <p>One line per message rather than one joined block: the chat window wraps on its own terms
     * and a joined block loses the indentation that carries the structure.
     *
     * <p>{@code sendSuccess(..., false)} — not broadcast to operators. This is a query the caller
     * asked for, and mirroring every {@code /lepus packs} into every operator's chat would
     * make the command unusable on a busy server.
     */
    private static int show(CommandSourceStack source, ViewModel view) {
        List<String> lines = TextView.render(view);
        lines.forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
        return lines.size();
    }
}
