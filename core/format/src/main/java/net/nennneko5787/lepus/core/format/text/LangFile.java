package net.nennneko5787.lepus.core.format.text;

import java.util.LinkedHashMap;
import java.util.Map;
import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * Bedrock's {@code .lang} format. SC-100 §8.
 *
 * <p>Superficially a properties file and different in every way that matters:
 *
 * <ul>
 *   <li>the <b>first</b> {@code =} splits; a value may contain more;
 *   <li>a line whose first non-space characters are {@code ##} is a comment;
 *   <li>a trailing {@code #comment} on a value line is <b>not</b> stripped, because Bedrock does not
 *       strip it and real packs contain {@code #} in values;
 *   <li>keys and values keep their internal whitespace; only a trailing {@code \r} is removed.
 * </ul>
 *
 * <p>That third rule is the one worth stating twice. Treating {@code #} as an end-of-line comment is
 * what a properties parser does, and doing it here silently truncates any translation containing a
 * hash — which is most translations of anything involving a number.
 */
@SpecImpl("SC-100")
public final class LangFile {

    private LangFile() {
    }

    /**
     * Parses a {@code .lang} file. Never fails: a line that is not {@code key=value} is skipped.
     *
     * @return the entries, in file order, with later duplicates overwriting earlier ones
     */
    public static Map<String, String> parse(String content) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String rawLine : content.split("\n", -1)) {
            String line = rawLine.endsWith("\r")
                    ? rawLine.substring(0, rawLine.length() - 1)
                    : rawLine;
            if (line.isBlank() || line.stripLeading().startsWith("##")) {
                continue;
            }
            int equals = line.indexOf('=');
            if (equals < 0) {
                continue;
            }
            out.put(line.substring(0, equals), line.substring(equals + 1));
        }
        return out;
    }
}
