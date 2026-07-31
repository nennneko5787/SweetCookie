package net.nennneko5787.sweetcookie.core.format.ir;

import java.util.List;
import java.util.Optional;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.diag.DiagnosticLog;
import net.nennneko5787.sweetcookie.core.format.value.PackId;

/**
 * Everything an add-on parsed into, in load order. SC-110 §8.
 *
 * <p>{@code IrIndex} — the merged cross-pack view of SC-110 §9 — is not here yet. It cannot be built
 * usefully from one content kind, and building it early would fix merge rules that differ per kind
 * before most of those kinds exist.
 *
 * @param packs       in resolved load order; index equals {@code loadOrder}, and higher wins
 * @param diagnostics everything SC-100's load and SC-110's parse reported, deduplicated
 */
@SpecImpl("SC-110")
public record AddonIr(List<PackIr> packs, DiagnosticLog diagnostics) {

    public AddonIr {
        packs = List.copyOf(packs);
    }

    public Optional<PackIr> byId(PackId id) {
        return packs.stream().filter(pack -> pack.id().equals(id)).findFirst();
    }
}
