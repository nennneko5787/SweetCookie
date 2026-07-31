package net.nennneko5787.sweetcookie.core.format.json;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;

/**
 * RFC 6901 JSON pointers, which are how {@code Provenance} says <em>where</em> in a file something
 * is. SC-110 §4.
 *
 * <p>Pointers are built as the parser descends, and only ever from a parent pointer plus one token,
 * so the cost is one string concatenation per described node rather than a walk from the root.
 *
 * <p>A note earned the hard way elsewhere in this project: RFC 6901 addresses <em>positions</em>, not
 * names. It cannot express "the child whose {@code name} member is X". Where that is what is wanted,
 * the answer is a different addressing scheme, not a cleverer pointer.
 */
@SpecImpl("SC-110")
public final class JsonPointer {

    /** The whole document. RFC 6901 spells this the empty string, not {@code "/"}. */
    public static final String ROOT = "";

    private JsonPointer() {
    }

    /** {@code parent} extended by an object member name. */
    public static String child(String parent, String key) {
        return parent + "/" + escape(key);
    }

    /** {@code parent} extended by an array index. */
    public static String index(String parent, int index) {
        return parent + "/" + index;
    }

    /** RFC 6901 §3: {@code ~} becomes {@code ~0} and {@code /} becomes {@code ~1}, in that order. */
    public static String escape(String token) {
        if (token.indexOf('~') < 0 && token.indexOf('/') < 0) {
            return token;
        }
        return token.replace("~", "~0").replace("/", "~1");
    }

    /** The inverse of {@link #escape}. Order matters: {@code ~1} first, then {@code ~0}. */
    public static String unescape(String token) {
        if (token.indexOf('~') < 0) {
            return token;
        }
        return token.replace("~1", "/").replace("~0", "~");
    }

    /**
     * Splits a pointer into its unescaped reference tokens.
     *
     * @throws IllegalArgumentException if the pointer is neither empty nor starts with {@code /}
     */
    public static List<String> split(String pointer) {
        if (pointer.isEmpty()) {
            return List.of();
        }
        if (pointer.charAt(0) != '/') {
            throw new IllegalArgumentException("not a JSON pointer: " + pointer);
        }
        List<String> tokens = new ArrayList<>();
        int from = 1;
        while (true) {
            int slash = pointer.indexOf('/', from);
            if (slash < 0) {
                tokens.add(unescape(pointer.substring(from)));
                return tokens;
            }
            tokens.add(unescape(pointer.substring(from, slash)));
            from = slash + 1;
        }
    }

    /** Resolves {@code pointer} against {@code root}, or returns empty if it addresses nothing. */
    public static Optional<JsonValue> resolve(JsonValue root, String pointer) {
        JsonValue current = root;
        for (String token : split(pointer)) {
            Optional<JsonValue> next = switch (current) {
                case JsonObject o -> o.get(token);
                case JsonArray a -> parseIndex(token).flatMap(a::get);
                default -> Optional.empty();
            };
            if (next.isEmpty()) {
                return Optional.empty();
            }
            current = next.get();
        }
        return Optional.of(current);
    }

    private static Optional<Integer> parseIndex(String token) {
        if (token.isEmpty() || (token.length() > 1 && token.charAt(0) == '0')) {
            return Optional.empty(); // RFC 6901 forbids leading zeroes in array indices
        }
        for (int i = 0; i < token.length(); i++) {
            if (token.charAt(i) < '0' || token.charAt(i) > '9') {
                return Optional.empty();
            }
        }
        try {
            return Optional.of(Integer.parseInt(token));
        } catch (NumberFormatException overflow) {
            return Optional.empty();
        }
    }
}
