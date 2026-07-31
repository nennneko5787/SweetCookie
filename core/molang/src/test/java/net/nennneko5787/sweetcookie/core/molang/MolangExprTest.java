package net.nennneko5787.sweetcookie.core.molang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.TreeSet;
import net.nennneko5787.sweetcookie.core.api.ProvesSpec;
import org.junit.jupiter.api.Test;

/** The Molang pipeline. SC-130, ADR-0013. */
@ProvesSpec("SC-130")
class MolangExprTest {

    private static float eval(String source) {
        return MolangExpr.compile(source).evaluate(MolangContext.standalone(1234L));
    }

    private static float eval(String source, MolangContext context) {
        return MolangExpr.compile(source).evaluate(context);
    }

    // ── The reason this pipeline is ours ─────────────────────────────────────────────────────

    @Test
    @ProvesSpec("SC-130")
    void evaluatesInFloatIncludingIntermediates() {
        // The whole point of ADR-0013. In double this comparison is true; Bedrock is float, so it
        // is false, and a render controller branching on it must take the same branch Bedrock does.
        assertEquals(0f, eval("0.1 + 0.2 > 0.3"));
        assertEquals(0.3f, eval("0.1 + 0.2"));
        assertEquals(1f, eval("0.1 + 0.2 == 0.3"));
    }

    @Test
    @ProvesSpec("SC-130")
    void foldsConstantsAtCompileTime() {
        // SC-130 §6's cheapest technique: an expression with no free names costs nothing per frame.
        MolangExpr folded = MolangExpr.compile("math.floor(2.9) * 4 + math.pi * 0");
        assertTrue(folded.isConstant());
        assertEquals(8f, folded.constantValue());

        assertFalse(MolangExpr.compile("query.anim_time * 2").isConstant());
    }

    // ── Operators ────────────────────────────────────────────────────────────────────────────

    @Test
    @ProvesSpec("SC-130#syntax/arithmetic")
    void appliesTheUsualPrecedence() {
        assertEquals(7f, eval("1 + 2 * 3"));
        assertEquals(9f, eval("(1 + 2) * 3"));
        assertEquals(-6f, eval("-2 * 3"));
        assertEquals(2f, eval("6 / 3"));
    }

    @Test
    @ProvesSpec("SC-130#syntax/arithmetic")
    void dividesByZeroWithoutProducingAnInfinity() {
        // An infinity reaching a bone matrix puts the model at no coordinate at all, which renders
        // as the entity vanishing rather than as a visible error.
        assertEquals(0f, eval("1 / 0"));
    }

    @Test
    @ProvesSpec("SC-130#syntax/comparison")
    void comparesAndCombines() {
        assertEquals(1f, eval("1 < 2"));
        assertEquals(0f, eval("2 < 1"));
        assertEquals(1f, eval("2 >= 2"));
        assertEquals(1f, eval("1 && 1"));
        assertEquals(0f, eval("1 && 0"));
        assertEquals(1f, eval("0 || 1"));
        assertEquals(1f, eval("!0"));
        // Bedrock treats any non-zero as true, including negatives and fractions.
        assertEquals(1f, eval("-0.5 ? 1 : 0"));
    }

    @Test
    @ProvesSpec("SC-130")
    void supportsTernaryAndBinaryIf() {
        assertEquals(5f, eval("1 ? 5 : 9"));
        assertEquals(9f, eval("0 ? 5 : 9"));
        // Binary-if: the form packs use for "add this offset only while flying".
        assertEquals(5f, eval("1 ? 5"));
        assertEquals(0f, eval("0 ? 5"));
    }

    @Test
    @ProvesSpec("SC-130")
    void assignsToVariablesAndTemporaries() {
        assertEquals(9f, eval("temp.x = 4; return temp.x + 5;"));
        assertEquals(2f, eval("variable.a = 2; variable.a"));
        // Statements without a trailing `return`, which Bedrock requires and packs omit.
        assertEquals(7f, eval("temp.a = 3; temp.b = 4; temp.a + temp.b"));
    }

