package net.nennneko5787.sweetcookie.core.molang;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;

/**
 * What an expression can read and write while it evaluates. SC-130 §3.
 *
 * <p>The single most important fact here is that {@code variable.*} lives <b>per entity</b> and is
 * shared between animations, render controllers and particles. It is how packs communicate between
 * those subsystems, and getting the sharing wrong breaks packs in ways that look like rendering
 * bugs rather than like state bugs.
 *
 * <p>Everything is {@code float} (ADR-0013), and an undefined name reads as 0 rather than throwing —
 * Bedrock's behaviour, and constitution rule 1.
 */
@SpecImpl("SC-130")
public interface MolangContext {

    /** Molang's scopes. The short forms {@code q. v. t. c.} resolve to the same ones. */
    enum Scope {
        /** Read-only engine state. */
        QUERY,
        /** Per entity, persists across frames, shared between subsystems. */
        VARIABLE,
        /** One expression evaluation. */
        TEMP,
        /** Supplied by whatever is evaluating. */
        CONTEXT
    }

    /**
     * Whether {@code name} has a value.
     *
     * <p>Needed by {@code ??}, which is the one construct that can tell "absent" from "zero". Every
     * other site treats them alike.
     */
    boolean isDefined(Scope scope, String name);

    /** The value, or 0 when undefined. */
    float read(Scope scope, String name);

    /** Writes. Only {@link Scope#VARIABLE} and {@link Scope#TEMP} are writable; others ignore. */
    void write(Scope scope, String name, float value);

    /**
     * Calls a query with arguments.
     *
     * <p>Separate from {@link #read} because Molang lets one name be both — {@code query.foo} and
     * {@code query.foo(1)} are the same query with different arity, and a binding needs to see the
     * argument count to answer.
     */
    float call(Scope scope, String name, float[] arguments);

    /** The random source, so a conformance case can seed it and pin an expression's value. */
    MolangMath math();

    /** A context with no engine state: variables and temporaries work, queries all read 0. */
    static MolangContext standalone() {
        return new Standalone(new MolangMath(new java.util.Random()));
    }

    /** As {@link #standalone()}, with a seeded random source. */
    static MolangContext standalone(long seed) {
        return new Standalone(new MolangMath(new java.util.Random(seed)));
    }

    /**
     * Variables and temporaries only.
     *
     * <p>This is the whole server-side context for block permutation conditions (SC-130 §4.1), and
     * the base every richer context extends.
     */
    final class Standalone implements MolangContext {

        private final Map<String, Float> variables = new HashMap<>();
        private final Map<String, Float> temps = new HashMap<>();
        private final MolangMath math;

        Standalone(MolangMath math) {
            this.math = math;
        }

        private Map<String, Float> mapFor(Scope scope) {
            return switch (scope) {
                case VARIABLE -> variables;
                case TEMP -> temps;
                case QUERY, CONTEXT -> null;
            };
        }

        @Override
        public boolean isDefined(Scope scope, String name) {
            Map<String, Float> map = mapFor(scope);
            return map != null && map.containsKey(key(name));
        }

        @Override
        public float read(Scope scope, String name) {
            Map<String, Float> map = mapFor(scope);
            if (map == null) {
                return 0f;
            }
            Float value = map.get(key(name));
            return value == null ? 0f : value;
        }

        @Override
        public void write(Scope scope, String name, float value) {
            Map<String, Float> map = mapFor(scope);
            if (map != null) {
                map.put(key(name), value);
            }
        }

        @Override
        public float call(Scope scope, String name, float[] arguments) {
            return 0f;
        }

        @Override
        public MolangMath math() {
            return math;
        }

        /** Names fold case-insensitively, like every other Molang identifier (SC-130 §2.1). */
        private static String key(String name) {
            return name.toLowerCase(Locale.ROOT);
        }
    }
}
