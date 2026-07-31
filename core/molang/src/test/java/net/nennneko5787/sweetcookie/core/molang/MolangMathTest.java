package net.nennneko5787.sweetcookie.core.molang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.nennneko5787.sweetcookie.core.api.ProvesSpec;
import org.junit.jupiter.api.Test;

/** Bedrock's 61 {@code math.*} functions. SC-130 §5, ADR-0013. */
@ProvesSpec("SC-130")
class MolangMathTest {

    private static final MolangContext CTX = MolangContext.standalone(20260731L);

    private static float eval(String source) {
        return MolangExpr.compile(source).evaluate(CTX);
    }

    /** The names {@code spec/coverage/molang-math.yaml} tracks, without the {@code math.} prefix. */
    private static final Set<String> LEDGER = new TreeSet<>(List.of(
            "abs", "acos", "asin", "atan", "atan2", "ceil", "clamp", "copy_sign", "cos",
            "die_roll", "die_roll_integer", "exp", "floor", "hermite_blend", "inverse_lerp",
            "lerp", "lerprotate", "ln", "max", "min", "min_angle", "mod", "pi", "pow", "random",
            "random_integer", "round", "sign", "sin", "sqrt", "trunc",
            "ease_in_back", "ease_in_bounce", "ease_in_circ", "ease_in_cubic", "ease_in_elastic",
            "ease_in_expo", "ease_in_quad", "ease_in_quart", "ease_in_quint", "ease_in_sine",
            "ease_out_back", "ease_out_bounce", "ease_out_circ", "ease_out_cubic",
            "ease_out_elastic", "ease_out_expo", "ease_out_quad", "ease_out_quart",
            "ease_out_quint", "ease_out_sine",
            "ease_in_out_back", "ease_in_out_bounce", "ease_in_out_circ", "ease_in_out_cubic",
            "ease_in_out_elastic", "ease_in_out_expo", "ease_in_out_quad", "ease_in_out_quart",
            "ease_in_out_quint", "ease_in_out_sine"));

    @Test
    @ProvesSpec("SC-130")
    void bindsEveryFunctionTheLedgerTracks() {
        assertEquals(61, LEDGER.size(), "the ledger tracks 61 math functions");
        assertEquals(LEDGER, new TreeSet<>(MolangMathBinding.names()),
                "the binding table and the coverage ledger must name the same set");
    }

    @Test
    @ProvesSpec("SC-130")
    void trigonometryIsInDegrees() {
        // Java's Math is radians and Bedrock's Molang is degrees. Getting this wrong produces
        // plausible motion at the wrong speed rather than an obvious failure, which is worse.
        assertEquals(1f, eval("math.sin(90)"), 1e-6f);
        assertEquals(0f, eval("math.cos(90)"), 1e-6f);
        assertEquals(90f, eval("math.asin(1)"), 1e-4f);
        assertEquals(45f, eval("math.atan(1)"), 1e-4f);
        assertEquals(45f, eval("math.atan2(1, 1)"), 1e-4f);
    }

    @Test
    @ProvesSpec("SC-130")
    void modIsNonNegativeForAPositiveDivisor() {
        // Java's % keeps the dividend's sign: -1 % 3 is -1 where Bedrock gives 2. Packs use mod to
        // cycle frames, so the difference is a one-frame glitch at every wrap.
        assertEquals(2f, eval("math.mod(-1, 3)"));
        assertEquals(1f, eval("math.mod(5, 2)"));
        assertEquals(0f, eval("math.mod(5, 0)"), "a zero divisor yields 0 rather than throwing");
    }

    @Test
    @ProvesSpec("SC-130")
    void truncatesTowardZero() {
        assertEquals(1f, eval("math.trunc(1.9)"));
        assertEquals(-1f, eval("math.trunc(-1.9)"), "toward zero, not toward negative infinity");
        assertEquals(-2f, eval("math.floor(-1.9)"));
    }

