package net.nennneko5787.sweetcookie.core.format.json;

import java.io.Serial;

/**
 * A JSON document that could not be read at all.
 *
 * <p>Throwing does not violate constitution rule 1. The rule is that bad add-on input must not crash
 * the game, and it is satisfied one level up: {@link Json#tryParse} catches this, turns it into a
 * diagnostic with provenance, and the file is skipped while the rest of the pack loads (SC-000 §10).
 * A checked-style exception here keeps the reader's internals simple and keeps the recovery decision
 * where it belongs, which is with the caller who knows what the file was for.
 *
 * <p>{@link #kind()} exists so the caller reports the right {@code SCE-} code without matching on
 * message text.
 */
public final class JsonParseException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** What went wrong, mapped to a diagnostic code by {@link Json}. */
    public enum Kind {
        /** Syntax the reader cannot make sense of at all. */
        MALFORMED,
        /** The same key twice in one object. SC-000 §6.6 makes this an error, not a last-wins. */
        DUPLICATE_KEY,
        /** Nesting beyond {@link JsonLimits#maxDepth()} — an untrusted-input guard, SC-260. */
        TOO_DEEP,
    }

    private final Kind kind;
    private final int line;
    private final int column;

    JsonParseException(Kind kind, String message, int line, int column) {
        super(message + " (line " + line + ", column " + column + ")");
        this.kind = kind;
        this.line = line;
        this.column = column;
    }

    public Kind kind() {
        return kind;
    }

    /** 1-based. */
    public int line() {
        return line;
    }

    /** 1-based, counted in {@code char}s. */
    public int column() {
        return column;
    }
}