    @Test
    @ProvesSpec("SC-130")
    void sharesVariablesAcrossEvaluations() {
        // SC-130 §3: variable.* lives per entity and persists across frames. It is how a render
        // controller and a particle emitter communicate, so the context outlives one expression.
        MolangContext context = MolangContext.standalone(1L);
        eval("variable.charge = 0.75", context);
        assertEquals(0.75f, eval("variable.charge", context));
        assertEquals(1f, eval("variable.charge > 0.5", context));
    }

    @Test
    @ProvesSpec("SC-130")
    void acceptsTheShortScopeForms() {
        MolangContext context = MolangContext.standalone(1L);
        eval("v.x = 3", context);
        assertEquals(3f, eval("v.x", context));
        assertEquals(3f, eval("variable.x", context));
        eval("t.y = 2", context);
        assertEquals(2f, eval("temp.y", context));
    }

    @Test
    @ProvesSpec("SC-130")
    void foldsCaseButNotInsideStrings() {
        assertEquals(eval("math.floor(1.9)"), eval("Math.FLOOR(1.9)"));
        assertEquals(eval("query.is_baby"), eval("QUERY.Is_Baby"));
        // String identities are case-sensitive: they are compared against pack-authored names.
        assertEquals(0f, eval("'Wand' == 'wand'"));
        assertEquals(1f, eval("'wand' == 'wand'"));
    }

    // ── Diagnosability ───────────────────────────────────────────────────────────────────────

    @Test
    @ProvesSpec("SC-130")
    void reportsSyntaxErrorsWithAPosition() {
        // The half the measured third-party library could not do. A truncated expression is a
        // failure at ingest, where there is a file and a column to attach it to.
        MolangSyntaxException e =
                assertThrows(MolangSyntaxException.class, () -> MolangExpr.compile("1 +"));
        assertEquals(1, e.line());
        assertTrue(e.getMessage().contains("column"), e.getMessage());

        assertThrows(MolangSyntaxException.class, () -> MolangExpr.compile("("));
        assertThrows(MolangSyntaxException.class, () -> MolangExpr.compile("1 ? : 2"));
        assertThrows(MolangSyntaxException.class, () -> MolangExpr.compile("math.floor("));
        assertThrows(MolangSyntaxException.class, () -> MolangExpr.compile("@@@"));
    }

    @Test
    @ProvesSpec("SC-130")
    void reportsAnUnknownNameInsteadOfSilentlyReturningZero() {
        // An unbound name is indistinguishable at runtime from one that legitimately returned zero,
        // so if it is not surfaced here it is never surfaced at all.
        MolangExpr expr = MolangExpr.compile("math.no_such_function(1) + nonsense.thing");
        assertEquals(Set.of("math.no_such_function", "nonsense.thing"), expr.unresolved());
        assertEquals(0f, expr.evaluate(MolangContext.standalone(1L)));
    }

    @Test
    @ProvesSpec("SC-130")
    void refusesAWrongArgumentCount() {
        MolangSyntaxException e = assertThrows(MolangSyntaxException.class,
                () -> MolangExpr.compile("math.clamp(1, 2)"));
        assertTrue(e.getMessage().contains("3 argument"), e.getMessage());
    }

    @Test
    @ProvesSpec("SC-130")
    void namesTheQueriesItReferences() {
        // SC-130 §1: knowing this statically is what allows the runtime to pre-bind rather than
        // resolving a name per frame.
        MolangExpr expr = MolangExpr.compile(
                "query.anim_time > 1 ? query.ground_speed : q.is_baby");
        assertEquals(new TreeSet<>(Set.of("anim_time", "ground_speed", "is_baby")),
                new TreeSet<>(expr.referencedQueries()));
    }

    @Test
    @ProvesSpec("SC-130")
    void saysWhatItCannotParseYet() {
        // Better a diagnostic naming the construct than a silently wrong value. SC-130 §2.4, §2.5.
        assertTrue(assertThrows(MolangSyntaxException.class,
                () -> MolangExpr.compile("q.get_nearby_entities(4)->q.health"))
                .getMessage().contains("not supported yet"));
    }
}
