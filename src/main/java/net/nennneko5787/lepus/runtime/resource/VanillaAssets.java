package net.nennneko5787.lepus.runtime.resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * Reads a file vanilla ships, so that replacing it can start from what it says. SC-170 §5.2.
 *
 * <p><b>Why this exists at all.</b> An attachable dressing a vanilla item has to stop that item
 * drawing its own sprite in the third-person hand, and the way to say so is its item definition —
 * which is also where a bow keeps its pull, a potion its tint and a compass its needle. Writing a
 * fresh definition from the item's name reproduces the plain case and silently destroys every one of
 * those. Reading the real file and wrapping it destroys nothing.
 *
 * <p><b>Not through the resource manager</b>, and that is the point of the class. The generated pack
 * sits above vanilla's, so asking the manager for {@code minecraft:items/bow.json} while building
 * that pack would either return our own replacement or the previous bind's. This asks the jar.
 *
 * <p>The lookup goes through a <b>Minecraft</b> class rather than one of ours so that the module or
 * class loader holding those assets is the one asked — Fabric and NeoForge place the game
 * differently. A resource path whose segments are not legal package names is readable across a
 * module boundary without anything being opened, which {@code assets/minecraft/...} is.
 *
 * <p>Empty on anything unexpected. A file this build cannot read is one it must not replace: the
 * caller then leaves vanilla's own in place, and the cost is a flat sprite inside a character rather
 * than an item that no longer works.
 */
@SpecImpl("SC-170#attachable/item")
public final class VanillaAssets {

    private VanillaAssets() {
    }

    /**
     * One of vanilla's client assets as text.
     *
     * @param path the path inside the jar, e.g. {@code assets/minecraft/items/bow.json}
     */
    public static Optional<String> read(String path) {
        try (InputStream in = Item.class.getResourceAsStream("/" + path)) {
            return in == null
                    ? Optional.empty()
                    : Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }
}
