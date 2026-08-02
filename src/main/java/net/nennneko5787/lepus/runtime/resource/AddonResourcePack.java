package net.nennneko5787.lepus.runtime.resource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.world.flag.FeatureFlagSet;
import net.nennneko5787.lepus.Lepus;
import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * The blockstates and models bound add-ons need, served from memory. SC-150 §5, SC-180.
 *
 * <p>The architecture calls for textures, models, lang and sounds to be synthesised as a resource
 * pack rather than handled by a mechanism of our own, and this is the first of those. Everything
 * vanilla does with a real pack — chunk meshing, the atlas, {@code /reload}, interaction with other
 * packs — then applies without being reimplemented.
 *
 * <p><b>Why not files in the jar.</b> The jar already ships one blockstate per pool slot, pointing
 * at {@code block/air}, and that is a build-time answer to a runtime question: which Bedrock block a
 * slot holds is decided per world, and a user who raises {@code lepus.blockPool} gets more
 * slots than the jar has files for. A pack built from the live bindings has neither problem.
 *
 * <p>Version-free. {@code PackResources}, {@code IoSupplier}, {@code PackType} and
 * {@code PackLocationInfo} are identical on both supported versions — the same check that let the
 * selection screen be one class.
 */
@SpecImpl({"SC-150", "SC-180"})
public final class AddonResourcePack implements PackResources {

    /** The pack's own id. Not a Bedrock name: this is one pack, whatever add-ons are enabled. */
    public static final String ID = "lepus:addons";

    private static volatile Map<String, byte[]> contents = Map.of();

    private final PackLocationInfo location;

    public AddonResourcePack() {
        this.location = new PackLocationInfo(
                ID,
                Component.literal("Lepus add-ons"),
                PackSource.BUILT_IN,
                Optional.empty());
    }

    /**
     * Replaces everything this pack serves.
     *
     * <p>A whole-snapshot swap for the same reason {@code BoundBlocks} is one: the bindings change
     * together, and a pack half-updated would draw one block with another's model. Volatile because
     * binding happens on the server thread and resource loading does not.
     */
    public static boolean replace(Map<String, byte[]> byPath) {
        Map<String, byte[]> previous = contents;
        contents = Map.copyOf(byPath);
        return !same(previous, contents);
    }

    /**
     * Whether two snapshots serve the same bytes.
     *
     * <p>The answer decides whether the client is asked to reload its resources, and a reload is a
     * visible pause — so "changed" has to mean changed, not "was rebuilt". Binding runs on every
     * world load and every activation change, and most of those produce the identical 2,012
     * blockstate files.
     *
     * <p>{@code Map.equals} would not do: the values are {@code byte[]}, whose equality is identity,
     * so every rebuild would compare unequal and every world load would reload the client.
     */
    private static boolean same(Map<String, byte[]> a, Map<String, byte[]> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (Map.Entry<String, byte[]> entry : a.entrySet()) {
            if (!java.util.Arrays.equals(entry.getValue(), b.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    /** UTF-8, for the callers whose files are JSON they just generated. */
    public static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /** How many files the pack currently serves. Zero is the honest state before any world loads. */
    public static int size() {
        return contents.size();
    }

    /**
     * The repository entry both loaders add.
     *
     * <p>One builder for the two of them, because the only thing that differs is <b>where</b> it is
     * added — NeoForge has an event, Fabric needs a mixin (SC-180 §8.1) — and the pack itself must
     * not diverge along with the hook.
     *
     * <p>Required and fixed-position: this is not a pack a user chooses. It is where the models for
     * their enabled add-ons come from, and switching it off would make every bound block invisible
     * with no indication why.
     */
    @SuppressWarnings("deprecation") // Pack.Metadata: NeoForge deprecates the form Fabric has.
    public static Pack asPack() {
        AddonResourcePack pack = new AddonResourcePack();
        return new Pack(
                pack.location(),
                new Pack.ResourcesSupplier() {
                    @Override
                    public PackResources openPrimary(PackLocationInfo location) {
                        return pack;
                    }

                    @Override
                    public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
                        return pack;
                    }
                },
                new Pack.Metadata(pack.location().title(), PackCompatibility.COMPATIBLE,
                        FeatureFlagSet.of(), List.of()),
                new PackSelectionConfig(true, Pack.Position.TOP, true));
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
        // No pack.png and no pack.mcmeta: this pack is added by a finder rather than found on disk,
        // so nothing ever reads either.
        return null;
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, Identifier id) {
        if (type != PackType.CLIENT_RESOURCES || !Lepus.MOD_ID.equals(id.getNamespace())) {
            return null;
        }
        byte[] bytes = contents.get(id.getPath());
        return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
    }

    @Override
    public void listResources(PackType type, String namespace, String prefix,
            ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES || !Lepus.MOD_ID.equals(namespace)) {
            return;
        }
        contents.forEach((path, bytes) -> {
            if (path.startsWith(prefix)) {
                output.accept(Identifier.fromNamespaceAndPath(Lepus.MOD_ID, path),
                        () -> new ByteArrayInputStream(bytes));
            }
        });
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? Set.of(Lepus.MOD_ID) : Set.of();
    }

    @Override
    public <T> T getMetadataSection(MetadataSectionType<T> type) {
        // No sections, including pack format. A finder-supplied pack is never version-checked, and
        // claiming a format we do not track would be a claim that goes stale on its own.
        return null;
    }

    @Override
    public PackLocationInfo location() {
        return location;
    }

    @Override
    public void close() {
        // Nothing to release: every byte is already in memory and outlives any one open.
    }
}
