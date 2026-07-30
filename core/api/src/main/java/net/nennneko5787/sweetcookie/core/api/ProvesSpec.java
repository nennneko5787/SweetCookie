package net.nennneko5787.sweetcookie.core.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test as proving a specification item.
 *
 * <p>The counterpart to {@link SpecImpl}. A coverage entry reaches {@code implemented} only when a
 * conformance case proves it and that case passes; a human never writes that status by hand
 * (constitution rule 9, {@code spec/process.md} section 4).
 *
 * <pre>{@code
 * @ProvesSpec("SC-160#minecraft:behavior.melee_attack")
 * void meleeAttack_stopsWhenTargetDies() { }
 * }</pre>
 *
 * <p>Named {@code ProvesSpec} rather than {@code SpecTest} deliberately. This is production API — it
 * lives in {@code src/main} because test source sets consume it and because {@code specLinks} reads
 * it out of bytecode — and a file called {@code SpecTest.java} sitting under {@code main} reads as a
 * test filed in the wrong directory. It was misread that way once, which was enough.
 *
 * @see SpecImpl
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ProvesSpec {

    /** One or more specification identifiers. See {@code spec/ids.md}. */
    String[] value();

    /**
     * The conformance case this test executes, as a path under {@code spec/conformance/}
     * — for example {@code "entity/melee_attack_basic"}.
     *
     * <p>Empty for a unit test that proves a specification item without a corpus case.
     */
    String conformance() default "";
}
