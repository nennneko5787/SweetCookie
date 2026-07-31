package net.nennneko5787.sweetcookie.core.molang;

import java.util.Set;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;

/**
 * A parsed, folded, ready-to-evaluate Molang expression. SC-110 §7, SC-130.
 *
 * <p>Produced once at ingest and evaluated many times — per bone, per frame, per visible entity. The
 * body is a tree of {@link Op} closures built at compile time, so evaluating costs virtual dispatch
 * over a fixed shape: no node inspection, no {@code instanceof}, no name lookup on the hot path
 * (ADR-0013).
 *
 * <p>A field that Bedrock allows to be an expression is one of these in the IR, <b>never a
 * string</b>. Parse errors then surface at load with provenance rather than mid-frame with none,
 * constants fold once instead of per frame, and {@link #referencedQueries()} makes the set of
 * queries a pack uses known statically — which is what allows pre-binding.
 */
@SpecImpl("SC-110")
public final class MolangExpr {

    /** One compiled operation. Package-private: the tree's shape is an implementation detail. */
    @FunctionalInterface
    interface Op {
        float apply(MolangContext context);
    }

    private static final MolangExpr ZERO = constant(0f);

    private final Op op;
    private final String source;
    private final boolean constant;
    private final float constantValue;
    private final Set<String> referencedQueries;
    private final Set<String> unresolved;

    MolangExpr(Op op, String source, boolean constant, float constantValue,
            Set<String> referencedQueries, Set<String> unresolved) {
        this.op = op;
        this.source = source;
        this.constant = constant;
        this.constantValue = constantValue;
        this.referencedQueries = Set.copyOf(referencedQueries);
        this.unresolved = Set.copyOf(unresolved);
    }

    /**
     * Compiles {@code source}.
     *
     * @throws MolangSyntaxException if it cannot be read. SC-110 §7 requires the caller to turn that
     *     into {@code constant(0)} plus a diagnostic naming the pack, file and column — Bedrock also
     *     yields 0 for a bad expression, so this matches once the diagnostic is raised.
     */
    public static MolangExpr compile(String source) {
        return new MolangCompiler(source).compile();
    }

    /** A literal. Also what a failed compile degrades to, once the caller has reported it. */
    public static MolangExpr constant(float value) {
        return new MolangExpr(ctx -> value, Float.toString(value), true, value, Set.of(), Set.of());
    }

    public static MolangExpr zero() {
        return ZERO;
    }

    public float evaluate(MolangContext context) {
        return op.apply(context);
    }

    /** True when the value cannot change, so a caller can evaluate it once and forget the tree. */
    public boolean isConstant() {
        return constant;
    }

    /** Only meaningful when {@link #isConstant()}. */
    public float constantValue() {
        return constantValue;
    }

    /** The expression as the pack wrote it, for diagnostics. */
    public String source() {
        return source;
    }

    /** Query names this expression reads, lower-cased. Known statically, for pre-binding. */
    public Set<String> referencedQueries() {
        return referencedQueries;
    }

    /**
     * Names that compiled to a constant 0 because nothing could resolve them.
     *
     * <p>The caller reports these. An unresolvable name is indistinguishable at runtime from one
     * that legitimately returned zero, so if it is not reported here it is never reported at all —
     * which is the failure constitution rule 8 exists to prevent, and precisely what the measured
     * library did.
     */
    public Set<String> unresolved() {
        return unresolved;
    }

    @Override
    public String toString() {
        return "MolangExpr[" + source + "]";
    }
}
