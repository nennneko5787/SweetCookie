package net.nennneko5787.lepus.core.format.diag;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import net.nennneko5787.lepus.core.api.SpecImpl;

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

    // ── SC-100: safe extraction ──────────────────────────────────────────────────────────────

    /** An entry name escapes the extraction root, or is absolute, or contains a {@code ..} segment. */
    public static final DiagnosticType ENTRY_PATH_ESCAPES = new DiagnosticType(
            1001, Severity.ERROR, "lepus.diagnostic.pack.path_escapes");

    /** A symlink or other non-regular entry. */
    public static final DiagnosticType ENTRY_NOT_REGULAR = new DiagnosticType(
            1002, Severity.ERROR, "lepus.diagnostic.pack.not_regular");

    /** The archive exceeds the total-size or per-entry compression-ratio limit. */
    public static final DiagnosticType ARCHIVE_TOO_LARGE = new DiagnosticType(
            1003, Severity.ERROR, "lepus.diagnostic.pack.too_large");

    /** The archive has more entries than the limit allows. */
    public static final DiagnosticType ARCHIVE_TOO_MANY_ENTRIES = new DiagnosticType(
            1004, Severity.ERROR, "lepus.diagnostic.pack.too_many_entries");

    /** An archive nested deeper than the limit allows. */
    public static final DiagnosticType ARCHIVE_TOO_DEEP = new DiagnosticType(
            1005, Severity.ERROR, "lepus.diagnostic.pack.nested_too_deep");

    /** A single file exceeds the per-file size limit. */
    public static final DiagnosticType ENTRY_TOO_LARGE = new DiagnosticType(
            1006, Severity.ERROR, "lepus.diagnostic.pack.entry_too_large");

    /** An entry name is longer than the limit allows after normalisation. */
    public static final DiagnosticType ENTRY_PATH_TOO_LONG = new DiagnosticType(
            1007, Severity.ERROR, "lepus.diagnostic.pack.path_too_long");

    /**
     * An entry name is not valid UTF-8, or differs from its own NFC normalisation.
     *
     * <p>The standard vector for making two distinct entries collide on a case-insensitive
     * filesystem, which is how a pack smuggles a file past a path check.
     */
    public static final DiagnosticType ENTRY_NAME_NOT_NORMALISED = new DiagnosticType(
            1008, Severity.ERROR, "lepus.diagnostic.pack.name_not_normalised");

    /** Two entries whose lowercase paths collide. The first in archive order wins. */
    public static final DiagnosticType ENTRY_CASE_COLLISION = new DiagnosticType(
            1009, Severity.WARNING, "lepus.diagnostic.pack.case_collision");

    /** A {@code manifest.json} inside a directory that already belongs to a discovered pack. */
    public static final DiagnosticType NESTED_MANIFEST_IGNORED = new DiagnosticType(
            1010, Severity.INFO, "lepus.diagnostic.pack.nested_manifest");

    /** A world container's own data — {@code level.dat} and {@code db/} — which 0.x does not read. */
    public static final DiagnosticType WORLD_DATA_SKIPPED = new DiagnosticType(
            1011, Severity.INFO, "lepus.diagnostic.pack.world_data_skipped");

    /** A {@code modules[].type} Mojang added after this was written. Recorded, then ignored. */
    public static final DiagnosticType MANIFEST_UNKNOWN_MODULE_TYPE = new DiagnosticType(
            1012, Severity.WARNING, "lepus.diagnostic.manifest.unknown_module_type");

    // ── SC-100: manifest.json ────────────────────────────────────────────────────────────────

    /** An unrecognised {@code format_version}. Parsed optimistically as version 2. */
    public static final DiagnosticType MANIFEST_UNKNOWN_FORMAT_VERSION = new DiagnosticType(
            1020, Severity.WARNING, "lepus.diagnostic.manifest.unknown_format_version");

    /** A {@code client_data} module, which appears in Microsoft's own examples. Treated as data. */
    public static final DiagnosticType MANIFEST_CLIENT_DATA_MODULE = new DiagnosticType(
            1021, Severity.INFO, "lepus.diagnostic.manifest.client_data_module");

    /** A {@code script} module with no {@code entry}. Defaults to {@code scripts/main.js}. */
    public static final DiagnosticType MANIFEST_SCRIPT_ENTRY_DEFAULTED = new DiagnosticType(
            1022, Severity.WARNING, "lepus.diagnostic.manifest.script_entry_defaulted");

    /** A dependency with neither {@code uuid} nor {@code module_name}. Dropped. */
    public static final DiagnosticType MANIFEST_DEPENDENCY_UNUSABLE = new DiagnosticType(
            1023, Severity.WARNING, "lepus.diagnostic.manifest.dependency_unusable");

    /** A version array of other than three elements, or a version string that will not parse. */
    public static final DiagnosticType MANIFEST_VERSION_MALFORMED = new DiagnosticType(
            1024, Severity.WARNING, "lepus.diagnostic.manifest.version_malformed");

    /** A malformed UUID, replaced by a stable name-based one so the pack still loads. */
    public static final DiagnosticType MANIFEST_UUID_MALFORMED = new DiagnosticType(
            1025, Severity.WARNING, "lepus.diagnostic.manifest.uuid_malformed");

    /** Two packs with the same UUID and the same version. The later in load order wins. */
    public static final DiagnosticType PACK_DUPLICATE = new DiagnosticType(
            1026, Severity.WARNING, "lepus.diagnostic.pack.duplicate");

    /** Two packs with the same UUID and different versions. The highest version wins. */
    public static final DiagnosticType PACK_DUPLICATE_VERSIONS = new DiagnosticType(
            1027, Severity.WARNING, "lepus.diagnostic.pack.duplicate_versions");

    /** An RP or BP with no {@code min_engine_version}. Treated as 1.16.0. */
    public static final DiagnosticType MANIFEST_NO_MIN_ENGINE_VERSION = new DiagnosticType(
            1028, Severity.INFO, "lepus.diagnostic.manifest.no_min_engine_version");

    /** A required manifest field is missing or unusable, so the pack cannot load. */
    public static final DiagnosticType MANIFEST_UNUSABLE = new DiagnosticType(
            1029, Severity.ERROR, "lepus.diagnostic.manifest.unusable");

    // ── SC-100: semantic ─────────────────────────────────────────────────────────────────────

    /** A declared capability, none of which changes behaviour in 0.x. */
    public static final DiagnosticType CAPABILITY_UNSUPPORTED = new DiagnosticType(
            2001, Severity.WARNING, "lepus.diagnostic.pack.capability_unsupported");

    /** {@code min_engine_version} is newer than the Bedrock engine Lepus targets. */
    public static final DiagnosticType ENGINE_VERSION_AHEAD = new DiagnosticType(
            2002, Severity.WARNING, "lepus.diagnostic.pack.engine_version_ahead");

    /** A dependency on a pack that is not loaded. The dependent pack still loads. */
    public static final DiagnosticType DEPENDENCY_MISSING = new DiagnosticType(
            2003, Severity.WARNING, "lepus.diagnostic.pack.dependency_missing");

    /** A dependency on a higher version than is loaded. Satisfied anyway. */
    public static final DiagnosticType DEPENDENCY_VERSION_AHEAD = new DiagnosticType(
            2004, Severity.WARNING, "lepus.diagnostic.pack.dependency_version_ahead");

    /** A script module needs an {@code @minecraft/*} API that is not supported. */
    public static final DiagnosticType SCRIPT_MODULE_UNSUPPORTED = new DiagnosticType(
            2005, Severity.WARNING, "lepus.diagnostic.pack.script_module_unsupported");

    // ── SC-110: the JSON facade ──────────────────────────────────────────────────────────────

    /** A file is not readable as JSON at all, so it is skipped and the rest of the pack loads. */
    public static final DiagnosticType JSON_MALFORMED = new DiagnosticType(
            1032, Severity.ERROR, "lepus.diagnostic.json.malformed");

    /**
     * The same member name twice in one object.
     *
     * <p>An error rather than a last-wins (SC-000 §6.6): a component list with two
     * {@code minecraft:collision_box} members has a bug the author needs told about, and quietly
     * keeping one of them hides it forever.
     */
    public static final DiagnosticType JSON_DUPLICATE_KEY = new DiagnosticType(
            1033, Severity.ERROR, "lepus.diagnostic.json.duplicate_key");

    /** Nesting past {@link net.nennneko5787.lepus.core.format.json.JsonLimits#maxDepth()}. */
    public static final DiagnosticType JSON_TOO_DEEP = new DiagnosticType(
            1034, Severity.ERROR, "lepus.diagnostic.json.too_deep");

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
