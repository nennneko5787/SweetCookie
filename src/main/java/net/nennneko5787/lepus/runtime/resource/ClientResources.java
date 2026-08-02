package net.nennneko5787.lepus.runtime.resource;

import java.util.Optional;
import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * Asks the client to read the generated pack again. SC-180 §8.1.
 *
 * <p><b>Why this has to exist.</b> The client bakes every block model once, during its resource
 * load, on the way to the main menu — which is long before any world, and therefore long before any
 * pack is bound. The generated pack served at that moment says every slot is unbound and draws
 * nothing. Binding then rewrites the pack and nothing tells the client, so a bound block is
 * <em>invisible</em>: its collision box and its outline are live and correct because those are asked
 * for per query, and its model is the one baked before it existed.
 *
 * <p>That failure is worth stating precisely because it looks like a texture problem and is not one.
 * The model and the texture were both generated correctly; the client is simply still holding the
 * previous answer.
 *
 * <p>The indirection is {@code ScreenOpener}'s, for {@code ScreenOpener}'s reason: binding runs on
 * the server thread in a process that, on a dedicated server, has no client classes at all.
 */
@SpecImpl("SC-180")
public final class ClientResources {

    private static Optional<Runnable> reloader = Optional.empty();

    private ClientResources() {
    }

    /** Called from client initialisation. */
    public static void register(Runnable reload) {
        reloader = Optional.ofNullable(reload);
    }

    /**
     * Reloads if there is a client to reload.
     *
     * <p>Call only when the pack's bytes actually changed. A resource reload is seconds of visible
     * pause, and one on every world load — most of which rebuild identical files — would be a
     * permanent tax for nothing.
     */
    public static void reload() {
        reloader.ifPresent(Runnable::run);
    }
}
