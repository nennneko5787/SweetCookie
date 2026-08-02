package net.nennneko5787.lepus.core.testkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.json.JsonArray;
import net.nennneko5787.lepus.core.format.json.JsonObject;
import net.nennneko5787.lepus.core.format.json.JsonValue;

/**
 * The path syntax a conformance case's {@code assert.that} uses:
 * {@code ir.packs[0].texts.entries['en_US']['pack.name']}.
 *
 * <p>Not RFC 6901, on purpose. {@code JsonPointer} would spell that
 * {@code /ir/packs/0/texts/entries/en_US/pack.name}, which is shorter to implement and considerably
 * worse to read in a file whose whole job is to state a claim a human will audit. The cost is this
 * class, which is small.
 *
 * <p>Bracket keys may be quoted with {@code '} or {@code "}, and must be when the key contains a dot
 * — which Bedrock keys constantly do.
 */
@SpecImpl("SC-100")
public final class JsonPath {

    private JsonPath() {
    }

    /** Resolves {@code expression} against {@code root}, or returns empty if it addresses nothing. */
    public static Optional<JsonValue> resolve(JsonValue root, String expression) {
        JsonValue current = root;
        for (String token : split(expression)) {
            Optional<JsonValue> next = step(current, token);
            if (next.isEmpty()) {
                return Optional.empty();
            }
            current = next.get();
        }
        return Optional.of(current);
    }

    private static Optional<JsonValue> step(JsonValue current, String token) {
        if (current instanceof JsonArray array) {
            try {
                return array.get(Integer.parseInt(token));
            } catch (NumberFormatException notAnIndex) {
                return Optional.empty();
            }
        }
        if (current instanceof JsonObject object) {
            return object.get(token);
        }
        return Optional.empty();
    }

    /**
     * Splits into resolution tokens.
     *
     * @throws IllegalArgumentException if the expression is malformed — a typo in a case file is a
     *     broken test, not a failing one, and the two deserve different messages
     */
    public static List<String> split(String expression) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int i = 0;
        while (i < expression.length()) {
            char c = expression.charAt(i);
            if (c == '.') {
                flush(tokens, current, expression);
                i++;
            } else if (c == '[') {
                flush(tokens, current, expression);
                int close = findClosingBracket(expression, i);
                String inner = expression.substring(i + 1, close).trim();
                tokens.add(unquote(inner, expression));
                i = close + 1;
            } else if (c == ']') {
                throw new IllegalArgumentException("unbalanced ']' in path: " + expression);
            } else {
                current.append(c);
                i++;
            }
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("empty path expression");
        }
        return tokens;
    }

    private static void flush(List<String> tokens, StringBuilder current, String expression) {
        if (current.isEmpty()) {
            return;
        }
        tokens.add(current.toString());
        current.setLength(0);
    }

    private static int findClosingBracket(String expression, int open) {
        char quote = 0;
        for (int i = open + 1; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == ']') {
                return i;
            }
        }
        throw new IllegalArgumentException("unbalanced '[' in path: " + expression);
    }

    private static String unquote(String token, String expression) {
        if (token.length() >= 2
                && (token.charAt(0) == '\'' || token.charAt(0) == '"')
                && token.charAt(token.length() - 1) == token.charAt(0)) {
            return token.substring(1, token.length() - 1);
        }
        if (token.isEmpty()) {
            throw new IllegalArgumentException("empty bracket in path: " + expression);
        }
        return token;
    }
}
