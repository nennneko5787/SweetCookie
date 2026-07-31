package net.nennneko5787.sweetcookie.runtime.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.nennneko5787.sweetcookie.SweetCookie;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.runtime.registry.WorldLedger;
import net.nennneko5787.sweetcookie.runtime.ui.TextView;
import net.nennneko5787.sweetcookie.runtime.ui.ViewModel;
import net.nennneko5787.sweetcookie.runtime.ui.Views;

/**
 * {@code /sweetcookie}. SC-280 §7, SC-120 §8.
 *
 * <p>Every management operation is a command first and the screen calls it (SC-280 §7). That is what
 * gives a dedicated server the full feature set with no client UI, and what stops the screen from
 * growing semantics of its own.
 *
 * <p>The read-only queries here need <b>no permission level</b>. They report what this instance has
 * installed and what this world has bound, which is information a player is entitled to when their
 * blocks look wrong — and refusing it to non-operators would make the single most common support
 * question unanswerable by the person asking it. Anything that <em>changes</em> state will require
 * level 2 when it arrives.
 */
@SpecImpl({"SC-280", "SC-120"})
public final class SweetCookieCommand {

    private SweetCookieCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(SweetCookie.MOD_ID)
                .executes(context -> show(context.getSource(), Views.packs(SweetCookie.addons())));

        root.then(Commands.literal("packs").executes(context ->
                show(context.getSource(), Views.packs(SweetCookie.addons()))));

        root.then(Commands.literal("pool").executes(context ->
                show(context.getSource(), Views.pool(SweetCookie.blockPool(), WorldLedger.current()))));

        dispatcher.register(root);
    }

    /**
     * Prints a view.
     *
     * <p>One line per message rather than one joined block: the chat window wraps on its own terms
     * and a joined block loses the indentation that carries the structure.
     *
     * <p>{@code sendSuccess(..., false)} — not broadcast to operators. This is a query the caller
     * asked for, and mirroring every {@code /sweetcookie packs} into every operator's chat would
     * make the command unusable on a busy server.
     */
    private static int show(CommandSourceStack source, ViewModel view) {
        List<String> lines = TextView.render(view);
        lines.forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
        return lines.size();
    }
}
