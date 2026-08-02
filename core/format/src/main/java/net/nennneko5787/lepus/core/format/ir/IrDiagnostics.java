package net.nennneko5787.lepus.core.format.ir;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.diag.DiagnosticType;
import net.nennneko5787.lepus.core.format.diag.Severity;

/**
 * The codes SC-110's parsing layer emits. SC-110 §2.2, SC-240 §5.
 *
 * <p>Separate from {@code FormatDiagnostics} only because that holds SC-100's and this holds
 * SC-110's; both are enumerable so the uniqueness test can see every code the module can emit.
 */
@SpecImpl("SC-110")
public final class IrDiagnostics {

    /**
     * The declared {@code format_version} is below the lowest parser registered for this kind of
     * file, so the lowest was used.
     */
    public static final DiagnosticType VERSION_BELOW_LOWEST = new DiagnosticType(
            1030, Severity.WARNING, "lepus.diagnostic.ir.version_below_lowest");

    /**
     * The file's structure contradicts its declared {@code format_version}, and the structure won.
     *
     * <p>Not an anomaly. Authoring tools have shipped this mismatch for years, which is why SC-110
     * §3.1 rule 3 makes sniffing a correctness requirement rather than an optimisation.
     */
    public static final DiagnosticType VERSION_SNIFFED = new DiagnosticType(
            1031, Severity.INFO, "lepus.diagnostic.ir.version_sniffed");

    /** A known key held a value of the wrong shape. The field is dropped; the object survives. */
    public static final DiagnosticType FIELD_MALFORMED = new DiagnosticType(
            1035, Severity.WARNING, "lepus.diagnostic.ir.field_malformed");

    /** A required key is absent, so the object it belongs to is skipped. */
    public static final DiagnosticType FIELD_REQUIRED = new DiagnosticType(
            1036, Severity.WARNING, "lepus.diagnostic.ir.field_required");

    /** No parser is registered for this kind of file at all, so it is skipped. */
    public static final DiagnosticType NO_PARSER = new DiagnosticType(
            1037, Severity.WARNING, "lepus.diagnostic.ir.no_parser");

    private IrDiagnostics() {
    }

    /** Every code declared here, for the uniqueness test SC-240 §7 requires. */
    public static List<DiagnosticType> all() {
        List<DiagnosticType> out = new ArrayList<>();
        for (Field field : IrDiagnostics.class.getDeclaredFields()) {
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
