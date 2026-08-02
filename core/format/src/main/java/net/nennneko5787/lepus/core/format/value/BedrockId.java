package net.nennneko5787.lepus.core.format.value;

import java.util.Locale;
import java.util.Objects;

import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * A namespaced Bedrock identifier, such as {@code wizardry:magic_wand}.
 *
 * <p>An identifier written without a namespace defaults to {@code minecraft}, matching Bedrock. The
 * original spelling is preserved because it appears verbatim in diagnostics, but comparison and
 * hashing are case-insensitive: Bedrock is authored largely on case-insensitive filesystems and
 * real packs are inconsistent about it.
 *
 * <p>This type deliberately knows nothing about the Java identifier it will become. That mapping is
 * SC-120's, and it lives in exactly one place outside this module.
 */
@SpecImpl("SC-110")
public record BedrockId(String namespace, String path) implements Comparable<BedrockId> {

    /** The namespace assumed when an identifier omits one. */
    public static final String DEFAULT_NAMESPACE = "minecraft";

    public BedrockId {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (namespace.isEmpty()) {
            throw new IllegalArgumentException("namespace must not be empty");
        }
        if (path.isEmpty()) {
            throw new IllegalArgumentException("path must not be empty");
        }
    }

    /**
     * Parses {@code namespace:path}, or {@code path} with the namespace defaulting to
     * {@code minecraft}.
     *
     * <p>Only the <em>first</em> colon separates. Bedrock content does contain further colons in
     * paths — geometry inheritance is written {@code geometry.a:geometry.b} — and splitting on the
     * last colon would silently mangle them.
     *
     * @throws IllegalArgumentException if the text is empty, or has an empty namespace or path
     */
    public static BedrockId parse(String text) {
        Objects.requireNonNull(text, "text");
        int colon = text.indexOf(':');
        if (colon < 0) {
            return new BedrockId(DEFAULT_NAMESPACE, text);
        }
        return new BedrockId(text.substring(0, colon), text.substring(colon + 1));
    }

    /** The identifier as Bedrock writes it, with the namespace always explicit. */
    @Override
    public String toString() {
        return namespace + ':' + path;
    }

    /** Whether this identifier is in the vanilla namespace. */
    public boolean isVanilla() {
        return DEFAULT_NAMESPACE.equalsIgnoreCase(namespace);
    }

    // Case-insensitive equality, with Locale.ROOT so that a Turkish locale cannot change which
    // identifiers collide (SC-000 section 9).

    @Override
    public boolean equals(Object o) {
        return o instanceof BedrockId(String ns, String p)
                && namespace.equalsIgnoreCase(ns)
                && path.equalsIgnoreCase(p);
    }

    @Override
    public int hashCode() {
        return 31 * namespace.toLowerCase(Locale.ROOT).hashCode()
                + path.toLowerCase(Locale.ROOT).hashCode();
    }

    @Override
    public int compareTo(BedrockId other) {
        int byNamespace = namespace.compareToIgnoreCase(other.namespace);
        return byNamespace != 0 ? byNamespace : path.compareToIgnoreCase(other.path);
    }
}
