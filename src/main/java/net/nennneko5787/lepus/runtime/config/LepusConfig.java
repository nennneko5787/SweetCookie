package net.nennneko5787.lepus.runtime.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.TreeMap;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.json.CanonicalJson;
import net.nennneko5787.lepus.core.format.json.Json;
import net.nennneko5787.lepus.core.format.json.JsonNumber;
import net.nennneko5787.lepus.core.format.json.JsonObject;
import net.nennneko5787.lepus.core.format.json.JsonValue;
import net.nennneko5787.lepus.core.registry.SlotPool;

/**
 * {@code config/lepus.json}. SC-120 §10, SC-100 §7.
 *
 * <p>Read once at init, before the pool is registered, because {@code blockPool} decides how many
 * blocks exist and the registry freezes immediately afterwards.
 *
 * <p>A missing file is written with the defaults rather than left absent: a config an operator
 * cannot see is one they cannot change, and SC-120 §8.1 asks them to change exactly one line in it
 * when a pool class fills up.
 */
@SpecImpl("SC-120")
public record LepusConfig(SlotPool pool, OptionalInt subpackMemoryTierCeiling) {

    public static final String FILE_NAME = "lepus.json";

    public static final LepusConfig DEFAULT =
            new LepusConfig(SlotPool.DEFAULT, OptionalInt.empty());

    /**
     * Reads the config, writing the defaults when it is absent.
     *
     * <p>A malformed config falls back to the defaults and <b>does not</b> overwrite the file. An
     * operator who broke their JSON wants it back, not replaced.
     */
    public static LepusConfig load(Path configDirectory) {
        Path file = configDirectory.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            try {
                Files.createDirectories(configDirectory);
                Files.writeString(file, DEFAULT.render(), StandardCharsets.UTF_8);
            } catch (IOException unwritable) {
                // Not fatal: the defaults are still what will be used.
            }
            return DEFAULT;
        }
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException malformed) {
            System.out.println("[Lepus] " + FILE_NAME + " could not be read (" + malformed
                    + "); using defaults and leaving the file alone");
            return DEFAULT;
        }
    }

    static LepusConfig parse(String text) {
        JsonObject root = Json.parse(text).asObject()
                .orElseThrow(() -> new IllegalArgumentException("config is not a JSON object"));

        Map<Integer, Integer> capacities = new TreeMap<>();
        root.getObject("blockPool").ifPresent(pool -> pool.members().forEach((sizeClass, count) ->
                capacities.put(Integer.parseInt(sizeClass),
                        count.asNumber().map(JsonNumber::intValue).orElse(0))));

        return new LepusConfig(
                capacities.isEmpty() ? SlotPool.DEFAULT : new SlotPool(capacities),
                root.getNumber("subpackMemoryTierCeiling")
                        .map(n -> OptionalInt.of(n.intValue()))
                        .orElse(OptionalInt.empty()));
    }

    /** The file's contents. Indented, because it is a file a human edits. */
    public String render() {
        Map<String, JsonValue> blockPool = new TreeMap<>(
                java.util.Comparator.comparingInt(Integer::parseInt));
        pool.capacities().forEach((sizeClass, count) ->
                blockPool.put(String.valueOf(sizeClass), JsonNumber.of(count)));

        Map<String, JsonValue> root = new LinkedHashMap<>();
        root.put("blockPool", new JsonObject(blockPool));
        // Null rather than absent: a key an operator can see is one they can change, and
        // SC-100 §7's ceiling is meaningless without knowing it exists.
        root.put("subpackMemoryTierCeiling", subpackMemoryTierCeiling.isPresent()
                ? JsonNumber.of(subpackMemoryTierCeiling.getAsInt())
                : net.nennneko5787.lepus.core.format.json.JsonNull.INSTANCE);
        return CanonicalJson.pretty(new JsonObject(root));
    }
}
