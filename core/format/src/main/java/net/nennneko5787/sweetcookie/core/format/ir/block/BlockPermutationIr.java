package net.nennneko5787.sweetcookie.core.format.ir.block;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.json.JsonValue;
import net.nennneko5787.sweetcookie.core.format.value.BedrockId;
import net.nennneko5787.sweetcookie.core.molang.MolangContext;
import net.nennneko5787.sweetcookie.core.molang.MolangExpr;

/**
 * One entry of {@code permutations[]}. SC-150 §3.
 *
 * <p>The condition is a compiled {@link MolangExpr}, never a string (SC-110 §7). A permutation whose
 * condition failed to compile is <b>not</b> represented by a condition that always matches or never
 * matches; the parser drops it and reports, because either guess silently changes what the block
 * looks like in half its states.
 *
 * @param condition  the Molang predicate; matches when it evaluates non-zero
 * @param components the components this permutation contributes, keyed by identifier
 */
@SpecImpl("SC-150")
public record BlockPermutationIr(MolangExpr condition, Map<BedrockId, JsonValue> components) {

    public BlockPermutationIr {
        components = Collections.unmodifiableMap(new LinkedHashMap<>(components));
    }

    /**
     * Whether this permutation applies.
     *
     * <p>Bedrock treats any non-zero result as true, including negatives and fractions — the same
     * rule as everywhere else in Molang, and one worth stating because a condition written as
     * {@code q.block_state('level') - 1} is common and means "level is not 1".
     */
    public boolean matches(MolangContext context) {
        return condition.evaluate(context) != 0f;
    }
}
