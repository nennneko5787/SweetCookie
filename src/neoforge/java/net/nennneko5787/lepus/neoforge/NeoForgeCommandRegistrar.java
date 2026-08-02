package net.nennneko5787.lepus.neoforge;

import com.mojang.brigadier.CommandDispatcher;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.nennneko5787.lepus.platform.CommandRegistrar;

/**
 * NeoForge's {@link CommandRegistrar}. SC-230 §3.
 *
 * <p>{@code RegisterCommandsEvent} exists at the same name and shape on both NeoForge versions
 * (21.11.45 and 26.2.0.40-beta), verified against both universal jars — the same check that found
 * {@code FMLEnvironment.dist} to be a method rather than a field.
 */
public final class NeoForgeCommandRegistrar implements CommandRegistrar {

    @Override
    public void onRegisterCommands(Consumer<CommandDispatcher<CommandSourceStack>> callback) {
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class,
                event -> callback.accept(event.getDispatcher()));
    }
}
