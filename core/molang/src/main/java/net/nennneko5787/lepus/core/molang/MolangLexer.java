package net.nennneko5787.lepus.core.molang;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * Molang's tokens. SC-130 §2.1.
 *
 * <p>Identifiers are folded to lower case here, because Molang is case-insensitive everywhere except
 * inside string literals — {@code Math.Floor} and {@code math.floor} are one name. Folding at the
 * lexer means nothing downstream has to remember, and it uses {@code Locale.ROOT} so a Turkish
 * locale cannot turn {@code query.Is_Baby} into a different identifier (SC-000 §9).
 */
@SpecImpl("SC-130")
final class MolangLexer {

    enum Kind {
        NUMBER, STRING, IDENTIFIER,
        PLUS, MINUS, STAR, SLASH,
        LT, LTE, GT, GTE, EQ, NEQ,
        AND, OR, NOT,
        QUESTION, COLON, COALESCE, ARROW,
        ASSIGN, SEMICOLON, COMMA, DOT,
        LPAREN, RPAREN, LBRACE, RBRACE, LBRACKET, RBRACKET,
        RETURN, BREAK, CONTINUE,
        END
    }

    record Token(Kind kind, String text, float number, int line, int column) {
    }

    private final String src;
    private int pos;
    private int line = 1;
    private int lineStart;

    MolangLexer(String source) {
        this.src = source;
    }

    List<Token> tokenise() {
        List<Token> out = new ArrayList<>();
        while (true) {
            skipSpaceAndComments();
            if (pos >= src.length()) {
                out.add(new Token(Kind.END, "", 0f, line, column()));
                return out;
            }
            out.add(next());
        }
    }

    private Token next() {
        int startLine = line;
        int startColumn = column();
        char c = src.charAt(pos);

        if (c >= '0' && c <= '9' || c == '.' && pos + 1 < src.length() && isDigit(src.charAt(pos + 1))) {
            return number(startLine, startColumn);
        }
        if (isIdentifierStart(c)) {
            return identifier(startLine, startColumn);
        }
        if (c == '\'') {
            return string(startLine, startColumn);
        }

        pos++;
        return switch (c) {
            case '+' -> token(Kind.PLUS, "+", startLine, startColumn);
            // `->` before `-`: otherwise a dereference lexes as a subtraction of a comparison and
            // evaluates to a plausible number instead of failing.
            case '-' -> token(take('>') ? Kind.ARROW : Kind.MINUS, "-", startLine, startColumn);
            case '*' -> token(Kind.STAR, "*", startLine, startColumn);
            case '/' -> token(Kind.SLASH, "/", startLine, startColumn);
            case ';' -> token(Kind.SEMICOLON, ";", startLine, startColumn);
            case ',' -> token(Kind.COMMA, ",", startLine, startColumn);
            case '.' -> token(Kind.DOT, ".", startLine, startColumn);
            case '(' -> token(Kind.LPAREN, "(", startLine, startColumn);
            case ')' -> token(Kind.RPAREN, ")", startLine, startColumn);
            case '{' -> token(Kind.LBRACE, "{", startLine, startColumn);
            case '}' -> token(Kind.RBRACE, "}", startLine, startColumn);
            case '[' -> token(Kind.LBRACKET, "[", startLine, startColumn);
            case ']' -> token(Kind.RBRACKET, "]", startLine, startColumn);
            case ':' -> token(Kind.COLON, ":", startLine, startColumn);
            case '<' -> token(take('=') ? Kind.LTE : Kind.LT, "<", startLine, startColumn);
            case '>' -> token(take('=') ? Kind.GTE : Kind.GT, ">", startLine, startColumn);
            case '!' -> token(take('=') ? Kind.NEQ : Kind.NOT, "!", startLine, startColumn);
            case '?' -> token(take('?') ? Kind.COALESCE : Kind.QUESTION, "?", startLine, startColumn);
            case '=' -> {
                if (take('=')) {
                    yield token(Kind.EQ, "==", startLine, startColumn);
                }
                yield token(Kind.ASSIGN, "=", startLine, startColumn);
            }
            case '&' -> {
                require('&', startLine, startColumn);
                yield token(Kind.AND, "&&", startLine, startColumn);
            }
            case '|' -> {
                require('|', startLine, startColumn);
                yield token(Kind.OR, "||", startLine, startColumn);
            }
            default -> throw fail("unexpected character '" + c + "'", startLine, startColumn);
        };
    }

