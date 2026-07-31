package net.nennneko5787.sweetcookie.core.molang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.nennneko5787.sweetcookie.core.api.ProvesSpec;
import org.junit.jupiter.api.Test;
import team.unnamed.mocha.MochaEngine;

/**
 * What {@code team.unnamed:mocha} 3.0.1 actually does, measured rather than assumed. SC-130 §2.6.
 *
 * <p>ADR-0008 chose mocha and left a {@code TODO} to verify its coverage; SC-130 §2.6 said the gap
 * list "needs an experiment, not a guess". This is that experiment, kept as a test rather than run
 * once and written up — so a mocha upgrade that closes a gap <b>fails the build</b> and prompts the
 * shim to be removed, instead of leaving SweetCookie shadowing a function the library now provides.
 *
 * <p>Every assertion describes <b>mocha</b>, not SweetCookie. It found ADR-0008's gap list to be
 * wrong in both directions: the language constructs it flagged as doubtful all work, and the two
 * things that actually diverge — the numeric type and the standard library — were not on it.
 *
 * <p>Reading signatures was not enough to establish any of this. {@code MochaMath} implements
 * {@code ObjectValue} and answers to names through {@code getProperty(String)}, so its list of
 * public static methods understates it by roughly a factor of two; a gap list built from
 * {@code javap} output was wrong about fourteen functions.
 */
@ProvesSpec("SC-130")
class MochaCapabilityTest {

    private static double eval(String source) {
        return MochaEngine.createStandard().eval(source);
    }

    // ── The finding that matters most: mocha is double, Molang is float ──────────────────────

    @Test
    @ProvesSpec("SC-130")
    void mochaEvaluatesInDoubleThroughout() {
        // SC-000 §7 requires float. mocha's whole value model is double - NumberValue.of(double),
        // MochaFunction.evaluate() returns double, the AST literal node is DoubleExpression, and
        // NumberValue.normalize only maps NaN and Infinity to zero rather than narrowing.
        //
        // This is not a rounding nicety. 0.1 + 0.2 > 0.3 is TRUE in double and FALSE in float, so a
        // render controller branching on that comparison takes the other branch.
        assertNotEquals(0.3d, eval("0.1 + 0.2"));
        assertEquals(0.3f, (float) eval("0.1 + 0.2"), "the same sum in float is exactly 0.3f");
        assertEquals(1.0d, eval("(0.1 + 0.2) > 0.3"), "mocha says the sum exceeds 0.3");
        assertTrue(0.1f + 0.2f <= 0.3f, "in float it does not");
    }

    // ── Language surface: complete, including everything ADR-0008 doubted ────────────────────

    @Test
    @ProvesSpec("SC-130")
    void supportsTheWholeOperatorSurface() {
        assertEquals(7.0d, eval("1 + 2 * 3"));
        assertEquals(1.0d, eval("1 < 2"));
        assertEquals(1.0d, eval("!0"));
        assertEquals(1.0d, eval("1 && 1"));
        assertEquals(5.0d, eval("1 ? 5 : 9"));
        assertEquals(9.0d, eval("0 ? 5 : 9"));
    }

    @Test
    @ProvesSpec("SC-130")
    void supportsBinaryIfAndNullCoalescing() {
        // ADR-0008 listed both as unverified. Both work, and binary-if yields 0 when false, which
        // is Bedrock's behaviour.
        assertEquals(5.0d, eval("1 ? 5"));
        assertEquals(0.0d, eval("0 ? 5"));
        assertEquals(3.0d, eval("temp.never_set ?? 3"));
    }

    @Test
    @ProvesSpec("SC-130")
    void supportsStatementsAssignmentAndCaseInsensitivity() {
        assertEquals(9.0d, eval("temp.x = 4; return temp.x + 5;"));
        assertEquals(2.0d, eval("variable.a = 2; return variable.a;"));
        assertEquals(eval("math.floor(1.9)"), eval("Math.Floor(1.9)"));
    }

    // ── Standard library: 36 of Bedrock's 61 math functions are absent ───────────────────────

