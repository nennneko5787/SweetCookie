package net.nennneko5787.lepus.core.format.manifest;

import java.util.Locale;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * A {@code capabilities[]} entry. SC-100 §4.2.
 *
 * <p>All five are {@code unsupported} in 0.x and each produces one {@code SCE-2001} per pack. The
 * enum exists so that a pack declaring one is <em>reported</em> rather than quietly rendering as
 * though the capability were off — which is what a player would otherwise experience as "this pack
 * is broken and nothing says why".
 */
@SpecImpl("SC-100")
public enum Capability {

    CHEMISTRY("chemistry"),
    EDITOR_EXTENSION("editorExtension"),
    EXPERIMENTAL_CUSTOM_UI("experimental_custom_ui"),
    RAYTRACED("raytraced"),
    PBR("pbr");

    private final String declared;

    Capability(String declared) {
        this.declared = declared;
    }

    /** The spelling Mojang uses. Note that {@code editorExtension} is the odd camelCase one. */
    public String declared() {
        return declared;
    }

    /** Case-insensitive, because real manifests are inconsistent about {@code editorExtension}. */
    public static Optional<Capability> parse(String raw) {
        String needle = raw.trim().toLowerCase(Locale.ROOT);
        for (Capability capability : values()) {
            if (capability.declared.toLowerCase(Locale.ROOT).equals(needle)) {
                return Optional.of(capability);
            }
        }
        return Optional.empty();
    }
}
