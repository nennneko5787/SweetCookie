package net.nennneko5787.lepus.core.format.ir.block;

import java.util.Locale;
import java.util.Map;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.value.BedrockId;
import net.nennneko5787.lepus.core.molang.MolangContext;
import net.nennneko5787.lepus.core.molang.MolangMath;
import net.nennneko5787.lepus.core.molang.MolangStrings;

/**
 * The Molang context a block permutation condition evaluates in. SC-130 §4.1, SC-150 §3.
 *
 * <p>Deliberately tiny. A permutation condition may read <b>block state and pure maths, and nothing
 * else</b> — no entity, no world, no time. That restriction is what makes the whole permutation set
 * pre-resolvable per index at bind time instead of evaluated per block access, so widening this
 * context later would quietly turn a bind-time table into a per-access Molang evaluation inside
 * {@code getShape}.
 *
 * <p>A query outside that set reads 0 rather than throwing, matching Bedrock and constitution
 * rule 1. It is also visible before it ever runs: the compiler records every referenced query name
 * on the expression, so a condition reaching for entity state is reportable at load.
 */
@SpecImpl("SC-150")
final class BlockStateContext implements MolangContext {

    private final BlockStateSchema schema;
    private final Map<BedrockId, String> values;
    private final MolangMath math = new MolangMath(new java.util.Random(0L));

    BlockStateContext(BlockStateSchema schema, Map<BedrockId, String> values) {
        this.schema = schema;
        this.values = values;
    }

    @Override
    public boolean isDefined(Scope scope, String name) {
        return scope == Scope.QUERY && isStateQuery(name);
    }

    @Override
    public float read(Scope scope, String name) {
        return 0f;
    }

    @Override
    public void write(Scope scope, String name, float value) {
        // A permutation condition is a predicate. Bedrock allows the syntax and the write cannot
        // outlive the evaluation, so accepting and discarding it is closer than refusing.
    }

    @Override
    public float call(Scope scope, String name, float[] arguments) {
        if (scope != Scope.QUERY || !isStateQuery(name) || arguments.length != 1) {
            return 0f;
        }
        // The argument arrives as an interned identity because Molang is float-typed; this is what
        // MolangStrings' reverse lookup exists for.
        String stateName = MolangStrings.text(arguments[0]).orElse(null);
        if (stateName == null) {
            return 0f;
        }
        BedrockId id = BedrockId.parse(stateName);
        String value = values.get(id);
        if (value == null) {
            return 0f;
        }
        return asFloat(id, value);
    }

    /**
     * A state's value as Molang sees it.
     *
     * <p>An integer state answers with its number and a boolean with 0 or 1, so that
     * {@code q.block_state('level') > 2} means what it looks like. A string state answers with the
     * same interned identity a literal compiles to, so {@code q.block_state('kind') == 'tall'}
     * compares equal — which is the only operation a string state is ever used with.
     */
    private float asFloat(BedrockId name, String value) {
        BlockStateIr state = schema.state(name).orElse(null);
        if (state == null) {
            return 0f;
        }
        return switch (state.kind()) {
            case BOOLEAN -> Boolean.parseBoolean(value) ? 1f : 0f;
            case INTEGER -> parseOrZero(value);
            case STRING -> MolangStrings.intern(value);
        };
    }

    private static float parseOrZero(String value) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException notANumber) {
            return 0f;
        }
    }

    private static boolean isStateQuery(String name) {
        String needle = name.toLowerCase(Locale.ROOT);
        return needle.equals("block_state") || needle.equals("block_property");
    }

    @Override
    public MolangMath math() {
        return math;
    }
}
