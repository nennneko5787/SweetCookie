package net.nennneko5787.lepus.fabric;

import com.mojang.brigadier.CommandDispatcher;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.nennneko5787.lepus.platform.CommandRegistrar;

/**
 * Fabric's {@link CommandRegistrar}. SC-230 §3.
 *
 * <p>{@code CommandRegistrationCallback} hands over a dispatcher, a build context and a selection.
 * Only the dispatcher crosses the interface: the other two are needed by commands that register
 * argument types against a registry, and none of ours does. Passing them anyway would put two more
 * Minecraft types in a signature to no purpose.
 */
public final class FabricCommandRegistrar implements CommandRegistrar {

    @Override
    public void onRegisterCommands(Consumer<CommandDispatcher<CommandSourceStack>> callback) {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> callback.accept(dispatcher));
    }
}
