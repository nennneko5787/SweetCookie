package net.nennneko5787.sweetcookie.core.registry;

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
import java.util.TreeMap;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.json.CanonicalJson;
import net.nennneko5787.sweetcookie.core.format.json.Json;
import net.nennneko5787.sweetcookie.core.format.json.JsonArray;
import net.nennneko5787.sweetcookie.core.format.json.JsonNumber;
import net.nennneko5787.sweetcookie.core.format.json.JsonObject;
import net.nennneko5787.sweetcookie.core.format.json.JsonString;
import net.nennneko5787.sweetcookie.core.format.json.JsonValue;

/**
 * Reads and writes {@code <world>/data/sweetcookie/ledger.json}. SC-120 §6.3.
 *
 * <p><b>Atomically</b>: to a temporary file, then a rename, keeping the previous version as
 * {@code ledger.json.bak}. A half-written ledger is a world whose blocks decode to the wrong
 * content, and a crash during a save is exactly when a ledger gets written.
 *
 * <p>Written as indented canonical JSON. Canonical because {@link StateSchema#hash()} digests the
 * same rules and the two must not disagree; indented because a ledger is a file a human reads when
 * a world goes wrong, and the diff between two revisions is the first thing they will want.
 */
@SpecImpl("SC-120")
public final class LedgerJson {

    public static final String FILE_NAME = "ledger.json";
    public static final String BACKUP_NAME = "ledger.json.bak";

    /** What was read. {@code pool} is what the world requires, not what is configured. */
    public record Contents(SlotPool pool, List<BlockLedger.Binding> bindings) {
        public Contents {
            bindings = List.copyOf(bindings);
        }
    }

    private LedgerJson() {
    }

    /**
     * Reads the ledger from {@code directory}, falling back to the backup.
     *
     * <p>Returns empty when neither exists, which is a new world and not a problem. An unreadable
     * primary with a readable backup is {@code SCE-4014} and the caller reports it — silently using
     * the backup would hide that a save is damaged.
     */
    public static Optional<Contents> read(Path directory) throws IOException {
        Path primary = directory.resolve(FILE_NAME);
        if (Files.isRegularFile(primary)) {
            try {
                return Optional.of(parse(Files.readString(primary, StandardCharsets.UTF_8)));
            } catch (RuntimeException damaged) {
                // Fall through to the backup rather than refusing: a world with a damaged ledger
                // and a good backup is recoverable, and refusing would make it not.
            }
        }
        Path backup = directory.resolve(BACKUP_NAME);
        if (Files.isRegularFile(backup)) {
            return Optional.of(parse(Files.readString(backup, StandardCharsets.UTF_8)));
        }
        return Optional.empty();
    }

