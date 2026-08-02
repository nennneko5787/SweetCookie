package net.nennneko5787.lepus.core.format.ir.block;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.json.JsonValue;
import net.nennneko5787.lepus.core.format.value.BedrockId;

/**
 * The resource pack's root {@code blocks.json}: a block's texture and its sound. SC-150 §5.4.
 *
 * <p><b>Not an alternative spelling of {@code material_instances} — it is the only place either is
 * written for blocks in the {@code 1.13}-era format, and that format is everywhere.</b> A block file
 * from that era carries {@code minecraft:destroy_time} and no materials at all; the picture and the
 * sound live in the resource pack, keyed by the block's name. Reading only the modern component
 * leaves every such block with the missing texture and a stone footstep, and nothing in the block's
 * own file explains either.
 *
 * <p>Keys are matched by <b>bare name first, then by full identifier</b>. Vanilla's own file uses
 * bare names and so do the custom packs that copy its shape; a pack that writes
 * {@code namespace:name} is accepted too, because accepting both costs one lookup and refusing one
 * costs the pack its textures.
 *
 * @param texturesByBlock block name to face name to texture key
 * @param soundByBlock    block name to Bedrock sound group, e.g. {@code metal}
 */
@SpecImpl("SC-150#minecraft:material_instances")
public record LegacyBlockIndex(Map<String, Map<String, String>> texturesByBlock,
        Map<String, String> soundByBlock) {

    public static final LegacyBlockIndex EMPTY = new LegacyBlockIndex(Map.of(), Map.of());

    /** Bedrock's own name for "every face this does not name". */
    private static final String ALL = "*";

    public LegacyBlockIndex {
        Map<String, Map<String, String>> copy = new LinkedHashMap<>();
        texturesByBlock.forEach((block, faces) -> copy.put(block, Map.copyOf(faces)));
        texturesByBlock = Map.copyOf(copy);
        soundByBlock = Map.copyOf(soundByBlock);
    }

    /**
     * Reads a parsed {@code blocks.json}.
     *
     * <p>Everything that is not a block entry is skipped rather than fatal: the file's own
     * {@code format_version} sits at the top level beside the blocks, so a reader that assumed every
     * member was a block would trip over the first line of every real file.
     */
    public static LegacyBlockIndex of(JsonValue file) {
        Map<String, Map<String, String>> textures = new LinkedHashMap<>();
        Map<String, String> sounds = new LinkedHashMap<>();
        file.asObject().ifPresent(root -> root.members().forEach((name, entry) -> {
            if (name.startsWith("format_version")) {
                return;
            }
            entry.asObject().ifPresent(object -> {
                Optional.ofNullable(object.members().get("textures")).ifPresent(value -> {
                    Map<String, String> faces = facesOf(value);
                    if (!faces.isEmpty()) {
                        textures.put(name, faces);
                    }
                });
                Optional.ofNullable(object.members().get("sound"))
                        .flatMap(JsonValue::asString)
                        .ifPresent(sound -> sounds.put(name, sound));
            });
        }));
        return new LegacyBlockIndex(textures, sounds);
    }

    /**
     * One block's faces.
     *
     * <p>{@code "textures": "name"} means every face. The object form names {@code up}, {@code down}
     * and {@code side}, which are the same instance names {@code material_instances} uses, so the
     * result drops straight into {@link BlockModels.Materials} with no second vocabulary.
     */
    private static Map<String, String> facesOf(JsonValue textures) {
        Optional<String> single = textures.asString();
        if (single.isPresent()) {
            return Map.of(ALL, single.get());
        }
        Map<String, String> faces = new LinkedHashMap<>();
        textures.asObject().ifPresent(object -> object.members().forEach((face, value) ->
                value.asString().ifPresent(texture -> faces.put(face, texture))));
        // `side` is the three faces neither up nor down. Nothing else in this project knows that
        // word, so it becomes the catch-all here rather than being special-cased downstream.
        if (faces.containsKey("side") && !faces.containsKey(ALL)) {
            faces.put(ALL, faces.get("side"));
        }
        return faces;
    }

    /** The materials for a block, or empty when this file says nothing about it. */
    public Optional<BlockModels.Materials> materialsFor(BedrockId identifier) {
        Map<String, String> faces = lookup(texturesByBlock, identifier);
        return faces == null
                ? Optional.empty()
                : Optional.of(new BlockModels.Materials(faces, true));
    }

    /** The Bedrock sound group a block declares, e.g. {@code metal}. */
    public Optional<String> soundFor(BedrockId identifier) {
        return Optional.ofNullable(lookup(soundByBlock, identifier));
    }

    private static <T> T lookup(Map<String, T> byName, BedrockId identifier) {
        T found = byName.get(identifier.path());
        return found != null ? found : byName.get(identifier.toString());
    }

    public boolean isEmpty() {
        return texturesByBlock.isEmpty() && soundByBlock.isEmpty();
    }
}
