package net.nennneko5787.sweetcookie.runtime.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.diag.Diagnostic;
import net.nennneko5787.sweetcookie.core.format.diag.Severity;
import net.nennneko5787.sweetcookie.core.registry.BlockLedger;
import net.nennneko5787.sweetcookie.core.registry.SlotPool;
import net.nennneko5787.sweetcookie.runtime.addon.AddonRegistry;
import net.nennneko5787.sweetcookie.runtime.addon.PackSummary;
import net.nennneko5787.sweetcookie.runtime.registry.BlockPool;

/**
 * Builds the {@link ViewModel}s. SC-280 §5.
 *
 * <p>One place per screen, so that the command and the eventual screen show the same thing by
 * construction rather than by discipline.
 */
@SpecImpl("SC-280")
public final class Views {

    private Views() {
    }

    /**
     * The add-on management view. SC-280 §5.
     *
     * <p>Shows what each pack <b>provides</b>, not just its name: a user with a folder of
     * {@code .mcaddon} files cannot otherwise tell them apart. Errors are quoted in full under their
     * pack, because a badge alone says something is wrong and not what.
     */
    public static ViewModel packs(AddonRegistry addons) {
        return packs(addons, net.nennneko5787.sweetcookie.core.registry.ActivePacks.NONE);
    }

    /**
     * The add-on management view, with this world's activation state.
     *
     * <p>Java Edition's resource-pack screen leaves three things to guess, and this one states all
     * three: <b>which end of the order wins</b>, <b>what a pack actually contains</b>, and <b>why a
     * pack is not doing anything</b>. Guessing the first gets you the other pack's texture with no
     * indication why.
     */
    public static ViewModel packs(
            AddonRegistry addons,
            net.nennneko5787.sweetcookie.core.registry.ActivePacks active) {

        List<ViewModel.Row> enabled = new ArrayList<>();
        List<ViewModel.Row> available = new ArrayList<>();

        for (PackSummary pack : addons.packs()) {
            java.util.Optional<Integer> order = active.orderOf(pack.id());
            String label = order.map(position -> (position + 1) + ". ").orElse("")
                    + (pack.name().isEmpty() ? pack.source() : pack.name());
            String provides = pack.provides().describe();
            List<String> notes = pack.diagnostics().stream()
                    .filter(d -> d.severity() == Severity.ERROR)
                    .map(Diagnostic::toString)
                    .toList();
            long warnings = pack.count(Severity.WARNING);

            // Built in one expression so the lambdas below capture something effectively final.
            String detail = pack.version()
                    + (provides.isEmpty() ? " - provides nothing this build reads" : " - " + provides)
                    + (warnings > 0 && notes.isEmpty() ? "  (" + warnings + " warning(s))" : "");

            ViewModel.Row row = pack.badge()
                    .map(badge -> ViewModel.Row.of(label, detail, badge, notes))
                    .orElseGet(() -> ViewModel.Row.of(label, detail));
            (order.isPresent() ? enabled : available).add(row);
        }

        List<ViewModel.Section> sections = new ArrayList<>();
        if (addons.packs().isEmpty()) {
            sections.add(ViewModel.Section.of("installed",
                    List.of(ViewModel.Row.empty("no add-ons installed"))));
        } else {
            // The heading carries the precedence rule. Java Edition's screen puts the direction
            // nowhere, and a user who assumes the wrong end silently gets the other pack's content.
            sections.add(ViewModel.Section.of(
                    enabled.isEmpty()
                            ? "enabled in this world - none"
                            : "enabled in this world, lowest priority first (the last one wins)",
                    enabled));
            sections.add(ViewModel.Section.of(
                    available.isEmpty() ? "installed but not enabled - none"
                            : "installed but not enabled",
                    available));
        }

        // Reported before any pack had an identity - a corrupt archive, an unusable manifest. It has
        // no pack to sit under and would be dropped by a per-pack view that did not say so.
        if (!addons.unattributed().isEmpty()) {
            sections.add(ViewModel.Section.of("not attributable to a pack",
                    addons.unattributed().stream()
                            .map(d -> ViewModel.Row.of(d.codeString(), d.toString(), d.severity(),
                                    List.of()))
                            .toList()));
        }
        return new ViewModel("SweetCookie add-ons", sections);
    }

    /** The block pool and this world's ledger. SC-120 §6. */
    public static ViewModel pool(BlockPool blockPool, Optional<BlockLedger> ledger) {
        SlotPool pool = blockPool.pool();
        List<ViewModel.Row> classes = new ArrayList<>();
        pool.capacities().forEach((sizeClass, count) -> classes.add(ViewModel.Row.of(
                "class " + sizeClass, count + " blocks, " + (sizeClass * count) + " states")));

        List<ViewModel.Section> sections = new ArrayList<>();
        sections.add(ViewModel.Section.of(
                blockPool.size() + " blocks, " + pool.totalStates() + " states registered", classes));

        // The ledger is per world, so "no world loaded" is a state worth showing rather than an
        // empty section that reads as "nothing is bound".
        List<ViewModel.Row> bindings = new ArrayList<>();
        if (ledger.isEmpty()) {
            bindings.add(ViewModel.Row.empty("no world loaded"));
        } else if (ledger.get().bindings().isEmpty()) {
            bindings.add(ViewModel.Row.empty("no blocks bound in this world"));
        } else {
            for (BlockLedger.Binding binding : ledger.get().bindings()) {
                String detail = binding.slot() + ", " + binding.schema().size() + " states";
                if (!binding.previousSchemas().isEmpty()) {
                    // Schema drift is the thing most worth surfacing: it means placed blocks were
                    // remapped, which is invisible in game and permanent in the save.
                    detail += ", " + binding.previousSchemas().size() + " earlier schema(s)";
                }
                boolean unresolved = blockPool.block(binding.slot()).isEmpty();
                bindings.add(unresolved
                        ? ViewModel.Row.of(binding.logicalId(), detail, Severity.ERROR,
                                List.of("SCE-4013 outside the registered pool; raise"
                                        + " sweetcookie.blockPool." + binding.slot().sizeClass()
                                        + " and restart"))
                        : ViewModel.Row.of(binding.logicalId(), detail));
            }
        }
        sections.add(ViewModel.Section.of("ledger", bindings));
        return new ViewModel("SweetCookie block pool", sections);
    }
}