    /**
     * Exactly the {@code math.*} names mocha does not answer to, measured.
     *
     * <p>Asserted as a set rather than checked one by one, so that a mocha release binding any of
     * them breaks this test and the corresponding shim gets deleted. A shim that silently shadows a
     * working library function is how two implementations of one function drift apart.
     */
    private static final Set<String> EXPECTED_UNBOUND = new TreeSet<>(List.of(
            "math.copy_sign", "math.inverse_lerp", "math.sign",
            "math.ease_in_back", "math.ease_out_back", "math.ease_in_out_back",
            "math.ease_in_bounce", "math.ease_out_bounce", "math.ease_in_out_bounce",
            "math.ease_in_circ", "math.ease_out_circ", "math.ease_in_out_circ",
            "math.ease_in_cubic", "math.ease_out_cubic", "math.ease_in_out_cubic",
            "math.ease_in_elastic", "math.ease_out_elastic", "math.ease_in_out_elastic",
            "math.ease_in_expo", "math.ease_out_expo", "math.ease_in_out_expo",
            "math.ease_in_quad", "math.ease_out_quad", "math.ease_in_out_quad",
            "math.ease_in_quart", "math.ease_out_quart", "math.ease_in_out_quart",
            "math.ease_in_quint", "math.ease_out_quint", "math.ease_in_out_quint",
            "math.ease_in_sine", "math.ease_out_sine", "math.ease_in_out_sine"));

    @Test
    @ProvesSpec("SC-130")
    void bindsTwentyFiveOfBedrocksSixtyOneMathFunctions() {
        MochaEngine<?> engine = MochaEngine.createStandard();
        Set<String> unbound = new TreeSet<>();

        // Arguments chosen so a correct implementation returns non-zero: mocha answers 0 for an
        // unbound name, so a function that legitimately returns 0 is indistinguishable from a
        // missing one.
        record Probe(String name, String call, double expected) {
        }
        List<Probe> probes = new java.util.ArrayList<>(List.of(
                new Probe("math.abs", "math.abs(-3)", 3),
                new Probe("math.acos", "math.acos(0)", 90),
                new Probe("math.asin", "math.asin(1)", 90),
                new Probe("math.atan", "math.atan(1)", 45),
                new Probe("math.atan2", "math.atan2(1, 1)", 45),
                new Probe("math.ceil", "math.ceil(1.1)", 2),
                new Probe("math.clamp", "math.clamp(5, 0, 1)", 1),
                new Probe("math.copy_sign", "math.copy_sign(3, -1)", -3),
                new Probe("math.cos", "math.cos(0)", 1),
                new Probe("math.exp", "math.exp(0)", 1),
                new Probe("math.floor", "math.floor(1.9)", 1),
                new Probe("math.hermite_blend", "math.hermite_blend(1)", 1),
                new Probe("math.inverse_lerp", "math.inverse_lerp(0, 2, 1)", 0.5),
                new Probe("math.lerp", "math.lerp(0, 1, 0.5)", 0.5),
                new Probe("math.lerprotate", "math.lerprotate(0, 90, 0.5)", 45),
                new Probe("math.max", "math.max(1, 2)", 2),
                new Probe("math.min", "math.min(1, 2)", 1),
                new Probe("math.min_angle", "math.min_angle(370)", 10),
                new Probe("math.mod", "math.mod(5, 2)", 1),
                new Probe("math.pow", "math.pow(2, 3)", 8),
                new Probe("math.round", "math.round(1.5)", 2),
                new Probe("math.sign", "math.sign(-2)", -1),
                new Probe("math.sin", "math.sin(90)", 1),
                new Probe("math.sqrt", "math.sqrt(4)", 2),
                new Probe("math.trunc", "math.trunc(1.9)", 1)));
        for (String shape : List.of("back", "bounce", "circ", "cubic", "elastic", "expo", "quad",
                "quart", "quint", "sine")) {
            for (String direction : List.of("in", "out", "in_out")) {
                String name = "math.ease_" + direction + "_" + shape;
                probes.add(new Probe(name, name + "(1)", 1));
            }
        }

        for (Probe probe : probes) {
            double actual;
            try {
                actual = engine.eval(probe.call());
            } catch (RuntimeException notBound) {
                unbound.add(probe.name());
                continue;
            }
            if (actual == 0.0d && probe.expected() != 0.0d) {
                unbound.add(probe.name());
            }
        }

        assertEquals(EXPECTED_UNBOUND, unbound,
                "mocha's math coverage changed. If it GREW, delete the corresponding shim in this "
                        + "module and update SC-130 §2.6; if it SHRANK, a shim is now load-bearing "
                        + "where it was redundant.");
        assertEquals(36, unbound.size() + 3,
                "33 names return 0 and 3 more throw; 61 - 36 = 25 usable");
    }

