package net.nennneko5787.lepus.core.format.ir.item;

import java.util.Map;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.ir.UnknownData;
import net.nennneko5787.lepus.core.format.json.JsonValue;
import net.nennneko5787.lepus.core.format.value.BedrockId;
import net.nennneko5787.lepus.core.format.value.Provenance;

/**
 * One Bedrock item definition. SC-170.
 *
 * <p><b>No slot, no ledger entry, no registration.</b> An item is a stack of the one carrier item
 * carrying its identity in a data component (SC-120 §4), so unlike a block it needs nothing reserved
 * and nothing remembered per world. That is why this record is so much smaller than
 * {@code BlockDefIr}: there is no state schema, no permutation list and no index to keep stable.
 *
 * <p>Components stay as raw JSON keyed by identifier, as blocks' do, because Bedrock has 44 of them
 * and typing all 44 before any is exercised would be a large change made blind.
 *
 * @param menuCategory the creative-menu group: {@code description.menu_category.category} in the
 *                     modern format and {@code description.category} in the {@code 1.10}–{@code 1.16}
 *                     one. Both are read, because both are in circulation and an item filed under
 *                     neither would sort last for a reason its author could not see.
 * @param inCreative   whether the creative menu offers it. The {@code 1.10}-era shape says so with
 *                     {@code register_to_creative_menu}; the modern shape dropped that and made
 *                     <b>declaring a {@code menu_category} the way an item asks</b>. An explicit
 *                     boolean wins in either shape, so an item that says no is not offered — showing
 *                     every internal item a pack defines would bury the ones a player is meant to
 *                     have
 */
@SpecImpl("SC-170")
public record ItemDefIr(
        BedrockId identifier,
        String menuCategory,
        boolean inCreative,
        Map<BedrockId, JsonValue> components,
        Provenance provenance,
        UnknownData unknown) {

    public ItemDefIr {
        // NOT Map.copyOf: its iteration order is unspecified and randomised per JVM run, so the
        // components list in a golden came out in a different order every time it was generated.
        // The declaration order is the pack's own and is worth keeping — a reviewer comparing a
        // golden against the file it came from should find them in the same sequence.
        components = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(components));
    }
}
