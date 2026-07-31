package net.nennneko5787.sweetcookie.core.format.diag;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;

/**
 * Every {@code SCE-} code {@code core/format} can emit, declared once. SC-240 §5.
 *
 * <p>SC-240 requires that a code is allocated exactly once and never renumbered or reused, because
 * users search the internet for them. That is only checkable if allocation happens somewhere a test
 * can enumerate — hence one holder rather than integers written inline at the emitting sites, and
 * hence {@link #all()}, which exists for the test rather than for production code.
 *
 * <p>The ranges are SC-240 §5's: 1000–1999 parse, 2000–2999 semantic. Within them, SC-100 owns
 * 1001–1028 and 2001–2005, SC-110 owns 1030–1040 and 2010–2012.
 */
@SpecImpl("SC-240")
public final class FormatDiagnostics {

    // ── SC-110: the JSON facade ──────────────────────────────────────────────────────────────

    /** A file is not readable as JSON at all, so it is skipped and the rest of the pack loads. */
    public static final DiagnosticType JSON_MALFORMED = new DiagnosticType(
            1032, Severity.ERROR, "sweetcookie.diagnostic.json.malformed");

    /**
     * The same member name twice in one object.
     *
     * <p>An error rather than a last-wins (SC-000 §6.6): a component list with two
     * {@code minecraft:collision_box} members has a bug the author needs told about, and quietly
     * keeping one of them hides it forever.
     */
    public static final DiagnosticType JSON_DUPLICATE_KEY = new DiagnosticType(
            1033, Severity.ERROR, "sweetcookie.diagnostic.json.duplicate_key");

    /** Nesting past {@link net.nennneko5787.sweetcookie.core.format.json.JsonLimits#maxDepth()}. */
    public static final DiagnosticType JSON_TOO_DEEP = new DiagnosticType(
            1034, Severity.ERROR, "sweetcookie.diagnostic.json.too_deep");

    private FormatDiagnostics() {
    }

    /**
     * Every code declared here, for the uniqueness test SC-240 §7 requires.
     *
     * <p>Reflective on purpose: a hand-maintained list would drift from the constants, and a list
     * that drifts is exactly the "check that cannot fail" this project has already been bitten by
     * three times.
     */
    public static List<DiagnosticType> all() {
        List<DiagnosticType> out = new ArrayList<>();
        for (Field field : FormatDiagnostics.class.getDeclaredFields()) {
            int mods = field.getModifiers();
            if (Modifier.isStatic(mods)
                    && Modifier.isPublic(mods)
                    && field.getType() == DiagnosticType.class) {
                try {
                    out.add((DiagnosticType) field.get(null));
                } catch (IllegalAccessException impossible) {
                    throw new AssertionError(field.getName(), impossible);
                }
            }
        }
        return List.copyOf(out);
    }
}
