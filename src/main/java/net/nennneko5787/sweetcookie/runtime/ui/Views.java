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

            // Keyed by the handle commands use, not by the label: the label carries the position
            // number, which changes the instant the row is dragged anywhere.
            ViewModel.Row row = pack.badge()
                    .map(badge -> ViewModel.Row.of(label, detail, badge, notes))
                    .orElseGet(() -> ViewModel.Row.of(label, detail))
                    .keyed(handle);
            if (order.isPresent()) {
                enabled.add(row.with(enabledActions(handle, order.get(), active.get().size())));
            } else {
                // A pack whose activation is unknown gets no actions: offering "enable" against a
                // list we did not compute would be a control that lies about the current state.
                available.add(active.isPresent()
                        ? row.with(List.of(new ViewModel.Action(
                                "enable", 'E', "sweetcookie enable " + handle)))
                        : row);
            }
        }

        List<ViewModel.Section> sections = new ArrayList<>();
        if (addons.packs().isEmpty()) {
            sections.add(ViewModel.Section.of("installed",
                    List.of(ViewModel.Row.empty("no add-ons installed"))));
        } else if (active.isEmpty()) {
            sections.add(ViewModel.Section.of(
                    "installed on this client - the server decides which of these this world uses",
                    available));
        } else {
            // The heading carries the precedence rule. Java Edition's screen puts the direction
            // nowhere, and a user who assumes the wrong end silently gets the other pack's content.
            //
            // Both sections take drops, which is what makes dragging the whole interaction rather
            // than half of one: a pack is enabled by dragging it into the list that is in use and
            // disabled by dragging it out, exactly as the two lists suggest.
            sections.add(ViewModel.Section.of(
                    enabled.isEmpty()
                            ? "enabled in this world - none; drag a pack here to use it"
                            : "enabled in this world, lowest priority first (the last one wins)",
                    enabled,
                    (dragged, position) -> enableAt(dragged, position, currentOrder(enabled))));
            sections.add(ViewModel.Section.of(
                    available.isEmpty() ? "installed but not enabled - none"
                            : "installed but not enabled",
                    available,
                    (dragged, position) -> currentOrder(enabled).contains(dragged.key())
                            ? List.of("sweetcookie disable " + dragged.key())
                            : List.of()));
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

    /** The enabled section's keys, in precedence order — what a drop is measured against. */
    private static List<String> currentOrder(List<ViewModel.Row> enabled) {
        return enabled.stream().map(ViewModel.Row::key).toList();
    }

    /**
     * What dropping a pack into the enabled list means.
     *
     * <p>Three cases, and the arithmetic in the middle one is the whole reason this is not a one
     * liner. {@code order} <b>removes and reinserts</b>, so an index measured against a list that
     * still contains the dragged pack is one too high once the pack it counted past is itself the
     * pack being moved. Getting this wrong moves a pack one place short of where the insertion mark
     * promised, every time it is dragged downwards — the kind of off-by-one a user reads as the
     * screen ignoring them.
     *
     * @param position where the pack landed, counted against the list as drawn
     */
    private static List<String> enableAt(ViewModel.Row dragged, int position, List<String> order) {
        int from = order.indexOf(dragged.key());
        if (from < 0) {
            // Not enabled yet. enable() appends, so it arrives at the end and then moves; two
            // commands, because that is honestly two operations and each reports its own result.
            int target = Math.min(position, order.size());
            return List.of("sweetcookie enable " + dragged.key(),
                    "sweetcookie order " + (target + 1) + " " + dragged.key());
        }
        int target = position > from ? position - 1 : position;
        if (target == from) {
            // Dropped where it already was. Sending a command that changes nothing would still print
            // a confirmation, and a user who nudged the mouse would be told they had reordered.
            return List.of();
        }
        return List.of("sweetcookie order " + (target + 1) + " " + dragged.key());
    }

    /**
     * What an enabled pack can do, in the order a user reaches for them.
     *
     * <p>Dragging is the way to reorder (SC-280 §5.2) and these keys are the same operations reached
     * without a mouse. They are not a second mechanism: both build the same {@code /sweetcookie}
     * command, so there is nothing for them to disagree about.
     *
     * <p>The ends are omitted rather than shown disabled: a key that does nothing is a key a user
     * presses twice before concluding the screen is broken.
     */
    private static List<ViewModel.Action> enabledActions(String handle, int position, int size) {
        List<ViewModel.Action> actions = new ArrayList<>();
        actions.add(new ViewModel.Action("disable", 'D', "sweetcookie disable " + handle));
        if (position + 1 < size) {
            // "raise" moves towards the winning end, which is the end the heading names. Calling it
            // "up" would mean the opposite thing to a user reading the list top-down.
            actions.add(new ViewModel.Action("raise priority", ']',
                    "sweetcookie order " + (position + 2) + " " + handle));
        }
        if (position > 0) {
            actions.add(new ViewModel.Action("lower priority", '[',
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
