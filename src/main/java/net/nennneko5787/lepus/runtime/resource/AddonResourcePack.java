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

    /**
     * What this pack serves in the <b>{@code minecraft}</b> namespace, replacing vanilla's own file.
     * SC-170 §5.2.
     *
     * <p>Separate from {@link #contents} rather than one map keyed by identifier, because the two are
     * different kinds of thing and the difference is worth being unable to lose: everything above is
     * a file only this mod names, and everything here <b>overwrites something vanilla shipped</b>.
     * The pack sits at {@link Pack.Position#TOP}, so what is written here wins.
     *
     * <p>Empty in every world that has no add-on dressing a vanilla item, which is nearly all of
     * them — an empty map means vanilla is untouched, and that is the state to prefer.
     */
    private static volatile Map<String, byte[]> overrides = Map.of();

    /** The namespace {@link #overrides} lands in. Vanilla's own, which is the point of it. */
    private static final String VANILLA = "minecraft";

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
        return replace(byPath, Map.of());
    }

    /**
     * As above, with the vanilla-namespace replacements this pack also serves. SC-170 §5.2.
     *
     * <p>Both halves swap together for the reason the first one swaps at all: an item whose
     * third-person hands were blanked and whose attachable had not yet bound would draw nothing in
     * either place, for as long as the two snapshots disagreed.
     */
    public static boolean replace(Map<String, byte[]> byPath, Map<String, byte[]> vanillaByPath) {
        Map<String, byte[]> previousContents = contents;
        Map<String, byte[]> previousOverrides = overrides;
        contents = Map.copyOf(byPath);
        overrides = Map.copyOf(vanillaByPath);
        return !same(previousContents, contents) || !same(previousOverrides, overrides);
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
        return contents.size() + overrides.size();
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
        if (type != PackType.CLIENT_RESOURCES) {
            return null;
        }
        byte[] bytes = served(id.getNamespace()).get(id.getPath());
        return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
    }

    @Override
    public void listResources(PackType type, String namespace, String prefix,
            ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES) {
            return;
        }
        served(namespace).forEach((path, bytes) -> {
            if (path.startsWith(prefix)) {
                output.accept(Identifier.fromNamespaceAndPath(namespace, path),
                        () -> new ByteArrayInputStream(bytes));
            }
        });
    }

    /** The half of this pack a namespace is served from; empty for anything else. */
    private static Map<String, byte[]> served(String namespace) {
        if (Lepus.MOD_ID.equals(namespace)) {
            return contents;
        }
        return VANILLA.equals(namespace) ? overrides : Map.of();
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        if (type != PackType.CLIENT_RESOURCES) {
            return Set.of();
        }
        // Vanilla's namespace is claimed only while something is actually replacing a file in it.
        // Claiming it unconditionally would make this pack a participant in every vanilla asset
        // lookup in the game for the sake of serving nothing.
        return overrides.isEmpty() ? Set.of(Lepus.MOD_ID) : Set.of(Lepus.MOD_ID, VANILLA);
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
