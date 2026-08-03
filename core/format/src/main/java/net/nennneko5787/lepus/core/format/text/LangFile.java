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
 *   <li>a value ends at a <b>tab followed by {@code #}</b>, which is the trailing-comment form;
 *   <li>keys and values keep their internal whitespace; only a trailing {@code \r} is removed.
 * </ul>
 *
 * <p><b>That third rule was the other way round, and a Bedrock client refuted it.</b> This file used
 * to keep the trailing comment, on the stated grounds that Bedrock does not strip it. A pack in the
 * corpus writes {@code item.totem.name=<name>\t#} and the Bedrock client shows the name with no tab
 * and no hash, so Bedrock strips it and the note was wrong. Mojang's own {@code en_US.lang} could
 * not settle it either way: at the pinned snapshot it contains no tab comment at all.
 *
 * <p><b>Only that form, and the old note's reasoning is why.</b> Treating any {@code #} as an
 * end-of-line comment is what a properties parser does, and it silently truncates a translation
 * containing a hash — "Item #3" loses three characters and nobody sees a diagnostic. A tab before
 * the hash is the form the observation covers and the form real packs write; a hash after a space,
 * or in the middle of a sentence, stays in the value where it belongs.
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
            out.put(line.substring(0, equals), stripComment(line.substring(equals + 1)));
        }
        return out;
    }

    /**
     * A value with its trailing comment removed.
     *
     * <p>The comment begins at a <b>tab</b> immediately followed by {@code #}. Anything looser eats
     * a hash a translator meant to keep; anything stricter leaves a tab and a hash in an item's
     * displayed name, which is what sent this rule to a Bedrock client to be settled.
     *
     * <p><b>The cut is all of it.</b> Trimming what is left over was tried and broke the rule above
     * it: a value keeps its own surrounding whitespace, and a test says so. The tab is already on
     * the comment's side of the cut, so there is nothing left to tidy that belongs to anybody but
     * the author.
     */
    private static String stripComment(String value) {
        int comment = value.indexOf("\t#");
        return comment < 0 ? value : value.substring(0, comment);
    }
}
