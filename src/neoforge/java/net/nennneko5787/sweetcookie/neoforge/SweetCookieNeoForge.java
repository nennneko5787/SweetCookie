package net.nennneko5787.sweetcookie.neoforge;

import net.neoforged.fml.common.Mod;

import net.nennneko5787.sweetcookie.SweetCookie;

/**
 * NeoForge entry point.
 *
 * <p>Lives in {@code src/neoforge/java}, which only the NeoForge nodes compile. See
 * {@code SweetCookieFabric} for why loader code is separated by directory rather than by
 * {@code //?} comment.
 */
@Mod(SweetCookie.MOD_ID)
public final class SweetCookieNeoForge {

    public SweetCookieNeoForge() {
        SweetCookie.init();
    }
}
