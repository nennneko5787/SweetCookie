package net.nennneko5787.lepus.runtime.ui;

import java.util.Optional;
import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * The one way anything server-side asks for the add-on screen. SC-280 §7.
 *
 * <p><b>An indirection, not a convenience.</b> {@code /lepus} runs on the server — on a
 * dedicated server, in a process with no client classes on the classpath at all. Calling
 * {@code AddonPackScreen.open()} from the command would load a client class there and take the
 * server down at the first invocation, which no compiler and no headless test would have caught.
 *
 * <p>So the client half <b>registers itself</b> during client initialisation and the command asks
 * whether anyone did. A dedicated server never registers, {@link #open()} answers false, and the
 * command prints the text view instead — which is the right answer there anyway, since there is
 * nobody to show a screen to.
 */
@SpecImpl("SC-280")
public final class ScreenOpener {

    private static Optional<java.util.function.Consumer<Boolean>> opener = Optional.empty();

    private ScreenOpener() {
    }

    /** Called from client initialisation. The only place that knows a screen class exists. */
    public static void register(java.util.function.Consumer<Boolean> openScreen) {
        opener = Optional.ofNullable(openScreen);
    }

    /**
     * Opens the screen if there is one.
     *
     * <p>The caller's permission is passed in rather than looked up on the client, because the
     * command already knows it and the client has no reliable way to ask. A reader who cannot manage
     * packs gets the read-only view: the selection screen would let them tick a box and then send a
     * command their own client refuses to parse, which fails in silence (SC-280 §7.1.1).
     *
     * @param mayManage whether this caller passes the mutating commands' permission check
     * @return false when there is no client in this process, so the caller can say something else
     */
    public static boolean open(boolean mayManage) {
        opener.ifPresent(open -> open.accept(mayManage));
        return opener.isPresent();
    }

    /** True when a screen could be opened. For deciding what to offer before offering it. */
    public static boolean available() {
        return opener.isPresent();
    }
}
