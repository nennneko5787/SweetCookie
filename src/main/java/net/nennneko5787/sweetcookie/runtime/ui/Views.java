package net.nennneko5787.sweetcookie.runtime.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.ui.ViewModel;
import net.nennneko5787.sweetcookie.core.format.diag.Diagnostic;
import net.nennneko5787.sweetcookie.core.format.diag.Severity;
import net.nennneko5787.sweetcookie.core.registry.ActivePacks;
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
        return packs(addons, Optional.of(ActivePacks.NONE));
    }

    /**
     * The add-on management view, with this world's activation state.
     *
     * <p>Java Edition's resource-pack screen leaves three things to guess, and this one states all
     * three: <b>which end of the order wins</b>, <b>what a pack actually contains</b>, and <b>why a
     * pack is not doing anything</b>. Guessing the first gets you the other pack's texture with no
     * indication why.
     *
     * @param active this world's pack set, or empty when this process is not the one running the
     *               world — a remote client knows what is installed on its disk and nothing about
     *               what the server enabled, and saying so beats listing everything as disabled
     */
    public static ViewModel packs(AddonRegistry addons, Optional<ActivePacks> active) {

        List<ViewModel.Row> enabled = new ArrayList<>();
        List<ViewModel.Row> available = new ArrayList<>();

        for (PackSummary pack : addons.packs()) {
            Optional<Integer> order = active.flatMap(packs -> packs.orderOf(pack.id()));
            String handle = pack.name().isEmpty() ? pack.id().toString() : pack.name();
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
            if (order.isPresent()) {
                enabled.add(row.with(enabledActions(handle, order.get(), active.get().size())));
            } else {
                // A pack whose activation is unknown gets no commands offered: naming one against a
                // list we did not compute would be advice that does not match the current state.
                available.add(active.isPresent()
                        ? row.with(List.of(new ViewModel.Action(
                                "enable", "sweetcookie enable " + handle)))
                        : row);
            }
        }

        List<ViewModel.Section> sections = new ArrayList<>();
        if (addons.packs().isEmpty()) {
            // "None installed" and "not looked yet" are different answers, and the first one is a
            // claim we cannot make before a scan has run. Saying it anyway is how a user concludes
            // their add-on was rejected when nothing has read the folder at all.
            //
            // And when the folder HAS been read, the useful thing is not that it was empty - it is
            // where to put a file. The path is the whole answer to "how do I install one".
            sections.add(addons.directories().isEmpty()
                    ? ViewModel.Section.of("installed", List.of(ViewModel.Row.empty(
                            "not scanned yet - add-ons are read when a world loads")))
                    : ViewModel.Section.of("installed", addons.directories().stream()
                            .map(path -> ViewModel.Row.of("no add-ons installed",
                                    "put .mcaddon, .mcpack or an unpacked folder in " + path))
                            .toList()));
        } else if (active.isEmpty()) {
            // No claim about a server: this branch is also the title screen, where there is no
            // server to decide anything and the answer is simply that no world is loaded.
            sections.add(ViewModel.Section.of(
                    "installed on this client - a loaded world decides which of these it uses",
                    available));
        } else {
            // The heading carries the precedence rule. This is text, printed lowest-first because
            // that is the direction ActivePacks stores; the selection screen shows the same order
            // reversed, so its title says "the top wins" and this one says "the last one wins".
            // Both are the same fact, stated in the direction the reader is looking.
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

    /**
     * The commands that act on an enabled pack, for whoever is reading text rather than a screen.
     *
     * <p>A client operates this through Minecraft's own selection screen (SC-280 §5.2). A dedicated
     * server's operator has no screen at all, and a list that says a pack can be reordered without
     * saying how would have told them nothing.
     *
     * <p>The ends are omitted: at the top there is nothing to raise past, and printing a command
     * that cannot change anything invites someone to run it and conclude the mod is broken.
     */
    private static List<ViewModel.Action> enabledActions(String handle, int position, int size) {
        List<ViewModel.Action> actions = new ArrayList<>();
        actions.add(new ViewModel.Action("disable", "sweetcookie disable " + handle));
        if (position + 1 < size) {
            // "raise" moves towards the winning end, which is the end the heading names. Calling it
            // "up" would mean the opposite thing to a user reading the list top-down.
            actions.add(new ViewModel.Action("raise priority",
                    "sweetcookie order " + (position + 2) + " " + handle));
        }
        if (position > 0) {
            actions.add(new ViewModel.Action("lower priority",
                    "sweetcookie order " + position + " " + handle));
        }
        return actions;
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
