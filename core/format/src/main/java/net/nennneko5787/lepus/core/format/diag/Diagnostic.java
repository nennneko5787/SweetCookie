package net.nennneko5787.lepus.core.format.diag;

import java.util.List;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.value.Provenance;

/**
 * One coded, located, translatable report of something Lepus did to an add-on that Bedrock
 * would not have done. SC-240 §1.
 *
 * <p>Constitution rule 1 says bad input never crashes. The price of that rule is that bugs hide as
 * silent no-ops, and this type is what pays it: every degradation, refusal, clamp and unsupported
 * construct becomes one of these.
 *
 * <p>{@code messageKey} is a translation key and never a formatted string, so the in-game surface
 * can be localised while the log stays English. {@code args} are the substitution arguments, and
 * they are also what a machine-readable diagnostics dump exposes.
 *
 * <p>{@code featureId} is the Bedrock feature identifier this concerns — the bare form, without the
 * {@code SC-nnn#} prefix, because the emitting site already knows its own document. It is what links
 * a user's report to a coverage entry, and therefore what turns bug reports into a demand signal
 * rather than an argument.
 *
 * @param code      the {@code SCE-nnnn} number, without the prefix
 * @param severity  where this surfaces, SC-240 §2
 * @param messageKey a translation key; never a pre-formatted sentence
 * @param args      substitution arguments, in order
 * @param where     the pack, file and JSON pointer, when one applies
 * @param featureId the Bedrock feature concerned, when one applies
 */
@SpecImpl("SC-240")
public record Diagnostic(
        int code,
        Severity severity,
        String messageKey,
        List<Object> args,
        Optional<Provenance> where,
        Optional<String> featureId) {

    public Diagnostic {
        if (code < 1000 || code > 9999) {
            throw new IllegalArgumentException("diagnostic code out of range: " + code);
        }
        args = List.copyOf(args);
    }

    /** The user-facing code, e.g. {@code SCE-1032}. Users search the internet for this string. */
    public String codeString() {
        return "SCE-" + code;
    }

    /**
     * The default deduplication key, SC-240 §3: one report per code per location per load.
     *
     * <p>A filter with an unknown test runs every tick, per entity. Without this a single unknown
     * construct produces a log nobody reads, which is the same as no log at all.
     */
    public Object dedupKey() {
        return List.of(code, where.<Object>map(p -> p).orElse(""));
    }

    /**
     * What went wrong, in the words the reporter used. SC-240 §3.
     *
     * <p>The arguments and nothing else: no code, no severity, and <b>no translation key</b>. A key
     * is an identifier for a sentence this build does not have yet, and putting it in front of a
     * player says nothing while looking like it should.
     *
     * <p>Until SC-240's message table exists, the arguments are the message. They are the concrete
     * half anyway — the file, the field, the line and column — and a reader who has the code has
     * everything the key would have told them.
     */
    public String describe() {
        return args.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", "));
    }

    /**
     * A one-line rendering for logs and test failures.
     *
     * <p>It <b>is</b> a user-facing surface, whatever an earlier revision of this comment claimed:
     * the pack screen and the text views all print it. So the arguments are joined rather than
     * handed to {@code List.toString}, which put square brackets and commas in front of anyone
     * whose pack had a malformed file.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(codeString())
                .append(' ').append(severity)
                .append(' ').append(messageKey);
        if (!args.isEmpty()) {
            sb.append(' ').append(describe());
        }
        where.ifPresent(p -> sb.append(" at ").append(p));
        return sb.toString();
    }
}
