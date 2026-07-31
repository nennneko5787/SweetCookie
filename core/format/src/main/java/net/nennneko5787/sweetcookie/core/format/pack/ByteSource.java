package net.nennneko5787.sweetcookie.core.format.pack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Bytes that have not been read yet.
 *
 * <p>The whole point of the virtual file system is that a 300 MB add-on is not loaded into memory to
 * find out what is in it (SC-100 §9). This is the handle that makes that possible: the index knows
 * every path and size after one pass over the archive directory, and nothing is decompressed until
 * somebody asks.
 */
@FunctionalInterface
public interface ByteSource {

    /**
     * Reads the whole entry.
     *
     * @throws IOException if the backing archive or file cannot be read, or the entry exceeds the
     *     size limit it was indexed under — a zip's declared sizes are attacker-controlled, so the
     *     limit is enforced again here against the bytes that actually arrive
     */
    byte[] read() throws IOException;

    /** Reads and decodes as UTF-8, stripping a byte-order mark. Every Bedrock text file is UTF-8. */
    default String readUtf8() throws IOException {
        byte[] bytes = read();
        int from = bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF ? 3 : 0;
        return new String(bytes, from, bytes.length - from, StandardCharsets.UTF_8);
    }

    static ByteSource of(byte[] bytes) {
        byte[] copy = bytes.clone();
        return copy::clone;
    }
}