    @Test
    @ProvesSpec("SC-130")
    void theRandomFamilyThrowsRatherThanReturningAValue() {
        // Constitution rule 1 territory: these must never reach a render frame unguarded. They are
        // reflectively bound and the binding fails at call time rather than at parse time.
        for (String call : List.of(
                "math.random(1, 1)", "math.random_integer(1, 1)", "math.die_roll_integer(1, 2, 2)")) {
            RuntimeException thrown = null;
            try {
                eval(call);
            } catch (RuntimeException e) {
                thrown = e;
            }
            assertTrue(thrown != null, call + " no longer throws; the shim for it can be removed");
        }
    }

    @Test
    @ProvesSpec("SC-130")
    void dieRollIgnoresItsRangeEntirely() {
        // math.die_roll(count, low, high) rolls `count` dice in [low, high]. With low == high == 2
        // every roll must be exactly 2. mocha returns a fresh random value below 1 each time, so
        // the arguments are not reaching the range at all - it is not a rounding difference, it is
        // a different function. Ten samples: a correct implementation cannot produce one below 2.
        MochaEngine<?> engine = MochaEngine.createStandard();
        for (int i = 0; i < 10; i++) {
            double roll = engine.eval("math.die_roll(1, 2, 2)");
            assertTrue(roll < 2.0d,
                    "die_roll now respects its range; the shim for it can be removed");
        }
    }

    // ── Error handling: not what SC-130 §1 assumed ───────────────────────────────────────────

    @Test
    @ProvesSpec("SC-130")
    void reportsSyntaxErrorsOnlyThroughAnOptInHandler() {
        // SC-130 §1 says parse errors surface at load with provenance rather than mid-frame with
        // none. That is true of mocha only if you ask: by DEFAULT a malformed expression evaluates
        // to 0 and says nothing, which is exactly the silent failure constitution rule 8 forbids.
        // SweetCookie must install the handler, and the diagnostic it raises is ours.
        assertEquals(0.0d, eval("("));
        assertEquals(0.0d, eval("math.floor("));

        // The handler receives a message and a position, which is what makes the diagnostic we
        // raise from it actionable.
        assertTrue(reportedFor("(").get(0).startsWith("Non closed expression"));
        assertTrue(reportedFor("(").get(0).contains("line 1, column 1"));
        assertTrue(reportedFor("1 2 3").get(0).startsWith("Expected a semicolon"));
        assertTrue(reportedFor("@@@").get(0).contains("invalid token"));
    }

    @Test
    @ProvesSpec("SC-130")
    void silentlyAcceptsSomeTruncatedExpressions() {
        // The half the handler does NOT cover, and the reason installing it is necessary but not
        // sufficient. A trailing operator is dropped and the expression evaluates as though the
        // author had not typed it; a ternary missing its true branch becomes 0. Neither reaches the
        // handler, so neither can be reported with provenance at ingest - a pack with a truncated
        // expression loads looking healthy.
        assertEquals(1.0d, eval("1 +"), "the trailing + is discarded rather than rejected");
        assertEquals(List.of(), reportedFor("1 +"));
        assertEquals(0.0d, eval("1 ? : 2"));
        assertEquals(List.of(), reportedFor("1 ? : 2"));
    }

    private static List<String> reportedFor(String source) {
        List<String> reported = new java.util.ArrayList<>();
        MochaEngine<?> engine = MochaEngine.createStandard()
                .handleParseExceptions(e -> reported.add(String.valueOf(e.getMessage())));
        try {
            engine.eval(source);
        } catch (RuntimeException ignored) {
            // Some inputs throw instead; the handler content is what this is measuring.
        }
        return reported;
    }

    @Test
    @ProvesSpec("SC-130")
    void yieldsZeroForUnboundNamesRatherThanThrowing() {
        // Matches Bedrock, and is also why the library gap above is dangerous: an unbound math
        // function is indistinguishable from one that legitimately returned zero.
        assertEquals(0.0d, eval("query.nonexistent_thing"));
        assertEquals(0.0d, eval("math.no_such_function(1)"));
    }
}
