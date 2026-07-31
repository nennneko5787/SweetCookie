package net.nennneko5787.sweetcookie.core.format.diag;

/**
 * How bad a {@link Diagnostic} is, and therefore where it surfaces. SC-240 §2.
 *
 * <p>The distinction that matters: an unimplemented feature a pack uses is {@link #WARNING}, not
 * {@link #ERROR}. The pack still works, mostly. Reserving {@code ERROR} for genuine breakage is what
 * keeps it meaningful — and constitution rule 1 means the common case really is "degraded, not
 * broken".
 */
public enum Severity {

    /** A normal, expected difference between Bedrock and Java. Surfaces only on request. */
    INFO,

    /** Content loaded, with reduced fidelity. Surfaces as an in-game summary. */
    WARNING,

    /** Content did not load, or something is broken. Surfaces in-game on join for operators. */
    ERROR;

    /** True when {@code this} is at least as severe as {@code other}. */
    public boolean atLeast(Severity other) {
        return compareTo(other) >= 0;
    }
}