    /** Writes atomically, rotating the previous file to {@code .bak}. */
    public static void write(Path directory, BlockLedger ledger) throws IOException {
        Files.createDirectories(directory);
        Path primary = directory.resolve(FILE_NAME);
        Path backup = directory.resolve(BACKUP_NAME);
        Path temporary = directory.resolve(FILE_NAME + ".tmp");

        Files.writeString(temporary, render(ledger), StandardCharsets.UTF_8);
        if (Files.isRegularFile(primary)) {
            Files.move(primary, backup, StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(temporary, primary,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException notAtomic) {
            // Some filesystems refuse an atomic move across their own quirks. A plain move is
            // still better than writing in place, and the backup above still exists.
            Files.move(temporary, primary, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** The file's exact contents, for a test that wants to assert on them without touching disk. */
    public static String render(BlockLedger ledger) {
        Map<String, JsonValue> pool = new TreeMap<>(java.util.Comparator.comparingInt(Integer::parseInt));
        ledger.requiredPool().capacities()
                .forEach((sizeClass, count) -> pool.put(String.valueOf(sizeClass), JsonNumber.of(count)));

        List<JsonValue> entries = new ArrayList<>();
        for (BlockLedger.Binding binding : ledger.bindings()) {
            Map<String, JsonValue> node = new LinkedHashMap<>();
            node.put("logicalId", new JsonString(binding.logicalId()));
            node.put("bedrockId", new JsonString(binding.bedrockId()));
            node.put("kind", new JsonString("block"));
            node.put("slot", new JsonObject(Map.of(
                    "sizeClass", JsonNumber.of(binding.slot().sizeClass()),
                    "index", JsonNumber.of(binding.slot().index()))));
            node.put("stateSchema", schemaJson(binding.schema()));
            node.put("stateSchemaHash", new JsonString(binding.schemaHash()));
            node.put("previousSchemas", new JsonArray(
                    binding.previousSchemas().stream().map(LedgerJson::schemaJson).toList()));
            entries.add(new JsonObject(node));
        }

        Map<String, JsonValue> root = new LinkedHashMap<>();
        root.put("formatVersion", JsonNumber.of(BlockLedger.FORMAT_VERSION));
        root.put("pool", new JsonObject(pool));
        root.put("entries", new JsonArray(entries));
        return CanonicalJson.pretty(new JsonObject(root));
    }

    static Contents parse(String text) {
        JsonObject root = Json.parse(text).asObject()
                .orElseThrow(() -> new IllegalArgumentException("ledger is not a JSON object"));

        int version = root.getNumber("formatVersion").map(JsonNumber::intValue).orElse(-1);
        if (version != BlockLedger.FORMAT_VERSION) {
            // Refused rather than guessed. A ledger written by a newer build may allocate slots in
            // a way this build does not understand, and misreading one hands placed blocks to the
            // wrong content.
            throw new IllegalArgumentException(
                    "ledger formatVersion " + version + "; this build writes "
                            + BlockLedger.FORMAT_VERSION);
        }

        Map<Integer, Integer> pool = new TreeMap<>();
        root.getObject("pool").ifPresent(o -> o.members().forEach((sizeClass, count) ->
                pool.put(Integer.parseInt(sizeClass), count.asNumber()
                        .map(JsonNumber::intValue).orElse(0))));

        List<BlockLedger.Binding> bindings = new ArrayList<>();
        root.getArray("entries").ifPresent(array -> {
            for (JsonValue value : array.values()) {
                value.asObject().ifPresent(entry -> bindings.add(binding(entry)));
            }
        });
        return new Contents(new SlotPool(pool), bindings);
    }

    private static BlockLedger.Binding binding(JsonObject entry) {
        JsonObject slot = entry.getObject("slot")
                .orElseThrow(() -> new IllegalArgumentException("ledger entry has no slot"));
        return new BlockLedger.Binding(
                entry.getString("logicalId").orElseThrow(
                        () -> new IllegalArgumentException("ledger entry has no logicalId")),
                entry.getString("bedrockId").orElse(""),
                new BlockSlot(
                        slot.getNumber("sizeClass").map(JsonNumber::intValue).orElseThrow(
                                () -> new IllegalArgumentException("slot has no sizeClass")),
                        slot.getNumber("index").map(JsonNumber::intValue).orElse(0)),
                schema(entry.get("stateSchema").orElse(JsonArray.EMPTY)),
                entry.getArray("previousSchemas")
                        .map(a -> a.values().stream().map(LedgerJson::schema).toList())
                        .orElse(List.of()));
    }

    private static JsonValue schemaJson(StateSchema schema) {
        List<JsonValue> states = new ArrayList<>();
        for (StateSchema.Entry state : schema.states()) {
            Map<String, JsonValue> node = new LinkedHashMap<>();
            node.put("name", new JsonString(state.name()));
            node.put("type", new JsonString(state.kind()));
            node.put("values", new JsonArray(
                    state.values().stream().map(v -> (JsonValue) new JsonString(v)).toList()));
            states.add(new JsonObject(node));
        }
        return new JsonArray(states);
    }

    private static StateSchema schema(JsonValue value) {
        List<StateSchema.Entry> states = new ArrayList<>();
        value.asArray().ifPresent(array -> {
            for (JsonValue element : array.values()) {
                element.asObject().ifPresent(state -> states.add(new StateSchema.Entry(
                        state.getString("name").orElse(""),
                        state.getString("type").orElse("string"),
                        state.getArray("values")
                                .map(a -> a.values().stream()
                                        .flatMap(v -> v.asString().stream()).toList())
                                .orElse(List.of()))));
            }
        });
        return new StateSchema(states);
    }
}
