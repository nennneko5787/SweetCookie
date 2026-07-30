package net.nennneko5787.sweetcookie.core.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as the implementation of a specification item.
 *
 * <p>The value is a specification identifier, optionally suffixed with the Bedrock feature
 * identifier it implements:
 *
 * <pre>{@code
 * @SpecImpl("SC-160#minecraft:behavior.melee_attack")
 * public final class MeleeAttackGoal extends BedrockGoal { }
 * }</pre>
 *
 * <p>This is not documentation. {@code specImplCheck} verifies, in both directions, that the
 * specification exists, that the feature has a coverage entry, that every coverage entry claiming
 * more than {@code stub} names a class carrying this annotation, and that no annotation names a
 * feature with no coverage entry. A coverage ledger nobody verifies is worse than none, because it
 * turns user bug reports into arguments — constitution rule 9.
 *
 * <p>Retention is {@code CLASS} rather than {@code RUNTIME}: the check reads bytecode at build
 * time, and there is no reason to carry this into the running game.
 *
 * @see ProvesSpec
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface SpecImpl {

    /**
     * One or more specification identifiers, each {@code SC-nnn} or
     * {@code SC-nnn#<bedrock feature id>}. See {@code spec/ids.md}.
     */
    String[] value();

    /**
     * Free-text note about how this implementation diverges from Bedrock.
     *
     * <p>Informative only — the authoritative statement is the {@code fidelity} field of the
     * coverage entry, because that is what is published to users. Use this for detail that helps a
     * reader of the code but does not belong in a compatibility table.
     */
    String note() default "";
}
