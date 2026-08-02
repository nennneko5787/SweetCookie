package net.nennneko5787.lepus.core.format.ir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.nennneko5787.lepus.core.api.ProvesSpec;
import net.nennneko5787.lepus.core.format.diag.Diagnostics;
import net.nennneko5787.lepus.core.format.json.Json;
import net.nennneko5787.lepus.core.format.json.JsonObject;
import net.nennneko5787.lepus.core.format.value.BedrockVersion;
import net.nennneko5787.lepus.core.format.value.PackId;
import net.nennneko5787.lepus.core.format.value.Provenance;
import org.junit.jupiter.api.Test;

/** Parser selection, SC-110 §3.1 — the three rules and nothing else. */
@ProvesSpec("SC-110")
class ParserRegistryTest {

    private static final Provenance WHERE = Provenance.file(PackId.NONE, "x.json");

    private Diagnostics diagnostics = new Diagnostics();

    /** A registry whose parsers each just report which one ran. */
    private ParserRegistry<String> ladder() {
        return new ParserRegistry<String>("test")
                .register(BedrockVersion.of(1, 8, 0), (root, ctx) -> Optional.of("v1.8"))
                .register(BedrockVersion.of(1, 12, 0), (root, ctx) -> Optional.of("v1.12"))
                .register(BedrockVersion.of(1, 21, 0), (root, ctx) -> Optional.of("v1.21"));
    }

    private String select(ParserRegistry<String> registry, String json) {
        diagnostics = new Diagnostics();
        JsonObject root = Json.parse(json).asObject().orElseThrow();
        return registry.parse(root, "format_version", WHERE, diagnostics).orElseThrow();
    }

    private boolean reported(int code) {
        return !diagnostics.snapshot().withCode(code).isEmpty();
    }

    @Test
    @ProvesSpec("SC-110")
    void usesTheHighestRegisteredVersionNotExceedingTheDeclaredOne() {
        // Bedrock's own semantics: "authored against version V". A parser registered at 1.21.0
        // handles a file declaring 1.21.40 until somebody registers a 1.21.40 parser.
        assertEquals("v1.21", select(ladder(), "{\"format_version\": \"1.21.40\"}"));
        assertEquals("v1.12", select(ladder(), "{\"format_version\": \"1.16.0\"}"));
        assertEquals("v1.8", select(ladder(), "{\"format_version\": \"1.8.0\"}"));
        assertFalse(reported(IrDiagnostics.VERSION_BELOW_LOWEST.code()));
    }

    @Test
    @ProvesSpec("SC-110")
    void fallsBackToTheLowestParserAndSaysSo() {
        assertEquals("v1.8", select(ladder(), "{\"format_version\": \"1.0.0\"}"));
        assertTrue(reported(IrDiagnostics.VERSION_BELOW_LOWEST.code()));
    }

    @Test
    @ProvesSpec("SC-110")
    void reportsAFileThatDeclaresNoVersionAtAll() {
        assertEquals("v1.8", select(ladder(), "{}"));
        assertTrue(reported(IrDiagnostics.VERSION_BELOW_LOWEST.code()));
    }

    @Test
    @ProvesSpec("SC-110")
    void aSniffOverridesAContradictingDeclaration() {
        // Rule 3, and the reason it is a correctness requirement rather than an optimisation:
        // trusting the declaration means silently failing to load a large fraction of real content.
        ParserRegistry<String> registry = ladder().sniffer(root ->
                root.has("modern_marker") ? Optional.of(BedrockVersion.of(1, 21, 0)) : Optional.empty());

        assertEquals("v1.21",
                select(registry, "{\"format_version\": \"1.8.0\", \"modern_marker\": true}"));
        assertTrue(reported(IrDiagnostics.VERSION_SNIFFED.code()));
    }

    @Test
    @ProvesSpec("SC-110")
    void aSniffThatAgreesWithTheDeclarationIsSilent() {
        // Otherwise every correctly-declared file in a pack emits a diagnostic, and the channel
        // that reports the genuinely mislabelled ones becomes unreadable.
        ParserRegistry<String> registry = ladder().sniffer(root ->
                root.has("modern_marker") ? Optional.of(BedrockVersion.of(1, 21, 0)) : Optional.empty());

        assertEquals("v1.21",
                select(registry, "{\"format_version\": \"1.21.0\", \"modern_marker\": true}"));
        assertFalse(reported(IrDiagnostics.VERSION_SNIFFED.code()));
    }

    @Test
    @ProvesSpec("SC-110")
    void aSnifferMayAbstain() {
        ParserRegistry<String> registry = ladder().sniffer(root -> Optional.empty());
        assertEquals("v1.12", select(registry, "{\"format_version\": \"1.12.0\"}"));
        assertFalse(reported(IrDiagnostics.VERSION_SNIFFED.code()));
    }

    @Test
    @ProvesSpec("SC-110")
    void recordsBothVersionsOnTheProvenanceItHandsTheParser() {
        // A diagnostic deeper in the file has to be able to say what the file claimed and what it
        // was read as, without the parser threading that through itself.
        ParserRegistry<Provenance> registry = new ParserRegistry<Provenance>("test")
                .register(BedrockVersion.of(1, 8, 0), (root, ctx) -> Optional.of(ctx.provenance()))
                .register(BedrockVersion.of(1, 12, 0), (root, ctx) -> Optional.of(ctx.provenance()))
                .sniffer(root -> root.has("modern_marker")
                        ? Optional.of(BedrockVersion.of(1, 12, 0)) : Optional.empty());

        JsonObject root = Json.parse("{\"format_version\": \"1.8.0\", \"modern_marker\": true}")
                .asObject().orElseThrow();
        Provenance where = registry.parse(root, "format_version", WHERE, new Diagnostics())
                .orElseThrow();

        assertEquals("1.8.0", where.declaredVersion());
        assertEquals("1.12.0", where.effectiveVersion());
        assertTrue(where.versionOverridden());
        assertTrue(where.lossy());
    }

    @Test
    @ProvesSpec("SC-110")
    void anEmptyRegistrySaysSoRatherThanReturningNothingQuietly() {
        Diagnostics into = new Diagnostics();
        Optional<String> result = new ParserRegistry<String>("test")
                .parse(JsonObject.EMPTY, "format_version", WHERE, into);

        assertTrue(result.isEmpty());
        assertFalse(into.snapshot().withCode(IrDiagnostics.NO_PARSER.code()).isEmpty());
    }
}
