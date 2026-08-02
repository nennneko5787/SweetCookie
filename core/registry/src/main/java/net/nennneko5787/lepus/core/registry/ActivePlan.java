package net.nennneko5787.lepus.core.registry;

import java.util.ArrayList;
import java.util.List;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.value.PackId;
import net.nennneko5787.lepus.core.format.value.SemanticVersion;

/**
 * The smallest set of steps that turns one activation into another. SC-120 §8, SC-280 §7.1.
 *
 * <p>A pack selection screen hands back a whole list — "these packs, in this order" — and every
 * management operation is a command (SC-280 §7.1), so something has to turn the one into the other.
 * Sending the whole list as an enable-and-order per pack would work and would be wrong: it puts two
 * lines of chat per installed pack in front of a user who moved one of them, and it writes the
 * activation file once per step for changes that were never made.
 *
 * <p>So this diffs. Closing the screen without touching anything produces no steps at all, and
 * moving one pack produces one.
 *
 * <p>Steps are operations, not command strings. The command vocabulary belongs to the Minecraft half
 * of the project; what belongs here is knowing which operations are needed, which is the part worth
 * testing.
 */
@SpecImpl({"SC-120", "SC-280"})
public record ActivePlan(List<Step> steps) {

    /** One operation. */
    public sealed interface Step {

        PackId pack();
    }

    public record Disable(PackId pack) implements Step {
    }

    public record Enable(PackId pack) implements Step {
    }

    /** Moves an enabled pack to {@code position}, counted from the lowest priority, zero-based. */
    public record Order(PackId pack, int position) implements Step {
    }

    public static final ActivePlan NOTHING = new ActivePlan(List.of());

    public ActivePlan {
        steps = List.copyOf(steps);
    }

    public boolean isEmpty() {
        return steps.isEmpty();
    }

    /**
     * The steps from {@code current} to {@code desired}.
     *
     * <p>{@code desired} is in precedence order, lowest first — the same direction as
     * {@link ActivePacks} itself.
     *
     * <p>Disables first, then enables, then moves. The order matters: disabling first shortens the
     * list every later position is counted against, and enabling before moving means a newly enabled
     * pack — which {@link ActivePacks#enable} appends — is already present when its position is set.
     *
     * <p>The moves are found by simulating rather than by computing an edit script. Simulating uses
     * the same {@link ActivePacks#moveTo} the commands will use, so a plan cannot disagree with what
     * executing it does; an edit script would be a second implementation of the same arithmetic, and
     * the two would agree until the first time one of them was wrong.
     */
    public static ActivePlan between(ActivePacks current, List<PackId> desired) {
        List<Step> steps = new ArrayList<>();
        ActivePacks state = current;

        for (PackId pack : current.order()) {
            if (!desired.contains(pack)) {
                steps.add(new Disable(pack));
                state = state.disable(pack);
            }
        }
        for (PackId pack : desired) {
            if (!state.isEnabled(pack)) {
                steps.add(new Enable(pack));
                // The version is not part of ordering, and the caller's enable command supplies the
                // real one. ZERO here only stands in for the simulation.
                state = state.enable(pack, SemanticVersion.ZERO);
            }
        }
        for (int position = 0; position < desired.size(); position++) {
            PackId wanted = desired.get(position);
            if (state.order().get(position).equals(wanted)) {
                continue;
            }
            steps.add(new Order(wanted, position));
            state = state.moveTo(wanted, position);
        }
        return new ActivePlan(steps);
    }

    /**
     * The full desired order, when a screen only decided about <b>some</b> of the packs.
     *
     * <p>SC-280 §5.2's tabs show behaviour packs and resource packs separately, so committing one
     * tab says nothing about the other. Handing {@link #between} only the tab's selection would read
     * every absent pack as deselected and disable the entire other kind — which is the failure mode
     * of every "the screen returns the whole state" assumption meeting a screen that returns part of
     * it.
     *
     * <p>So the tab's packs are <b>replaced in place</b>: the slots the old ones occupied are
     * rewritten with the new sequence, and everything else keeps the position it had. Committing an
     * unchanged tab therefore produces no steps at all, which is what makes switching tabs and
     * closing free.
     *
     * @param current   the whole activation, in precedence order
     * @param selection what this tab now has selected, in precedence order
     * @param kind      every installed pack of this tab's kind, selected or not — membership, not
     *                  order, so that a pack the tab did not select is known to be deselected rather
     *                  than merely absent
     */
    public static List<PackId> spliceKind(
            List<PackId> current, List<PackId> selection, java.util.Set<PackId> kind) {
        List<PackId> slots = new ArrayList<>();
        for (int i = 0; i < current.size(); i++) {
            if (kind.contains(current.get(i))) {
                slots.add(current.get(i));
            }
        }
        List<PackId> merged = new ArrayList<>();
        int taken = 0;
        for (PackId pack : current) {
            if (!kind.contains(pack)) {
                merged.add(pack);
            } else if (taken < selection.size()) {
                merged.add(selection.get(taken++));
            }
            // A slot with nothing left to put in it closes up: the tab deselected more than it
            // selected.
        }
        // More selected than there were slots. The extras go at the end, which is where enabling a
        // pack puts it anyway (ActivePacks.enable), so the two routes agree.
        for (int i = taken; i < selection.size(); i++) {
            merged.add(selection.get(i));
        }
        return merged;
    }

    /** Applies the plan, for a caller holding the state directly rather than sending commands. */
    public ActivePacks applyTo(ActivePacks packs, java.util.function.Function<PackId,
            SemanticVersion> versions) {
        ActivePacks state = packs;
        for (Step step : steps) {
            state = switch (step) {
                case Disable disable -> state.disable(disable.pack());
                case Enable enable -> state.enable(enable.pack(), versions.apply(enable.pack()));
                case Order order -> state.moveTo(order.pack(), order.position());
            };
        }
        return state;
    }
}
