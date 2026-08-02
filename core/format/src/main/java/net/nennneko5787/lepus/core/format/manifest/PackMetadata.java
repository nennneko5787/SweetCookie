package net.nennneko5787.lepus.core.format.manifest;

import java.util.List;
import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * {@code metadata}. SC-100 §4.2. Recorded verbatim, never interpreted.
 *
 * <p>{@code generatedWith} is the one that earns its place: it names the authoring tool and its
 * version, so a diagnostic pattern that only ever appears in packs from one tool is identifiable as
 * a tool bug rather than a format question.
 *
 * @param authors       {@code metadata.authors}
 * @param license       {@code metadata.license}
 * @param url           {@code metadata.url}
 * @param productType   {@code metadata.product_type}
 * @param generatedWith {@code metadata.generated_with}, as tool name to versions
 */
@SpecImpl("SC-100")
public record PackMetadata(
        List<String> authors,
        String license,
        String url,
        String productType,
        List<String> generatedWith) {

    public static final PackMetadata EMPTY =
            new PackMetadata(List.of(), "", "", "", List.of());

    public PackMetadata {
        authors = List.copyOf(authors);
        generatedWith = List.copyOf(generatedWith);
        license = license == null ? "" : license;
        url = url == null ? "" : url;
        productType = productType == null ? "" : productType;
    }
}