    private Token number(int startLine, int startColumn) {
        int from = pos;
        while (pos < src.length() && isDigit(src.charAt(pos))) {
            pos++;
        }
        if (pos < src.length() && src.charAt(pos) == '.'
                && pos + 1 < src.length() && isDigit(src.charAt(pos + 1))) {
            pos++;
            while (pos < src.length() && isDigit(src.charAt(pos))) {
                pos++;
            }
        }
        String text = src.substring(from, pos);
        // Parsed straight to float. Going through double and narrowing would round twice, and
        // 0.1f is not (float) 0.1d for every literal a pack can write.
        return new Token(Kind.NUMBER, text, Float.parseFloat(text), startLine, startColumn);
    }

    private Token identifier(int startLine, int startColumn) {
        int from = pos;
        while (pos < src.length() && isIdentifierPart(src.charAt(pos))) {
            pos++;
        }
        String text = src.substring(from, pos).toLowerCase(Locale.ROOT);
        Kind kind = switch (text) {
            case "return" -> Kind.RETURN;
            case "break" -> Kind.BREAK;
            case "continue" -> Kind.CONTINUE;
            default -> Kind.IDENTIFIER;
        };
        return new Token(kind, text, 0f, startLine, startColumn);
    }

    private Token string(int startLine, int startColumn) {
        pos++; // the opening quote
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= src.length()) {
                throw fail("unterminated string", startLine, startColumn);
            }
            char c = src.charAt(pos++);
            if (c == '\'') {
                // Case is preserved: SC-130 §2.1 makes only identifiers case-insensitive, and
                // string literals are compared against pack-authored names.
                return new Token(Kind.STRING, sb.toString(), 0f, startLine, startColumn);
            }
            if (c == '\n') {
                line++;
                lineStart = pos;
            }
            sb.append(c);
        }
    }

    /**
     * Whitespace and comments.
     *
     * <p>Molang has no comment syntax of its own, but expressions arrive embedded in JSON that has
     * already been through a lenient reader, and {@code //} appears in authored expressions often
     * enough to be worth accepting rather than failing on.
     */
    private void skipSpaceAndComments() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '\n') {
                line++;
                pos++;
                lineStart = pos;
            } else if (c == ' ' || c == '\t' || c == '\r' || c == '\f') {
                pos++;
            } else if (c == '/' && pos + 1 < src.length() && src.charAt(pos + 1) == '/') {
                while (pos < src.length() && src.charAt(pos) != '\n') {
                    pos++;
                }
            } else {
                return;
            }
        }
    }

    private boolean take(char expected) {
        if (pos < src.length() && src.charAt(pos) == expected) {
            pos++;
            return true;
        }
        return false;
    }

    private void require(char expected, int atLine, int atColumn) {
        if (!take(expected)) {
            throw fail("expected '" + expected + "'", atLine, atColumn);
        }
    }

    private Token token(Kind kind, String text, int atLine, int atColumn) {
        return new Token(kind, text, 0f, atLine, atColumn);
    }

    private MolangSyntaxException fail(String message, int atLine, int atColumn) {
        return new MolangSyntaxException(message, src, atLine, atColumn);
    }

    private int column() {
        return pos - lineStart + 1;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isIdentifierStart(char c) {
        return c == '_' || Character.isLetter(c);
    }

    private static boolean isIdentifierPart(char c) {
        return c == '_' || Character.isLetterOrDigit(c);
    }
}
