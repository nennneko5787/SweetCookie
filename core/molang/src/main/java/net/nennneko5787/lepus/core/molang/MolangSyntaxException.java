package net.nennneko5787.lepus.core.molang;

import java.io.Serial;

/**
 * A Molang expression that could not be read.
 *
 * <p>Carries a position because the caller turns this into a diagnostic with provenance, and
 * "malformed expression" without a column is something a pack author cannot act on.
 *
 * <p>Throwing does not violate constitution rule 1: this happens at <b>ingest</b>, where there is a
 * file and a JSON pointer to attach it to, and SC-110 §7 requires a failed expression to become
 * {@code MolangExpr.constant(0)} plus a diagnostic. Failing here rather than mid-frame is the whole
 * reason expressions are parsed at load.
 */
public final class MolangSyntaxException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int line;
    private final int column;
    private final String source;

    MolangSyntaxException(String message, String source, int line, int column) {
        super(message + " (line " + line + ", column " + column + ")");
        this.source = source;
        this.line = line;
        this.column = column;
    }

    /** 1-based. */
    public int line() {
        return line;
    }

    /** 1-based, counted in {@code char}s. */
    public int column() {
        return column;
    }

    /** The expression as written, for a diagnostic that quotes it back. */
    public String source() {
        return source;
    }
}
