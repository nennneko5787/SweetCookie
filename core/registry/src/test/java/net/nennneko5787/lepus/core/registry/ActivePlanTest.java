package net.nennneko5787.lepus.core.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.nennneko5787.lepus.core.format.value.PackId;
import net.nennneko5787.lepus.core.format.value.SemanticVersion;
import org.junit.jupiter.api.Test;

class ActivePlanTest {

    private static PackId pack(String name) {
        return PackId.derived(name);
    }

    private static final PackId A = pack("alpha");
    private static final PackId B = pack("beta");
    private static final PackId C = pack("gamma");
    private static final PackId D = pack("delta");

    private static ActivePacks packs(PackId... order) {
        ActivePacks active = ActivePacks.NONE;
        for (PackId pack : order) {
            active = active.enable(pack, SemanticVersion.ZERO);
        }
        return active;
    }

    /** Every plan must actually produce what it was asked for. */
    private static void assertReaches(ActivePacks current, List<PackId> desired) {
        ActivePlan plan = ActivePlan.between(current, desired);
        assertEquals(desired, plan.applyTo(current, id -> SemanticVersion.ZERO).order(),
                "plan did not reach the requested order: " + plan.steps());
    }

    @Test
    void anUnchangedSelectionCostsNothing() {
        // Opening the screen and closing it must not write the activation file or say anything in
        // chat. This is the commonest interaction there is.
        assertTrue(ActivePlan.between(packs(A, B, C), List.of(A, B, C)).isEmpty());
    }

    @Test
    void movingOnePackIsOneStep() {
        ActivePlan plan = ActivePlan.between(packs(A, B, C), List.of(A, C, B));
        assertEquals(1, plan.steps().size(), plan.steps().toString());
        assertReaches(packs(A, B, C), List.of(A, C, B));
    }

    @Test
    void aPackDroppedFromTheSelectionIsDisabled() {
        assertEquals(List.of(new ActivePlan.Disable(B)),
                ActivePlan.between(packs(A, B, C), List.of(A, C)).steps());
    }

    @Test
    void aPackAddedAtTheEndIsJustEnabled() {
        // enable() appends, so a pack that belongs at the end needs no move after it.
        assertEquals(List.of(new ActivePlan.Enable(D)),
                ActivePlan.between(packs(A, B, C), List.of(A, B, C, D)).steps());
    }

    @Test
    void aPackAddedInTheMiddleIsEnabledThenMoved() {
        ActivePlan plan = ActivePlan.between(packs(A, B), List.of(A, D, B));
        assertEquals(List.of(new ActivePlan.Enable(D), new ActivePlan.Order(D, 1)), plan.steps());
        assertReaches(packs(A, B), List.of(A, D, B));
    }

    @Test
    void aReversalReachesItsTarget() {
        assertReaches(packs(A, B, C, D), List.of(D, C, B, A));
    }

    @Test
    void everythingReplacedAtOnceReachesItsTarget() {
        assertReaches(packs(A, B), List.of(C, D));
    }

    @Test
    void clearingTheSelectionDisablesEverything() {
        assertEquals(List.of(new ActivePlan.Disable(A), new ActivePlan.Disable(B)),
                ActivePlan.between(packs(A, B), List.of()).steps());
    }

    @Test
    void enablingFromNothingReachesItsTarget() {
        assertReaches(ActivePacks.NONE, List.of(A, B, C));
    }

    // --- spliceKind: committing one tab must not disable the other kind (SC-280 §5.2) ---

    private static final java.util.Set<PackId> BEHAVIOUR = java.util.Set.of(A, C);
    private static final java.util.Set<PackId> RESOURCE = java.util.Set.of(B, D);

    @Test
    void committingOneTabLeavesTheOtherKindAlone() {
        // The bug this exists to prevent: the behaviour tab returns only behaviour packs, and a
        // plan built straight from it disables every resource pack in the world.
        List<PackId> merged =
                ActivePlan.spliceKind(List.of(A, B, C, D), List.of(A, C), BEHAVIOUR);
        assertEquals(List.of(A, B, C, D), merged);
        assertTrue(ActivePlan.between(packs(A, B, C, D), merged).isEmpty());
    }

    @Test
    void reorderingWithinATabRewritesOnlyThatKindsSlots() {
        assertEquals(List.of(C, B, A, D),
                ActivePlan.spliceKind(List.of(A, B, C, D), List.of(C, A), BEHAVIOUR));
    }

    @Test
    void deselectingInATabClosesUpItsSlots() {
        // C takes the earliest of the kind's slots and the last one closes up, so C ends up ahead
        // of B. That is not a reorder anyone asked for in any sense that matters: B is the other
        // kind, and precedence between a behaviour pack and a resource pack decides nothing. What
        // must be preserved is the order within a kind, and C is the only one left.
        assertEquals(List.of(C, B, D),
                ActivePlan.spliceKind(List.of(A, B, C, D), List.of(C), BEHAVIOUR));
    }

    @Test
    void selectingMoreThanThereWereSlotsAppendsAtTheEnd() {
        // Where ActivePacks.enable would have put it, so the screen and the command agree.
        assertEquals(List.of(A, B, D, C),
                ActivePlan.spliceKind(List.of(A, B, D), List.of(A, C), BEHAVIOUR));
    }

    @Test
    void aTabWithNothingEnabledYetStillPlacesItsSelection() {
        assertEquals(List.of(B, D, A),
                ActivePlan.spliceKind(List.of(B, D), List.of(A), BEHAVIOUR));
    }

    @Test
    void theOtherTabSeesTheSameActivationTheOppositeWayRound() {
        assertEquals(List.of(A, D, C, B),
                ActivePlan.spliceKind(List.of(A, B, C, D), List.of(D, B), RESOURCE));
    }

    @Test
    void disablesComeBeforeTheMovesTheyShorten() {
        // If a move ran before the disable, its position would be counted against a list that still
        // contained a pack about to leave, and would land one place out.
        assertReaches(packs(A, B, C, D), List.of(C, A));
    }
}
