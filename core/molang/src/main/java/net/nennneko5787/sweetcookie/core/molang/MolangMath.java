package net.nennneko5787.sweetcookie.core.molang;

import java.util.Random;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;

/**
 * Bedrock's {@code math.*} library, in {@code float}. SC-130.
 *
 * <p>All 61 of them, ours (ADR-0013). Every one takes and returns {@code float}: computing in
 * {@code double} and narrowing at the end rounds twice, and the second rounding is what moves a
 * value across a comparison boundary.
 *
 * <p><b>Angles are degrees</b>, as Bedrock's are, in {@code sin}, {@code cos}, {@code asin},
 * {@code acos}, {@code atan}, {@code atan2}, {@code lerprotate} and {@code min_angle}. Java's
 * {@code Math} is radians throughout, so every one of those converts — a mistake that produces
 * plausible-looking motion at the wrong speed rather than an obvious failure.
 */
@SpecImpl("SC-130")
public final class MolangMath {

    public static final float PI = (float) Math.PI;

    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);
    private static final float RAD_TO_DEG = (float) (180.0 / Math.PI);

    /** Back-easing's overshoot constants, from Penner's originals. */
    private static final float BACK_C1 = 1.70158f;
    private static final float BACK_C2 = BACK_C1 * 1.525f;
    private static final float BACK_C3 = BACK_C1 + 1f;
    private static final float ELASTIC_C4 = (float) (2 * Math.PI / 3);
    private static final float ELASTIC_C5 = (float) (2 * Math.PI / 4.5);
    private static final float BOUNCE_N1 = 7.5625f;
    private static final float BOUNCE_D1 = 2.75f;

    private final Random random;

    /**
     * @param random the source for {@code math.random} and its relatives. Injected rather than
     *     global so a conformance case can seed it: an expression whose value changes per run cannot
     *     be pinned by a golden otherwise.
     */
    public MolangMath(Random random) {
        this.random = random;
    }

    // ── Basic ────────────────────────────────────────────────────────────────────────────────

    public static float abs(float v) {
        return Math.abs(v);
    }

    public static float ceil(float v) {
        return (float) Math.ceil(v);
    }

    public static float floor(float v) {
        return (float) Math.floor(v);
    }

    public static float round(float v) {
        return Math.round(v);
    }

    /** Toward zero, matching Bedrock and SC-000 §7. */
    public static float trunc(float v) {
        return v < 0f ? (float) Math.ceil(v) : (float) Math.floor(v);
    }

    public static float sqrt(float v) {
        return (float) Math.sqrt(v);
    }

    public static float pow(float base, float exponent) {
        return (float) Math.pow(base, exponent);
    }

    public static float exp(float v) {
        return (float) Math.exp(v);
    }

    public static float ln(float v) {
        return (float) Math.log(v);
    }

    public static float max(float a, float b) {
        return Math.max(a, b);
    }

    public static float min(float a, float b) {
        return Math.min(a, b);
    }

    public static float clamp(float v, float low, float high) {
        return v < low ? low : Math.min(v, high);
    }

    /**
     * Remainder, always non-negative for a positive divisor.
     *
     * <p>Java's {@code %} keeps the dividend's sign, so {@code -1 % 3} is {@code -1} where Bedrock
     * gives {@code 2}. Packs use this to cycle through frames and the difference shows up as a
     * one-frame glitch at every wrap.
     */
    public static float mod(float value, float divisor) {
        if (divisor == 0f) {
            return 0f;
        }
        float r = value % divisor;
        return r != 0f && (r < 0f) != (divisor < 0f) ? r + divisor : r;
    }

    public static float sign(float v) {
        return Math.signum(v);
    }

    public static float copySign(float magnitude, float sign) {
        return Math.copySign(magnitude, sign);
    }

    // ── Trigonometry, in degrees ─────────────────────────────────────────────────────────────

    public static float sin(float degrees) {
        return (float) Math.sin(degrees * DEG_TO_RAD);
    }

    public static float cos(float degrees) {
        return (float) Math.cos(degrees * DEG_TO_RAD);
    }

    public static float asin(float v) {
        return (float) Math.asin(v) * RAD_TO_DEG;
    }

    public static float acos(float v) {
        return (float) Math.acos(v) * RAD_TO_DEG;
    }

    public static float atan(float v) {
        return (float) Math.atan(v) * RAD_TO_DEG;
    }

    public static float atan2(float y, float x) {
        return (float) Math.atan2(y, x) * RAD_TO_DEG;
    }

    /** Wraps to (-180, 180]. */
    public static float minAngle(float degrees) {
        float a = mod(degrees + 180f, 360f) - 180f;
        return a == -180f ? 180f : a;
    }

    // ── Interpolation ────────────────────────────────────────────────────────────────────────

    public static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    /** Interpolates the short way round, so 350 to 10 passes through 0 rather than through 180. */
    public static float lerpRotate(float from, float to, float t) {
        return from + minAngle(to - from) * t;
    }

    public static float inverseLerp(float from, float to, float value) {
        return from == to ? 0f : (value - from) / (to - from);
    }

    /** Smoothstep. Bedrock's name for {@code 3t² - 2t³}. */
    public static float hermiteBlend(float t) {
        return t * t * (3f - 2f * t);
    }

    // ── Random ───────────────────────────────────────────────────────────────────────────────

    public float random(float low, float high) {
        return low == high ? low : low + random.nextFloat() * (high - low);
    }

    /** Inclusive of both ends, matching Bedrock. */
    public float randomInteger(float low, float high) {
        int lo = (int) Math.floor(low);
        int hi = (int) Math.floor(high);
        return lo >= hi ? lo : lo + random.nextInt(hi - lo + 1);
    }

    /** The sum of {@code count} rolls, each uniform in {@code [low, high]}. */
    public float dieRoll(float count, float low, float high) {
        int n = (int) Math.floor(count);
        float sum = 0f;
        for (int i = 0; i < n; i++) {
            sum += random(low, high);
        }
        return sum;
    }

    public float dieRollInteger(float count, float low, float high) {
        int n = (int) Math.floor(count);
        float sum = 0f;
        for (int i = 0; i < n; i++) {
            sum += randomInteger(low, high);
        }
        return sum;
    }

    // ── The thirty easing curves ─────────────────────────────────────────────────────────────
    //
    // Penner's set, as Bedrock uses it. Every one satisfies f(0) == 0 and f(1) == 1, which is what
    // the tests assert: it is the property a pack relies on when it eases between two poses.

    public static float easeInSine(float t) {
        return 1f - cos(t * 90f);
    }

    public static float easeOutSine(float t) {
        return sin(t * 90f);
    }

    public static float easeInOutSine(float t) {
        return -(cos(180f * t) - 1f) / 2f;
    }

    public static float easeInQuad(float t) {
        return t * t;
    }

    public static float easeOutQuad(float t) {
        return 1f - (1f - t) * (1f - t);
    }

    public static float easeInOutQuad(float t) {
        return t < 0.5f ? 2f * t * t : 1f - pow(-2f * t + 2f, 2f) / 2f;
    }

    public static float easeInCubic(float t) {
        return t * t * t;
    }

    public static float easeOutCubic(float t) {
        return 1f - pow(1f - t, 3f);
    }

    public static float easeInOutCubic(float t) {
        return t < 0.5f ? 4f * t * t * t : 1f - pow(-2f * t + 2f, 3f) / 2f;
    }

    public static float easeInQuart(float t) {
        return t * t * t * t;
    }

    public static float easeOutQuart(float t) {
        return 1f - pow(1f - t, 4f);
    }

    public static float easeInOutQuart(float t) {
        return t < 0.5f ? 8f * t * t * t * t : 1f - pow(-2f * t + 2f, 4f) / 2f;
    }

    public static float easeInQuint(float t) {
        return t * t * t * t * t;
    }

    public static float easeOutQuint(float t) {
        return 1f - pow(1f - t, 5f);
    }

    public static float easeInOutQuint(float t) {
        return t < 0.5f ? 16f * t * t * t * t * t : 1f - pow(-2f * t + 2f, 5f) / 2f;
    }

    public static float easeInExpo(float t) {
        return t == 0f ? 0f : pow(2f, 10f * t - 10f);
    }

    public static float easeOutExpo(float t) {
        return t == 1f ? 1f : 1f - pow(2f, -10f * t);
    }

    public static float easeInOutExpo(float t) {
        if (t == 0f) {
            return 0f;
        }
        if (t == 1f) {
            return 1f;
        }
        return t < 0.5f ? pow(2f, 20f * t - 10f) / 2f : (2f - pow(2f, -20f * t + 10f)) / 2f;
    }

    public static float easeInCirc(float t) {
        return 1f - sqrt(1f - t * t);
    }

    public static float easeOutCirc(float t) {
        return sqrt(1f - (t - 1f) * (t - 1f));
    }

    public static float easeInOutCirc(float t) {
        return t < 0.5f
                ? (1f - sqrt(1f - pow(2f * t, 2f))) / 2f
                : (sqrt(1f - pow(-2f * t + 2f, 2f)) + 1f) / 2f;
    }

    public static float easeInBack(float t) {
        return BACK_C3 * t * t * t - BACK_C1 * t * t;
    }

    public static float easeOutBack(float t) {
        return 1f + BACK_C3 * pow(t - 1f, 3f) + BACK_C1 * pow(t - 1f, 2f);
    }

    public static float easeInOutBack(float t) {
        return t < 0.5f
                ? pow(2f * t, 2f) * ((BACK_C2 + 1f) * 2f * t - BACK_C2) / 2f
                : (pow(2f * t - 2f, 2f) * ((BACK_C2 + 1f) * (t * 2f - 2f) + BACK_C2) + 2f) / 2f;
    }

    public static float easeInElastic(float t) {
        if (t == 0f || t == 1f) {
            return t;
        }
        return -pow(2f, 10f * t - 10f) * (float) Math.sin((t * 10f - 10.75f) * ELASTIC_C4);
    }

    public static float easeOutElastic(float t) {
        if (t == 0f || t == 1f) {
            return t;
        }
        return pow(2f, -10f * t) * (float) Math.sin((t * 10f - 0.75f) * ELASTIC_C4) + 1f;
    }

    public static float easeInOutElastic(float t) {
        if (t == 0f || t == 1f) {
            return t;
        }
        float s = (float) Math.sin((20f * t - 11.125f) * ELASTIC_C5);
        return t < 0.5f
                ? -(pow(2f, 20f * t - 10f) * s) / 2f
                : pow(2f, -20f * t + 10f) * s / 2f + 1f;
    }

    public static float easeOutBounce(float t) {
        if (t < 1f / BOUNCE_D1) {
            return BOUNCE_N1 * t * t;
        }
        if (t < 2f / BOUNCE_D1) {
            float u = t - 1.5f / BOUNCE_D1;
            return BOUNCE_N1 * u * u + 0.75f;
        }
        if (t < 2.5f / BOUNCE_D1) {
            float u = t - 2.25f / BOUNCE_D1;
            return BOUNCE_N1 * u * u + 0.9375f;
        }
        float u = t - 2.625f / BOUNCE_D1;
        return BOUNCE_N1 * u * u + 0.984375f;
    }

    public static float easeInBounce(float t) {
        return 1f - easeOutBounce(1f - t);
    }

    public static float easeInOutBounce(float t) {
        return t < 0.5f
                ? (1f - easeOutBounce(1f - 2f * t)) / 2f
                : (1f + easeOutBounce(2f * t - 1f)) / 2f;
    }
}
