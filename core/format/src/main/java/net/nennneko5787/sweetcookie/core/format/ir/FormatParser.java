package net.nennneko5787.sweetcookie.core.format.ir;

import java.util.Optional;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.json.JsonObject;

/**
 * Parses one kind of file at one {@code format_version}. SC-110 §3.1.
 *
 * <p>Returns empty rather than throwing when the file cannot be made into anything usable. The
 * caller has already reported why through {@link ParseContext}, and constitution rule 1 means the
 * rest of the pack keeps loading.
 */
@FunctionalInterface
@SpecImpl("SC-110")
public interface FormatParser<T> {

    Optional<T> parse(JsonObject root, ParseContext context);
}
