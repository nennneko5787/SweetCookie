package net.nennneko5787.sweetcookie.core.molang;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;

/**
 * Binds Bedrock's {@code math.*} names to {@link MolangMath}. SC-130 §5.
 *
 * <p>One table, so that "which functions exist" is a question with a single answer a test can
 * enumerate — the alternative is discovering, as the measurement in SC-130 §2.6 did of another
 * implementation, that a name is bound but computes something else.
 *
 * <p>{@code pure} marks a function whose result depends only on its arguments, and therefore folds
 * at compile time. The random family is the only exception.
 *
 * @param arity how many arguments the name takes
 * @param pure  whether a call with constant arguments can be folded
 * @param fn    the implementation
 */
@SpecImpl("SC-130")
record MolangMathBinding(int arity, boolean pure, Impl fn) {

    @FunctionalInterface
    interface Impl {
        float apply(MolangContext context, float[] args);
    }

    private static final Map<String, MolangMathBinding> BY_NAME = new LinkedHashMap<>();

    private static void pure(String name, int arity, Impl fn) {
        BY_NAME.put(name, new MolangMathBinding(arity, true, fn));
    }

    private static void impure(String name, int arity, Impl fn) {
        BY_NAME.put(name, new MolangMathBinding(arity, false, fn));
    }

    private static void ease(String shape, Impl in, Impl out, Impl inOut) {
        pure("ease_in_" + shape, 1, in);
        pure("ease_out_" + shape, 1, out);
        pure("ease_in_out_" + shape, 1, inOut);
    }

    static {
        pure("abs", 1, (c, a) -> MolangMath.abs(a[0]));
        pure("acos", 1, (c, a) -> MolangMath.acos(a[0]));
        pure("asin", 1, (c, a) -> MolangMath.asin(a[0]));
        pure("atan", 1, (c, a) -> MolangMath.atan(a[0]));
        pure("atan2", 2, (c, a) -> MolangMath.atan2(a[0], a[1]));
        pure("ceil", 1, (c, a) -> MolangMath.ceil(a[0]));
        pure("clamp", 3, (c, a) -> MolangMath.clamp(a[0], a[1], a[2]));
        pure("copy_sign", 2, (c, a) -> MolangMath.copySign(a[0], a[1]));
        pure("cos", 1, (c, a) -> MolangMath.cos(a[0]));
        pure("exp", 1, (c, a) -> MolangMath.exp(a[0]));
        pure("floor", 1, (c, a) -> MolangMath.floor(a[0]));
        pure("hermite_blend", 1, (c, a) -> MolangMath.hermiteBlend(a[0]));
        pure("inverse_lerp", 3, (c, a) -> MolangMath.inverseLerp(a[0], a[1], a[2]));
        pure("lerp", 3, (c, a) -> MolangMath.lerp(a[0], a[1], a[2]));
        pure("lerprotate", 3, (c, a) -> MolangMath.lerpRotate(a[0], a[1], a[2]));
        pure("ln", 1, (c, a) -> MolangMath.ln(a[0]));
        pure("max", 2, (c, a) -> MolangMath.max(a[0], a[1]));
        pure("min", 2, (c, a) -> MolangMath.min(a[0], a[1]));
        pure("min_angle", 1, (c, a) -> MolangMath.minAngle(a[0]));
        pure("mod", 2, (c, a) -> MolangMath.mod(a[0], a[1]));
        pure("pi", 0, (c, a) -> MolangMath.PI);
        pure("pow", 2, (c, a) -> MolangMath.pow(a[0], a[1]));
        pure("round", 1, (c, a) -> MolangMath.round(a[0]));
        pure("sign", 1, (c, a) -> MolangMath.sign(a[0]));
        pure("sin", 1, (c, a) -> MolangMath.sin(a[0]));
        pure("sqrt", 1, (c, a) -> MolangMath.sqrt(a[0]));
        pure("trunc", 1, (c, a) -> MolangMath.trunc(a[0]));

        impure("random", 2, (c, a) -> c.math().random(a[0], a[1]));
        impure("random_integer", 2, (c, a) -> c.math().randomInteger(a[0], a[1]));
        impure("die_roll", 3, (c, a) -> c.math().dieRoll(a[0], a[1], a[2]));
        impure("die_roll_integer", 3, (c, a) -> c.math().dieRollInteger(a[0], a[1], a[2]));

        ease("sine",
                (c, a) -> MolangMath.easeInSine(a[0]),
                (c, a) -> MolangMath.easeOutSine(a[0]),
                (c, a) -> MolangMath.easeInOutSine(a[0]));
        ease("quad",
                (c, a) -> MolangMath.easeInQuad(a[0]),
                (c, a) -> MolangMath.easeOutQuad(a[0]),
                (c, a) -> MolangMath.easeInOutQuad(a[0]));
        ease("cubic",
                (c, a) -> MolangMath.easeInCubic(a[0]),
                (c, a) -> MolangMath.easeOutCubic(a[0]),
                (c, a) -> MolangMath.easeInOutCubic(a[0]));
        ease("quart",
                (c, a) -> MolangMath.easeInQuart(a[0]),
                (c, a) -> MolangMath.easeOutQuart(a[0]),
                (c, a) -> MolangMath.easeInOutQuart(a[0]));
        ease("quint",
                (c, a) -> MolangMath.easeInQuint(a[0]),
                (c, a) -> MolangMath.easeOutQuint(a[0]),
                (c, a) -> MolangMath.easeInOutQuint(a[0]));
        ease("expo",
                (c, a) -> MolangMath.easeInExpo(a[0]),
                (c, a) -> MolangMath.easeOutExpo(a[0]),
                (c, a) -> MolangMath.easeInOutExpo(a[0]));
        ease("circ",
                (c, a) -> MolangMath.easeInCirc(a[0]),
                (c, a) -> MolangMath.easeOutCirc(a[0]),
                (c, a) -> MolangMath.easeInOutCirc(a[0]));
        ease("back",
                (c, a) -> MolangMath.easeInBack(a[0]),
                (c, a) -> MolangMath.easeOutBack(a[0]),
                (c, a) -> MolangMath.easeInOutBack(a[0]));
        ease("elastic",
                (c, a) -> MolangMath.easeInElastic(a[0]),
                (c, a) -> MolangMath.easeOutElastic(a[0]),
                (c, a) -> MolangMath.easeInOutElastic(a[0]));
        ease("bounce",
                (c, a) -> MolangMath.easeInBounce(a[0]),
                (c, a) -> MolangMath.easeOutBounce(a[0]),
                (c, a) -> MolangMath.easeInOutBounce(a[0]));
    }

    static MolangMathBinding byName(String name) {
        return BY_NAME.get(name.toLowerCase(Locale.ROOT));
    }

    /** Every bound name, for the test that checks all 61 of Bedrock's are present. */
    static Set<String> names() {
        return Set.copyOf(BY_NAME.keySet());
    }

    float apply(MolangContext context, float[] args) {
        return fn.apply(context, args);
    }
}
