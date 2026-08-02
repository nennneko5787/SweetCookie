package net.nennneko5787.lepus.core.format.ir.attachable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.ir.UnknownData;
import net.nennneko5787.lepus.core.format.value.BedrockId;
import net.nennneko5787.lepus.core.format.value.Provenance;

/**
 * One {@code minecraft:attachable}: the 3D model an item wears or is held as. SC-170 §5.
 *
 * <p>Java has no counterpart at all. An item there is a flat sprite or a block model; a Bedrock
 * attachable is a <b>skeletal model with its own animations, its own state machine and its own
 * per-frame Molang</b>, drawn attached to whoever holds it. That is why this record is a set of
 * <em>named references</em> rather than resolved things: the pack names a geometry, some textures,
 * some animations and some controllers, and each of those lives in its own file that may not have
 * been read yet — or at all.
 *
 * <p><b>Everything is a map of short name to identifier</b>, mirroring the file. The short names are
 * what {@code scripts} and the render controllers refer to, so flattening them to "the geometry"
 * would throw away the only thing that makes the references resolvable.
 *
 * @param identifier   {@code description.identifier}, which is the ITEM this attaches to
 * @param geometries   short name → {@code geometry.*} identifier
 * @param textures     short name → texture path, relative to the pack
 * @param materials    short name → Bedrock material name, which is not a Java render type
 * @param animations   short name → animation or animation-controller identifier
 * @param animate      {@code scripts.animate}: what to play, in order. See {@link Play}
 * @param preAnimation {@code scripts.pre_animation}: Molang run before each frame's animations
 * @param renderControllers the controllers that choose geometry, texture and material per frame
 */
@SpecImpl({"SC-170#attachable/item", "SC-170#attachable/geometry", "SC-170#attachable/textures",
        "SC-170#attachable/materials", "SC-170#attachable/animations",
        "SC-170#attachable/scripts", "SC-170#attachable/render_controllers"})
public record AttachableIr(
        BedrockId identifier,
        Map<String, String> geometries,
        Map<String, String> textures,
        Map<String, String> materials,
        Map<String, String> animations,
        List<Play> animate,
        List<String> preAnimation,
        List<String> renderControllers,
        Provenance provenance,
        UnknownData unknown) {

    public AttachableIr {
        geometries = ordered(geometries);
        textures = ordered(textures);
        materials = ordered(materials);
        animations = ordered(animations);
        animate = List.copyOf(animate);
        preAnimation = List.copyOf(preAnimation);
        renderControllers = List.copyOf(renderControllers);
    }

    /**
     * One entry of {@code scripts.animate}, which has two shapes in the same array.
     *
     * <p>A bare string plays unconditionally; an object plays its one entry <b>while a Molang
     * expression is true</b>. Both appear in the same list in real packs — the corpus this was
     * written against has {@code [{"main_hand": "v.main_hand && c.is_first_person"}, "hoshino",
     * "default_controller"]} — so the list is of this rather than of strings.
     *
     * <p>The condition is kept as <b>source text</b>. SC-110 §7 forbids storing Molang as something
     * evaluable in the IR; it becomes a compiled expression at the point something is ready to run
     * it, and until then a raw string that looks evaluable is how a parse error surfaces mid-frame
     * with no provenance.
     *
     * @param name      the short name in {@code animations}
     * @param condition the Molang source, or empty when the entry was a bare string
     */
    public record Play(String name, Optional<String> condition) {
    }

    /** The geometry this draws with when nothing has chosen otherwise. */
    public Optional<String> defaultGeometry() {
        return Optional.ofNullable(geometries.get("default"))
                .or(() -> geometries.values().stream().findFirst());
    }

    /** The texture this draws with when nothing has chosen otherwise. */
    public Optional<String> defaultTexture() {
        return Optional.ofNullable(textures.get("default"))
                .or(() -> textures.values().stream().findFirst());
    }

    private static Map<String, String> ordered(Map<String, String> source) {
        // NOT Map.copyOf: its iteration order is unspecified and randomised per JVM run, which puts
        // a golden's arrays in a different order every time they are generated.
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
