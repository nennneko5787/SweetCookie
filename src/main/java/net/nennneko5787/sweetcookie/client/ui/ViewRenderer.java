package net.nennneko5787.sweetcookie.client.ui;

import net.nennneko5787.sweetcookie.core.api.SpecImpl;

/**
 * The one thing a screen backend has to provide: put a line of text somewhere. SC-280 §3.1.
 *
 * <p>Deliberately this small. 1.21.11 draws through {@code GuiGraphics.drawString} during
 * {@code Screen.render}, and 26.2 through {@code GuiGraphicsExtractor.text} during
 * {@code Screen.extractRenderState} — different method to override, different parameter type,
 * different call. Everything else about laying out a {@code ViewModel} is arithmetic and is shared.
 *
 * <p>Narrowing the version-divergent surface to one method is the whole point. SC-220 §3 warns that
 * rendering is exactly where the temptation to interleave {@code //?} peaks; the answer is to make
 * the divergent part small enough that a per-version directory holds one class.
 */
@FunctionalInterface
@SpecImpl("SC-280")
public interface ViewRenderer {

    /**
     * @param text  a single line, already laid out
     * @param x     left edge in GUI pixels
     * @param y     top edge in GUI pixels
     * @param argb  colour with alpha in the high byte
     */
    void line(String text, int x, int y, int argb);
}
