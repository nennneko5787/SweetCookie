package net.nennneko5787.sweetcookie.core.format.pack;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.manifest.SubpackDecl;

/**
 * Which {@code header.subpacks[]} variant is active. SC-100 §7.
 *
 * <p><b>Selection is configuration, never inference.</b> One memory tier is 0.25 GiB on Bedrock, and
 * SweetCookie must not read the host's RAM to pick one: a Java client and a dedicated server have
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
@SpecImpl("SC-100")
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

    /**
     * Lays the selected subpack over {@code root}, remapped to root-relative.
     *
     * <p>An overlay rather than a copy, so that reselecting a tier costs nothing.
     */
    public PackVfs applyTo(PackVfs root) {
        return selected
                .map(subpack -> LayeredVfs.over(root.rooted(subpack.path()), root))
                .orElse(root);
    }
}
