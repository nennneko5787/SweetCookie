package net.nennneko5787.sweetcookie.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.nennneko5787.sweetcookie.SweetCookie;
import net.nennneko5787.sweetcookie.client.ui.ViewScreen;
import net.nennneko5787.sweetcookie.runtime.addon.WorldActivation;
import net.nennneko5787.sweetcookie.runtime.ui.Views;

/**
 * Opens the add-on screen from ModMenu. SC-280 §3.
 *
 * <p>A <b>soft</b> dependency: ModMenu is {@code modCompileOnly}, this class is named only from
 * {@code fabric.mod.json}'s {@code modmenu} entry point, and nothing else in the mod references it.
 * A client without ModMenu never loads it, which is what SC-280 §3 means by "absent ModMenu must
 * not break anything" — a soft dependency that is soft only in intent is a crash on somebody else's
 * machine.
 *
 * <p>{@code ViewScreen} resolves to whichever of the two per-version implementations this node
 * compiled ({@code src/1.21.11/java} or {@code src/26.2/java}). This class does not know there are
 * two, which is the whole return on the directory split.
 */
public final class SweetCookieModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new ViewScreen(parent, Views.packs(SweetCookie.addons(), WorldActivation.current()));
    }
}
