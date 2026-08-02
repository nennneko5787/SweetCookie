package net.nennneko5787.lepus.core.format.json;

/**
 * Bounds the reader applies to untrusted input. SC-260.
 *
 * <p>Only nesting depth so far, because that is the one an attacker controls cheaply: a few kilobytes
 * of {@code [[[[[…} } overflows a recursive-descent parser's stack, and a {@code StackOverflowError}
 * thrown from a mixin during world load is not recoverable in the way constitution rule 1 requires.
 * Size limits live in the archive layer (SC-100 §3), where they can abort before anything is read
 * into memory.
 *
 * @param maxDepth the deepest nesting of objects and arrays accepted
 */
public record JsonLimits(int maxDepth) {

    /**
     * 256. Deep enough for JSON UI, which is the deepest real Bedrock content by a wide margin, and
     * shallow enough that the recursion cannot exhaust a default thread stack.
     */
    public static final JsonLimits DEFAULT = new JsonLimits(256);

    public JsonLimits {
        if (maxDepth < 1) {
            throw new IllegalArgumentException("maxDepth must be positive: " + maxDepth);
        }
    }
}
