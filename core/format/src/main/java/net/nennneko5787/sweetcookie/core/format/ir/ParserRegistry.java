package net.nennneko5787.sweetcookie.core.format.ir;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.diag.Diagnostics;
import net.nennneko5787.sweetcookie.core.format.json.JsonObject;
import net.nennneko5787.sweetcookie.core.format.value.BedrockVersion;
import net.nennneko5787.sweetcookie.core.format.value.Provenance;

/**
 * Chooses which {@link FormatParser} reads a file, and is the whole of SC-110 §3.1.
 *
 * <p>Three rules, in order:
 *
 * <ol>
 *   <li>use the <b>highest registered version not exceeding</b> the declared one. Bedrock's own
 *       semantics are "this file was authored against version V", so a parser registered at 1.21.0
 *       handles a file declaring 1.21.40 until somebody registers a 1.21.40 parser;
 *   <li>if the declared version is below the lowest registered parser, use the lowest and emit
 *       {@code SCE-1030};
 *   <li><b>a structural sniff overrides a declared version that contradicts the file's shape.</b>
 * </ol>
 *
 * <p>Rule 3 is not an optimisation. Real packs declare {@code 1.8.0} on files written in the
 * {@code 1.12.0} geometry shape and the reverse, and the authoring tools have shipped that bug for
 * years — trusting the declaration means silently failing to load a large fraction of published
 * content, with bones that parse as empty and an entity that renders as nothing at all.
 *
 * <p>A sniffer returns the version it believes the file really is, or empty to abstain. When it
 * disagrees with the declaration, {@code SCE-1031} records <b>both</b>, because the author needs to
 * know their file says something it is not.
 */
@SpecImpl("SC-110")
public final class ParserRegistry<T> {

    /**
     * Recognises a file by shape.
     *
     * <p>Abstaining (returning empty) is the normal answer for a file whose shape is compatible with
     * every registered version. A sniffer must not guess.
     */
    @FunctionalInterface
    public interface Sniffer {
        Optional<BedrockVersion> sniff(JsonObject root);
    }

    private final String kind;
    private final NavigableMap<BedrockVersion, FormatParser<T>> parsers = new TreeMap<>();
    private final List<Sniffer> sniffers = new ArrayList<>();

    /** @param kind names the content kind in diagnostics, e.g. {@code geometry} */
    public ParserRegistry(String kind) {
        this.kind = kind;
    }

    public ParserRegistry<T> register(BedrockVersion since, FormatParser<T> parser) {
        parsers.put(since, parser);
        return this;
    }

    public ParserRegistry<T> sniffer(Sniffer sniffer) {
        sniffers.add(sniffer);
        return this;
    }

    /** The registered versions, lowest first. Exposed so a test can assert the ladder is complete. */
    public List<BedrockVersion> registeredVersions() {
        return List.copyOf(parsers.navigableKeySet());
    }

    /**
     * Reads {@code root} with whichever parser the rules above select.
     *
     * @param declaredVersionKey the member holding the declared version, normally
     *     {@code format_version}
     */
    public Optional<T> parse(
            JsonObject root,
            String declaredVersionKey,
            Provenance file,
            Diagnostics diagnostics) {

        if (parsers.isEmpty()) {
            diagnostics.report(IrDiagnostics.NO_PARSER.at(file, kind));
            return Optional.empty();
        }

        String declaredText = root.get(declaredVersionKey)
                .flatMap(value -> value.asString()
                        .or(() -> value.asArray().map(a -> join(a.floats()))))
                .orElse("");
        Optional<BedrockVersion> declared = BedrockVersion.tryParse(declaredText);

        Optional<BedrockVersion> sniffed = sniffers.stream()
                .map(sniffer -> sniffer.sniff(root))
                .flatMap(Optional::stream)
                .findFirst();

        BedrockVersion effective;
        boolean lossy = false;
        if (sniffed.isPresent()) {
            effective = sniffed.get();
            if (declared.isPresent() && !selectFor(declared.get()).equals(selectFor(effective))) {
                // Both versions, deliberately. "This file is not what it says" is only actionable if
                // the author can see what it says and what it is.
                diagnostics.report(IrDiagnostics.VERSION_SNIFFED.at(
                        file, kind, declaredText, effective.toString()));
                lossy = true;
            }
        } else if (declared.isPresent()) {
            effective = declared.get();
            if (effective.compareTo(parsers.firstKey()) < 0) {
                diagnostics.report(IrDiagnostics.VERSION_BELOW_LOWEST.at(
                        file, kind, declaredText, parsers.firstKey().toString()));
            }
        } else {
            // No declaration and nothing recognised the shape. The lowest parser is the one whose
            // shape is the oldest and therefore the most likely to still read something.
            diagnostics.report(IrDiagnostics.VERSION_BELOW_LOWEST.at(
                    file, kind, declaredText.isEmpty() ? "<absent>" : declaredText,
                    parsers.firstKey().toString()));
            effective = parsers.firstKey();
        }

        BedrockVersion selected = selectFor(effective);
        Provenance where = file.withVersions(
                declaredText.isEmpty() ? "" : declaredText, effective.toString());
        if (lossy) {
            where = where.markLossy();
        }
        return parsers.get(selected).parse(root, new ParseContext(where, diagnostics, effective));
    }

    /** Rule 1 and 2: the highest registered version not exceeding {@code version}. */
    private BedrockVersion selectFor(BedrockVersion version) {
        BedrockVersion floor = parsers.floorKey(version);
        return floor != null ? floor : parsers.firstKey();
    }

    private static String join(List<Float> parts) {
        StringBuilder sb = new StringBuilder();
        for (Float part : parts) {
            if (!sb.isEmpty()) {
                sb.append('.');
            }
            sb.append((int) (float) part);
        }
        return sb.toString();
    }

    /** Builds a registry. Present so a domain can name its ladder in one expression. */
    public static <T> ParserRegistry<T> of(
            String kind, Function<ParserRegistry<T>, ParserRegistry<T>> configure) {
        return configure.apply(new ParserRegistry<>(kind));
    }
}
