package net.nennneko5787.lepus.core.format.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * A recursive-descent reader for the dialect of JSON that Bedrock accepts. SC-110 §2.1.
 *
 * <p>Bedrock's parser is more permissive than RFC 8259 and real packs rely on every bit of it, so
 * this reader accepts:
 *
 * <ul>
 *   <li>a UTF-8 byte-order mark;
 *   <li>{@code //} line comments and {@code /* *}{@code /} block comments, anywhere whitespace is
 *       allowed — authoring tools write them and Blockbench emits them;
 *   <li>trailing commas in objects and arrays;
 *   <li>unescaped control characters inside strings, including raw newlines;
 *   <li>leading zeroes and a leading {@code +} on numbers.
 * </ul>
 *
 * <p>It does <em>not</em> accept duplicate keys. SC-000 §6.6 makes them an error rather than a
 * last-wins, because a pack with two {@code minecraft:collision_box} members in one component list
 * has a bug the author needs told about, and silently picking one hides it forever.
 *
 * <p>An unrecognised escape sequence is kept <em>verbatim</em>, backslash included, rather than
 * being dropped or unescaped to the bare character. Both alternatives lose bytes; this one cannot,
 * and a Windows path that slipped into a description field survives unchanged.
 *
 * <p>Written by hand rather than configured out of an off-the-shelf parser because the leniency
 * above is not a configuration any of them offers in full, and because the facade
 * ({@link JsonValue}) is the part that must stay stable — the reader behind it is replaceable.
 */
@SpecImpl("SC-110")
final class JsonReader {

    /** U+FEFF, written numerically so that this source file cannot itself acquire one. */
    private static final char BOM = (char) 0xFEFF;

    private final String src;
    private final JsonLimits limits;
    private int pos;
    private int line = 1;
    private int lineStart;
    private int depth;

    JsonReader(String source, JsonLimits limits) {
        // A byte-order mark is not whitespace and every strict parser rejects it. Packs authored on
        // Windows contain them constantly.
        this.src = !source.isEmpty() && source.charAt(0) == BOM ? source.substring(1) : source;
        this.limits = limits;
    }

    JsonValue readDocument() {
        skipInsignificant();
        if (atEnd()) {
            throw fail(JsonParseException.Kind.MALFORMED, "the document is empty");
        }
        JsonValue value = readValue();
        skipInsignificant();
        if (!atEnd()) {
            throw fail(JsonParseException.Kind.MALFORMED,
                    "trailing content after the top-level value: '" + peek() + "'");
        }
        return value;
    }

    private JsonValue readValue() {
        char c = peek();
        return switch (c) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> new JsonString(readString());
            case 't' -> readKeyword("true", JsonBool.TRUE);
            case 'f' -> readKeyword("false", JsonBool.FALSE);
            case 'n' -> readKeyword("null", JsonNull.INSTANCE);
            default -> readNumber();
        };
    }

    private JsonValue readObject() {
        enter();
        expect('{');
        Map<String, JsonValue> members = new LinkedHashMap<>();
        skipInsignificant();
        if (!atEnd() && peek() == '}') {
            pos++;
            leave();
            return new JsonObject(members);
        }
        while (true) {
            skipInsignificant();
            if (atEnd()) {
                throw fail(JsonParseException.Kind.MALFORMED, "unterminated object");
            }
            // A trailing comma leaves us looking at the closing brace.
            if (peek() == '}') {
                pos++;
                break;
            }
            if (peek() != '"') {
                throw fail(JsonParseException.Kind.MALFORMED,
                        "expected a quoted member name, found '" + peek() + "'");
            }
            int keyLine = line;
            int keyColumn = column();
            String key = readString();
            if (members.containsKey(key)) {
                throw new JsonParseException(JsonParseException.Kind.DUPLICATE_KEY,
                        "duplicate member name \"" + key + "\"", keyLine, keyColumn);
            }
            skipInsignificant();
            expect(':');
            skipInsignificant();
            if (atEnd()) {
                throw fail(JsonParseException.Kind.MALFORMED,
                        "member \"" + key + "\" has no value");
            }
            members.put(key, readValue());
            skipInsignificant();
            if (atEnd()) {
                throw fail(JsonParseException.Kind.MALFORMED, "unterminated object");
            }
            char c = peek();
            if (c == ',') {
                pos++;
            } else if (c == '}') {
                pos++;
                break;
            } else {
                throw fail(JsonParseException.Kind.MALFORMED,
                        "expected ',' or '}' after a member, found '" + c + "'");
            }
        }
        leave();
        return new JsonObject(members);
    }

    private JsonValue readArray() {
        enter();
        expect('[');
        List<JsonValue> values = new ArrayList<>();
        skipInsignificant();
        if (!atEnd() && peek() == ']') {
            pos++;
            leave();
            return new JsonArray(values);
        }
        while (true) {
            skipInsignificant();
            if (atEnd()) {
                throw fail(JsonParseException.Kind.MALFORMED, "unterminated array");
            }
            if (peek() == ']') { // trailing comma
                pos++;
                break;
            }
            values.add(readValue());
            skipInsignificant();
            if (atEnd()) {
                throw fail(JsonParseException.Kind.MALFORMED, "unterminated array");
            }
            char c = peek();
            if (c == ',') {
                pos++;
            } else if (c == ']') {
                pos++;
                break;
            } else {
                throw fail(JsonParseException.Kind.MALFORMED,
                        "expected ',' or ']' after an element, found '" + c + "'");
            }
        }
        leave();
        return new JsonArray(values);
    }

    private String readString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (atEnd()) {
                throw fail(JsonParseException.Kind.MALFORMED, "unterminated string");
            }
            char c = src.charAt(pos);
            if (c == '"') {
                pos++;
                return sb.toString();
            }
            if (c == '\\') {
                readEscape(sb);
                continue;
            }
            // Raw control characters are accepted verbatim, including newlines, which strict JSON
            // forbids. Line accounting still has to advance or every later diagnostic in the file
            // points at the wrong line.
            if (c == '\n') {
                line++;
                lineStart = pos + 1;
            }
            sb.append(c);
            pos++;
        }
    }

    private void readEscape(StringBuilder sb) {
        int backslash = pos;
        pos++; // the backslash
        if (atEnd()) {
            throw fail(JsonParseException.Kind.MALFORMED, "the string ends in a backslash");
        }
        char e = src.charAt(pos++);
        switch (e) {
            case '"' -> sb.append('"');
            case '\\' -> sb.append('\\');
            case '/' -> sb.append('/');
            case 'b' -> sb.append('\b');
            case 'f' -> sb.append('\f');
            case 'n' -> sb.append('\n');
            case 'r' -> sb.append('\r');
            case 't' -> sb.append('\t');
            case 'u' -> {
                if (pos + 4 > src.length() || !isHex4(pos)) {
                    // Keep it verbatim rather than guessing. See the class comment.
                    sb.append(src, backslash, pos);
                    return;
                }
                sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                pos += 4;
            }
            default -> sb.append(src, backslash, pos);
        }
    }

    private boolean isHex4(int from) {
        for (int i = from; i < from + 4; i++) {
            char c = src.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    private JsonValue readKeyword(String word, JsonValue value) {
        if (!src.startsWith(word, pos)) {
            throw fail(JsonParseException.Kind.MALFORMED, "expected '" + word + "'");
        }
        pos += word.length();
        return value;
    }

    private JsonValue readNumber() {
        int start = pos;
        if (!atEnd() && (peek() == '-' || peek() == '+')) {
            pos++; // a leading '+' is not JSON; Bedrock content contains it
        }
        int digitsBefore = skipDigits();
        int digitsAfter = 0;
        if (!atEnd() && peek() == '.') {
            pos++;
            digitsAfter = skipDigits();
        }
        if (digitsBefore == 0 && digitsAfter == 0) {
            pos = start;
            throw fail(JsonParseException.Kind.MALFORMED,
                    "expected a value, found '" + (atEnd() ? "<end of file>" : peek()) + "'");
        }
        if (!atEnd() && (peek() == 'e' || peek() == 'E')) {
            int mark = pos;
            pos++;
            if (!atEnd() && (peek() == '+' || peek() == '-')) {
                pos++;
            }
            if (skipDigits() == 0) {
                pos = mark; // "1e" — the 'e' is trailing garbage, not part of the number
            }
        }
        String literal = src.substring(start, pos);
        try {
            return JsonNumber.ofLiteral(literal);
        } catch (IllegalArgumentException e) {
            pos = start;
            throw fail(JsonParseException.Kind.MALFORMED, "not a number: '" + literal + "'");
        }
    }

    private int skipDigits() {
        int from = pos;
        while (!atEnd() && peek() >= '0' && peek() <= '9') {
            pos++;
        }
        return pos - from;
    }

    /** Whitespace and comments, which Bedrock treats identically. */
    private void skipInsignificant() {
        while (!atEnd()) {
            char c = src.charAt(pos);
            if (c == '\n') {
                line++;
                pos++;
                lineStart = pos;
            } else if (c == ' ' || c == '\t' || c == '\r' || c == '\f') {
                pos++;
            } else if (c == '/' && pos + 1 < src.length() && src.charAt(pos + 1) == '/') {
                while (!atEnd() && src.charAt(pos) != '\n') {
                    pos++;
                }
            } else if (c == '/' && pos + 1 < src.length() && src.charAt(pos + 1) == '*') {
                int commentStart = pos;
                pos += 2;
                while (true) {
                    if (pos + 1 >= src.length()) {
                        pos = commentStart;
                        throw fail(JsonParseException.Kind.MALFORMED, "unterminated block comment");
                    }
                    if (src.charAt(pos) == '*' && src.charAt(pos + 1) == '/') {
                        pos += 2;
                        break;
                    }
                    if (src.charAt(pos) == '\n') {
                        line++;
                        lineStart = pos + 1;
                    }
                    pos++;
                }
            } else {
                return;
            }
        }
    }

    private void enter() {
        if (++depth > limits.maxDepth()) {
            throw fail(JsonParseException.Kind.TOO_DEEP,
                    "nested deeper than " + limits.maxDepth() + " levels");
        }
    }

    private void leave() {
        depth--;
    }

    private void expect(char c) {
        if (atEnd() || src.charAt(pos) != c) {
            throw fail(JsonParseException.Kind.MALFORMED,
                    "expected '" + c + "', found '" + (atEnd() ? "<end of file>" : peek()) + "'");
        }
        pos++;
    }

    private char peek() {
        return src.charAt(pos);
    }

    private boolean atEnd() {
        return pos >= src.length();
    }

    private int column() {
        return pos - lineStart + 1;
    }

    private JsonParseException fail(JsonParseException.Kind kind, String message) {
        return new JsonParseException(kind, message, line, column());
    }
}
