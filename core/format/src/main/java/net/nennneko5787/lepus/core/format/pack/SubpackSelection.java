package net.nennneko5787.lepus.core.format.pack;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.manifest.SubpackDecl;

/**
 * Which {@code header.subpacks[]} variant is active. SC-100 §7.
 *
 * <p><b>Selection is configuration, never inference.</b> One memory tier is 0.25 GiB on Bedrock, and
 * Lepus must not read the host's RAM to pick one: a Java client and a dedicated server have
 * unrelated memory characteristics, and a server choosing a texture resolution on a client's behalf
 * is wrong in a multiplayer context whichever way it guesses.
 *
 * <p>The default ceiling is the highest tier the pack offers, so an unconfigured install gets the
 * pack's best variant — which is what an author who shipped an HD subpack intended.
 *
 * @param available every declared subpack, in declared order
 * @param selected  the active one, or empty when the pack declares none
 * @param ceiling   the configured tier ceiling that produced {@code selected}
 */
@SpecImpl({"SC-100", "SC-100#manifest/subpacks"})
public record SubpackSelection(
        List<SubpackDecl> available, Optional<SubpackDecl> selected, int ceiling) {

    public static final SubpackSelection NONE =
            new SubpackSelection(List.of(), Optional.empty(), 0);

    public SubpackSelection {
        available = List.copyOf(available);
    }

    /**
     * Picks the highest-tier subpack not exceeding {@code ceiling}.
     *
     * @param ceiling the configured ceiling, or empty to use the highest tier offered
     */
    public static SubpackSelection choose(List<SubpackDecl> available, OptionalInt ceiling) {
        if (available.isEmpty()) {
            return NONE;
        }
        int highest = available.stream().mapToInt(SubpackDecl::memoryTier).max().orElse(0);
        int limit = ceiling.orElse(highest);
        Optional<SubpackDecl> chosen = available.stream()
                .filter(s -> s.memoryTier() <= limit)
                // Ties break on declaration order, which the max-by comparator keeps by returning
                // the first of equal elements only if the comparison is strict. It is.
                .max(Comparator.comparingInt(SubpackDecl::memoryTier));
        return new SubpackSelection(available, chosen, limit);
    }

    /** Where variants live. Hidden from the resolved view once one has been selected. */
    public static final String SUBPACK_ROOT = "subpacks";

    /**
     * Lays the selected subpack over {@code root}, remapped to root-relative, and hides
     * {@code subpacks/}.
     *
     * <p>An overlay rather than a copy, so that reselecting a tier costs nothing.
     *
     * <p>Hiding {@code subpacks/} matters as much as the overlay does. Once a variant is selected,
     * that tree is not content — every variant that was <em>not</em> selected is still in it, and an
     * asset walk that saw them would register {@code subpacks/sd/textures/a.png} alongside the
     * {@code textures/a.png} the player was actually meant to get. A pack declaring no subpacks is
     * unaffected, because it has no such directory to hide.
     */
    public PackVfs applyTo(PackVfs root) {
        if (available.isEmpty()) {
            return root;
        }
        PackVfs withoutVariants = new ExcludingVfs(root, SUBPACK_ROOT);
        return selected
                .map(subpack -> LayeredVfs.over(root.rooted(subpack.path()), withoutVariants))
                .orElse(withoutVariants);
    }
}
