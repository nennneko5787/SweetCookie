package net.nennneko5787.lepus.core.registry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.json.CanonicalJson;
import net.nennneko5787.lepus.core.format.json.Json;
import net.nennneko5787.lepus.core.format.json.JsonArray;
import net.nennneko5787.lepus.core.format.json.JsonNumber;
import net.nennneko5787.lepus.core.format.json.JsonObject;
import net.nennneko5787.lepus.core.format.json.JsonString;
import net.nennneko5787.lepus.core.format.json.JsonValue;
import net.nennneko5787.lepus.core.format.value.PackId;
import net.nennneko5787.lepus.core.format.value.SemanticVersion;

/**
 * Reads and writes {@code <world>/data/lepus/active.json}. SC-120 §8.
 *
 * <p>The same atomic discipline as the ledger, and for a weaker but still real reason: a truncated
 * activation file does not corrupt a world, but it does silently turn a pack off, which a user
 * experiences as their content vanishing.
 *
 * <p><b>Unlike the ledger, a damaged activation file is recoverable by hand</b> — it is a short list
 * of identities a user could retype. So it falls back to the backup and then to "nothing enabled"
 * rather than refusing, and the file is left in place either way.
 */
@SpecImpl("SC-120")
public final class ActiveJson {

    public static final String FILE_NAME = "active.json";
    public static final String BACKUP_NAME = "active.json.bak";

    /** Bumped only when the on-disk shape changes. */
    public static final int FORMAT_VERSION = 1;

    private ActiveJson() {
    }

    /** Reads, falling back to the backup. Empty means "this world has never activated anything". */
    public static Optional<ActivePacks> read(Path directory) throws IOException {
        Path primary = directory.resolve(FILE_NAME);
        if (Files.isRegularFile(primary)) {
            try {
                return Optional.of(parse(Files.readString(primary, StandardCharsets.UTF_8)));
            } catch (RuntimeException damaged) {
                // Fall through to the backup.
            }
        }
        Path backup = directory.resolve(BACKUP_NAME);
        if (Files.isRegularFile(backup)) {
            try {
                return Optional.of(parse(Files.readString(backup, StandardCharsets.UTF_8)));
            } catch (RuntimeException alsoDamaged) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /** Writes atomically, rotating the previous file to {@code .bak}. */
    public static void write(Path directory, ActivePacks active) throws IOException {
        Files.createDirectories(directory);
        Path primary = directory.resolve(FILE_NAME);
        Path backup = directory.resolve(BACKUP_NAME);
        Path temporary = directory.resolve(FILE_NAME + ".tmp");

        Files.writeString(temporary, render(active), StandardCharsets.UTF_8);
        if (Files.isRegularFile(primary)) {
            Files.move(primary, backup, StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(temporary, primary,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException notAtomic) {
            Files.move(temporary, primary, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** The file's exact contents. */
    public static String render(ActivePacks active) {
        List<JsonValue> entries = new ArrayList<>();
        for (ActivePacks.Entry entry : active.entries()) {
            Map<String, JsonValue> node = new LinkedHashMap<>();
            node.put("packUuid", new JsonString(entry.pack().toString()));
            node.put("version", new JsonString(entry.version().toString()));
            entries.add(new JsonObject(node));
        }
        Map<String, JsonValue> root = new LinkedHashMap<>();
        root.put("formatVersion", JsonNumber.of(FORMAT_VERSION));
        // The order in this array IS the precedence order (SC-100 §5), so the comment belongs in
        // the file: a user editing it by hand has no other way to learn which end wins.
        root.put("_comment", new JsonString(
                "packs in precedence order, lowest first; the last entry overrides the rest"));
        root.put("packs", new JsonArray(entries));
        return CanonicalJson.pretty(new JsonObject(root));
    }

    static ActivePacks parse(String text) {
        JsonObject root = Json.parse(text).asObject()
                .orElseThrow(() -> new IllegalArgumentException("active.json is not a JSON object"));

        int version = root.getNumber("formatVersion").map(JsonNumber::intValue).orElse(-1);
        if (version != FORMAT_VERSION) {
            throw new IllegalArgumentException(
                    "active.json formatVersion " + version + "; this build writes " + FORMAT_VERSION);
        }

        List<ActivePacks.Entry> entries = new ArrayList<>();
        root.getArray("packs").ifPresent(array -> {
            for (JsonValue value : array.values()) {
                value.asObject().ifPresent(entry -> entry.getString("packUuid")
                        .flatMap(PackId::parse)
                        .ifPresent(pack -> entries.add(new ActivePacks.Entry(
                                pack,
                                entry.getString("version")
                                        .flatMap(SemanticVersion::tryParse)
                                        .orElse(SemanticVersion.ZERO)))));
            }
        });
        return new ActivePacks(entries);
    }
}
