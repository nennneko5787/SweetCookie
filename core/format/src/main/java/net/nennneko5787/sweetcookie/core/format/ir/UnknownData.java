package net.nennneko5787.sweetcookie.core.format.ir;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.json.JsonObject;
import net.nennneko5787.sweetcookie.core.format.json.JsonValue;

/**
 * Keys a parser did not recognise, kept verbatim. SC-110 §5.
 *
 * <p>Load-bearing for three separate reasons, and it is worth listing them because "keep the leftover
 * JSON" reads like hoarding:
 *
 * <ol>
 *   <li>a later version can implement a field without re-parsing anything;
 *   <li>a diagnostic can list precisely what was ignored, rather than saying a file "partly loaded";
 *   <li>a round-trip test can prove the parser is not silently dropping data — which is the failure
 *       mode a parser has no other way to detect about itself.
 * </ol>
 *
 * <p>Unknown data stops at the translation boundary. It is never carried into runtime objects.
 *
 * @param keys the unrecognised members, in the order the file wrote them
 */
@SpecImpl("SC-110")
public record UnknownData(Map<String, JsonValue> keys) {

    public static final UnknownData EMPTY = new UnknownData(Map.of());

    public UnknownData {
        keys = Collections.unmodifiableMap(new LinkedHashMap<>(keys));
    }

    public boolean isEmpty() {
        return keys.isEmpty();
    }

    public Set<String> names() {
        return keys.keySet();
    }

    /**
     * Everything in {@code object} except {@code recognised}.
     *
     * <p>Members whose names begin {@code _} are dropped rather than recorded: {@code _comment} is
     * the community's universal convention for a note to a human, it appears in a large fraction of
     * published files, and reporting it as unrecognised data would bury the real findings.
     */
    public static UnknownData of(JsonObject object, Set<String> recognised) {
        Map<String, JsonValue> leftovers = new LinkedHashMap<>();
        object.members().forEach((name, value) -> {
            if (!recognised.contains(name) && !name.startsWith("_")) {
                leftovers.put(name, value);
            }
        });
        return leftovers.isEmpty() ? EMPTY : new UnknownData(leftovers);
    }
}
