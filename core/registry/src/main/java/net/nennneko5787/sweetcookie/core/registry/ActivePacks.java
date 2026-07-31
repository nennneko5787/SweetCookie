package net.nennneko5787.sweetcookie.core.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.value.PackId;
import net.nennneko5787.sweetcookie.core.format.value.SemanticVersion;

/**
 * Which packs one world uses, and in what order. SC-120 §8.
 *
 * <p><b>Order is authoritative</b> for SC-100 §5's override precedence, and <b>later wins</b> — the
 * last entry in this list overrides everything before it. That is the same direction as SC-110 §9.1
 * and the opposite of what Java Edition's resource-pack screen shows, where the topmost selected
 * pack wins. Whichever direction is chosen, the screen has to say so in words; a user who guesses
 * wrong silently gets the other pack's texture.
 *
 * <p>Immutable. Every operation returns a new value, so a failed activation (SC-120 §8 step 1: a
 * parse error aborts the change) leaves the previous set live by construction rather than by
 * remembering to roll back.
 *
 * @param entries in precedence order, lowest first
 */
@SpecImpl("SC-120")
public record ActivePacks(List<Entry> entries) {

    /**
     * One activated pack.
     *
     * <p>The version is recorded as well as the identity so that a world can tell "the pack I
     * activated" from "a different version of it that happens to be installed now". Nothing acts on
     * that yet; recording it from the first release is what makes acting on it possible later.
     */
    public record Entry(PackId pack, SemanticVersion version) {
    }

    public static final ActivePacks NONE = new ActivePacks(List.of());

    public ActivePacks {
        entries = List.copyOf(entries);
    }

    public boolean isEnabled(PackId pack) {
        return entries.stream().anyMatch(entry -> entry.pack().equals(pack));
    }

    /** Position in precedence order, or empty when not enabled. */
    public Optional<Integer> orderOf(PackId pack) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).pack().equals(pack)) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    /**
     * Enables a pack at the end, where it overrides everything already enabled.
     *
     * <p>The end rather than the start: a user enabling a pack almost always wants to see it, and a
     * pack that silently lost to one enabled earlier would look broken. Re-enabling an
     * already-enabled pack updates its recorded version and leaves its position alone, because
     * moving it would change what overrides what without being asked.
     */
    public ActivePacks enable(PackId pack, SemanticVersion version) {
        List<Entry> updated = new ArrayList<>(entries);
        Optional<Integer> existing = orderOf(pack);
        if (existing.isPresent()) {
            updated.set(existing.get(), new Entry(pack, version));
        } else {
            updated.add(new Entry(pack, version));
        }
        return new ActivePacks(updated);
    }

    /** Disables a pack. Disabling one that is not enabled is not an error. */
    public ActivePacks disable(PackId pack) {
        List<Entry> updated = new ArrayList<>(entries);
        updated.removeIf(entry -> entry.pack().equals(pack));
        return new ActivePacks(updated);
    }

    /**
     * Moves an enabled pack to {@code index}, clamped into range.
     *
     * <p>Clamped rather than refused: "move it to the top" is naturally expressed as a number past
     * the end, and a user who types one should get the top rather than an error message.
     */
    public ActivePacks moveTo(PackId pack, int index) {
        Optional<Integer> from = orderOf(pack);
        if (from.isEmpty()) {
            return this;
        }
        List<Entry> updated = new ArrayList<>(entries);
        Entry entry = updated.remove((int) from.get());
        updated.add(Math.max(0, Math.min(index, updated.size())), entry);
        return new ActivePacks(updated);
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** The identities, in precedence order — what SC-100 §5 takes as its explicit order. */
    public List<PackId> order() {
        return entries.stream().map(Entry::pack).toList();
    }
}