    @Test
    @ProvesSpec("SC-130")
    void minAngleWrapsToTheShortWayRound() {
        assertEquals(10f, eval("math.min_angle(370)"), 1e-4f);
        assertEquals(-90f, eval("math.min_angle(270)"), 1e-4f);
        // lerprotate goes the short way: 350 to 10 passes through 0, not through 180. The midpoint
        // comes out as 360, which is the same angle - whether Bedrock normalises the RESULT into
        // (-180, 180] is not something a parse-level test can establish, so this asserts the
        // property that matters and leaves the representation alone.
        assertEquals(0f, eval("math.min_angle(math.lerprotate(350, 10, 0.5))"), 1e-3f);
        assertEquals(180f, eval("math.min_angle(math.lerprotate(170, 190, 0.5))"), 1e-3f);
    }

    @Test
    @ProvesSpec("SC-130")
    void interpolationHelpersAgree() {
        assertEquals(0.5f, eval("math.lerp(0, 1, 0.5)"));
        assertEquals(0.5f, eval("math.inverse_lerp(0, 2, 1)"));
        assertEquals(0f, eval("math.inverse_lerp(1, 1, 5)"), "a zero range yields 0, not infinity");
        assertEquals(0.5f, eval("math.hermite_blend(0.5)"));
        assertEquals(1f, eval("math.clamp(5, 0, 1)"));
        assertEquals(-3f, eval("math.copy_sign(3, -1)"));
        assertEquals(-1f, eval("math.sign(-2)"));
    }

    @Test
    @ProvesSpec("SC-130")
    void everyEasingCurveRunsFromZeroToOne() {
        // The property a pack relies on when easing between two poses: f(0) == 0 and f(1) == 1.
        // Asserting it across all thirty catches a transcription slip in any one of them.
        for (String shape : List.of("sine", "quad", "cubic", "quart", "quint", "expo", "circ",
                "back", "elastic", "bounce")) {
            for (String direction : List.of("in", "out", "in_out")) {
                String name = "math.ease_" + direction + "_" + shape;
                assertEquals(0f, eval(name + "(0)"), 1e-5f, name + "(0)");
                assertEquals(1f, eval(name + "(1)"), 1e-5f, name + "(1)");
                float middle = eval(name + "(0.5)");
                assertTrue(Float.isFinite(middle), name + "(0.5) is finite");
            }
        }
    }

    @Test
    @ProvesSpec("SC-130")
    void easingDirectionsAreDistinctAndMonotoneWhereTheyShouldBe() {
        // ease_in starts slow, ease_out starts fast. Without this, in and out could be swapped and
        // every f(0)/f(1) assertion above would still pass.
        assertTrue(eval("math.ease_in_cubic(0.25)") < 0.25f);
        assertTrue(eval("math.ease_out_cubic(0.25)") > 0.25f);
        assertEquals(0.5f, eval("math.ease_in_out_cubic(0.5)"), 1e-5f);
    }

    @Test
    @ProvesSpec("SC-130")
    void randomIsSeededSoAConformanceCaseCanPinIt() {
        // An expression whose value changes per run cannot be pinned by a golden, so the source is
        // injected rather than global.
        MolangContext a = MolangContext.standalone(42L);
        MolangContext b = MolangContext.standalone(42L);
        assertEquals(MolangExpr.compile("math.random(0, 100)").evaluate(a),
                MolangExpr.compile("math.random(0, 100)").evaluate(b));

        assertEquals(5f, eval("math.random(5, 5)"), "a zero-width range is its endpoint");
        assertEquals(3f, eval("math.random_integer(3, 3)"));
        assertEquals(6f, eval("math.die_roll(3, 2, 2)"), "three dice, each necessarily 2");
    }

    @Test
    @ProvesSpec("SC-130")
    void randomDoesNotFoldAwayAtCompileTime() {
        // Pure functions fold; these must not, or every entity would share one roll.
        assertTrue(MolangExpr.compile("math.random(0, 100)").isConstant() == false);
        assertTrue(MolangExpr.compile("math.floor(1.9)").isConstant());
    }
}
