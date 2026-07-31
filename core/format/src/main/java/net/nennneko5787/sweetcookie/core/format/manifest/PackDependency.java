package net.nennneko5787.sweetcookie.core.format.manifest;

import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.value.PackId;
import net.nennneko5787.sweetcookie.core.format.value.SemanticVersion;

/**
 * A {@code dependencies[]} entry. SC-100 §4.2.
 *
 * <p>Two disjoint shapes share one array, distinguished by which key is present:
 *
 * <pre>{@code
 * { "uuid": "…",                       "version": [1, 0, 0] }   // another pack
 * { "module_name": "@minecraft/server", "version": "2.8.0"  }   // a built-in script module
 * }</pre>
 *
 * <p>Sealed so that a {@code switch} over them is exhaustive: the two are checked against completely
 * different things — the set of loaded packs, and SC-200's supported API set — and a third shape
 * appearing should break the code that resolves them rather than fall through a default.
 */
@SpecImpl("SC-100")
public sealed interface PackDependency {

    /** The version the dependent asks for. Satisfied leniently; see SC-100 §10. */
    SemanticVersion version();

    /**
     * A dependency on another pack.
     *
     * <p>Cycles are permitted and normal: a behavior pack and its paired resource pack routinely
     * depend on each other, so this graph is <b>never</b> topologically sorted in a way that fails
     * on one.
     */
    record OnPack(PackId uuid, SemanticVersion version) implements PackDependency {
    }

    /**
     * A dependency on a built-in script module such as {@code @minecraft/server}.
     *
     * <p>An unsupported module, or a version outside the supported range, disables <em>that script
     * module only</em> ({@code SCE-2005}) — not the pack, which usually has content worth loading
     * regardless.
     */
    record OnModule(String moduleName, SemanticVersion version) implements PackDependency {
    }
}
