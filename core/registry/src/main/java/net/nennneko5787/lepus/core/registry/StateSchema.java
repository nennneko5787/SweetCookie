package net.nennneko5787.lepus.core.registry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.ir.block.BlockStateIr;
import net.nennneko5787.lepus.core.format.ir.block.BlockStateSchema;
import net.nennneko5787.lepus.core.format.json.CanonicalJson;
import net.nennneko5787.lepus.core.format.json.JsonArray;
import net.nennneko5787.lepus.core.format.json.JsonObject;
import net.nennneko5787.lepus.core.format.json.JsonString;
import net.nennneko5787.lepus.core.format.json.JsonValue;

/**
 * A block's state list as the ledger records it, and the hash that detects drift. SC-120 §6.3, §6.4.
 *
 * <p>This is the copy that lives in the world save, not the one parsed from the pack. Keeping both
 * is the entire point: an add-on author who adds one block state would otherwise silently scramble
 * every placed copy in every world, and the recorded schema is what makes that detectable and
 * recoverable. It has to exist from the first release or it can never be added retroactively.
 *
 * @param states in declaration order; the first is the least significant digit of the index
 */
@SpecImpl("SC-120")
public record StateSchema(List<Entry> states) {

    /**
     * One recorded state.
     *
     * @param name   the state's identifier, as the pack spells it
     * @param kind   {@code bool}, {@code int} or {@code string}
     * @param values permitted values in declaration order
     */
    public record Entry(String name, String kind, List<String> values) {
        public Entry {
            values = List.copyOf(values);
        }
    }

    public static final StateSchema EMPTY = new StateSchema(List.of());

    public StateSchema {
        states = List.copyOf(states);
    }

    /** The recorded form of a parsed schema. */
    public static StateSchema of(BlockStateSchema parsed) {
        List<Entry> entries = new ArrayList<>();
        for (BlockStateIr state : parsed.states()) {
            entries.add(new Entry(
                    state.name().toString(),
                    switch (state.kind()) {
                        case BOOLEAN -> "bool";
                        case INTEGER -> "int";
                        case STRING -> "string";
                    },
                    state.values()));
        }
        return new StateSchema(entries);
    }

    /** The number of distinct indices: the product of every state's value count. */
    public int size() {
        int product = 1;
        for (Entry state : states) {
            product *= Math.max(1, state.values().size());
        }
        return product;
    }

    public Optional<Entry> state(String name) {
        return states.stream().filter(s -> s.name().equals(name)).findFirst();
    }

    /** Decodes an index into one value per state. SC-120 §6.1, least significant first. */
    public Map<String, String> decode(int index) {
        Map<String, String> out = new LinkedHashMap<>();
        int remaining = Math.floorMod(index, Math.max(1, size()));
        for (Entry state : states) {
            int radix = Math.max(1, state.values().size());
            out.put(state.name(), state.values().isEmpty()
                    ? "" : state.values().get(remaining % radix));
            remaining /= radix;
        }
        return out;
    }

    /** Encodes one value per state. A missing or unrecognised value takes the state's default. */
    public int encode(Map<String, String> values) {
        int index = 0;
        int radix = 1;
        for (Entry state : states) {
            String value = values.get(state.name());
            int digit = value == null ? 0 : Math.max(0, state.values().indexOf(value));
            index += digit * radix;
            radix *= Math.max(1, state.values().size());
        }
        return index;
    }

    /**
     * Re-encodes an index from {@code this} schema into {@code updated}. SC-120 §6.4.
     *
     * <p>States are matched <b>by name</b> and values <b>by value</b>, so reordering either is
     * lossless. A state that disappeared is dropped; one that appeared takes its first declared
     * value; a value that disappeared falls back to its state's first.
     *
     * <p>This is the operation that saves a world when a pack changes its state list. Without it,
     * adding one state to one block scrambles every placed copy.
     */
    public int remapTo(StateSchema updated, int index) {
        return updated.encode(decode(index));
    }

    /** SC-000 §6 canonical JSON of the schema, which is what {@link #hash()} digests. */
    public String canonicalJson() {
        List<JsonValue> entries = new ArrayList<>();
        for (Entry state : states) {
            Map<String, JsonValue> node = new LinkedHashMap<>();
            node.put("name", new JsonString(state.name()));
            node.put("type", new JsonString(state.kind()));
            node.put("values", new JsonArray(
                    state.values().stream().map(v -> (JsonValue) new JsonString(v)).toList()));
            entries.add(new JsonObject(node));
        }
        return CanonicalJson.write(new JsonArray(entries));
    }

    /**
     * {@code sha256:…} over {@link #canonicalJson()}.
     *
     * <p>Canonical JSON is what makes this comparable at all: the hash has to be stable against
     * whitespace and key order and unstable against a changed value or a changed order of states,
     * and those are exactly SC-000 §6's rules.
     */
    public String hash() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalJson().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder("sha256:");
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("SHA-256 unavailable", impossible);
        }
    }
}
