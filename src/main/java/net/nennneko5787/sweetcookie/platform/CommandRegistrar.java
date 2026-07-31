package net.nennneko5787.sweetcookie.platform;

import com.mojang.brigadier.CommandDispatcher;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;

/**
 * Registers {@code /sweetcookie}. SC-230 §3.
 *
 * <p>Both loaders hand over the same thing — a Brigadier {@code CommandDispatcher} over
 * {@code CommandSourceStack} — through different events, so the abstraction is thin by nature.
 * Neither Brigadier nor {@code CommandSourceStack} is a loader type, so nothing here breaks SC-230
 * §2 rule 5.
 *
 * <p>Commands matter more than the screen does, and not only because the screen is harder: SC-280 §7
 * makes every management operation a command first, with the screen calling it. A dedicated server
 * then gets the whole feature set with no client UI, and the screen cannot drift from the command's
 * semantics because it has none of its own.
 */
@SpecImpl("SC-230")
public interface CommandRegistrar {

    /**
     * Runs whenever commands are built — at server start, and again after {@code /reload}.
     *
     * <p>Registering more than once per dispatcher is not a concern: each call receives a fresh
     * dispatcher, which is why this is a callback rather than a one-shot.
     */
    void onRegisterCommands(Consumer<CommandDispatcher<CommandSourceStack>> callback);
}
