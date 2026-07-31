package net.nennneko5787.sweetcookie.core.format.value;

import java.util.Objects;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;

/**
 * Where a piece of IR came from: which pack, which file, which position in that file, and which
 * {@code format_version} was declared versus actually used. SC-110 §4.
 *
 * <p>Mandatory on every IR node reachable from a pack, and that is constitution rule 8 rather than
 * fastidiousness. "Unknown component" is useless to an author with forty packs installed;
 * "{@code wizardry} → {@code entities/wizard.json} → {@code /minecraft:entity/components}" is
 * actionable. It is also what lets hot reload say which pack changed.
 *
 * <p>{@code declaredVersion} and {@code effectiveVersion} differ constantly in real packs — the
 * authoring tools have shipped that bug for years — and keeping both is what makes {@code SCE-1031}
 * reportable. Neither is ever read as a behavioural switch (SC-110 §3.2); they exist for
 * diagnostics.
 *
 * <p>Interning: {@code pack} and {@code path} are shared references held by the pack's parse
 * context, and {@link #at} builds pointers only when a node is actually described. The cost is a few
 * bytes per node, not a string per node.
 *
 * @param pack             the pack this came from, or {@link PackId#NONE}
 * @param path             the VFS path within the pack, {@code /}-separated and root-relative
 * @param jsonPointer      RFC 6901 position within the file; empty means the whole document
 * @param declaredVersion  {@code format_version} as written; empty when the file declares none
 * @param effectiveVersion the version the parser actually used
 * @param lossy            an upgrade or clamp discarded information reaching this node
 */
@SpecImpl("SC-110")
public record Provenance(
        PackId pack,
        String path,
        String jsonPointer,
        String declaredVersion,
        String effectiveVersion,
        boolean lossy) {

    /** Content that came from no file at all — a synthesised default, or a configuration value. */
    public static final Provenance NONE = new Provenance(PackId.NONE, "", "", "", "", false);

    public Provenance {
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(jsonPointer, "jsonPointer");
        Objects.requireNonNull(declaredVersion, "declaredVersion");
        Objects.requireNonNull(effectiveVersion, "effectiveVersion");
    }

    /** A whole file, before any version has been determined. */
    public static Provenance file(PackId pack, String path) {
        return new Provenance(pack, path, "", "", "", false);
    }

    /** The same file, at an RFC 6901 pointer. Build the pointer with {@code JsonPointer}. */
    public Provenance at(String pointer) {
        return pointer.equals(jsonPointer)
                ? this
                : new Provenance(pack, path, pointer, declaredVersion, effectiveVersion, lossy);
    }

    /** The same location, recording which version was declared and which was used. */
    public Provenance withVersions(String declared, String effective) {
        return new Provenance(pack, path, jsonPointer, declared, effective, lossy);
    }

    /**
     * The same location, marked as having lost information. Never unset once set.
     *
     * <p>Named {@code markLossy} rather than {@code lossy} because the record component already owns
     * that name — the accessor answers the question, this changes the answer.
     */
    public Provenance markLossy() {
        return lossy ? this : new Provenance(pack, path, jsonPointer, declaredVersion,
                effectiveVersion, true);
    }

    /** True when the file declared a version that the parser did not honour. SC-110 §3.1 rule 3. */
    public boolean versionOverridden() {
        return !declaredVersion.isEmpty()
                && !effectiveVersion.isEmpty()
                && !declaredVersion.equals(effectiveVersion);
    }

    /** A one-line rendering for diagnostics: {@code <pack>/<path>#<pointer>}. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (!pack.isNone()) {
            sb.append(pack).append('/');
        }
        sb.append(path.isEmpty() ? "<no file>" : path);
        if (!jsonPointer.isEmpty()) {
            sb.append('#').append(jsonPointer);
        }
        return sb.toString();
    }
}
