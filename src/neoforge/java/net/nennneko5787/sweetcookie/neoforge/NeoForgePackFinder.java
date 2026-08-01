package net.nennneko5787.sweetcookie.neoforge;

import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.PackSelectionConfig;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.nennneko5787.sweetcookie.runtime.resource.AddonResourcePack;

/**
 * Adds the generated add-on pack to the client's resource packs. SC-150 §5.
 *
 * <p>NeoForge has a supported event for exactly this, which is why this side landed first: Fabric
 * has no equivalent and needs a mixin, and that is the remaining half of making a bound block
 * visible.
 *
 * <p>Required and fixed-position: this is not a pack a user chooses, it is where the models for
 * their enabled add-ons come from. Turning it off would make every bound block invisible with no
 * indication why.
 */
@EventBusSubscriber(modid = "sweetcookie")
public final class NeoForgePackFinder {

    private NeoForgePackFinder() {
    }

    // Four-argument Pack.Metadata: NeoForge deprecates it in favour of a five-argument form Fabric
    // does not have. Same reasoning as AddonPackScreen - suppressed at the one call site.
    @SuppressWarnings("deprecation")
    @SubscribeEvent
    public static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        AddonResourcePack pack = new AddonResourcePack();
        event.addRepositorySource(consumer -> consumer.accept(new Pack(
                pack.location(),
                new Pack.ResourcesSupplier() {
                    @Override
                    public net.minecraft.server.packs.PackResources openPrimary(
                            net.minecraft.server.packs.PackLocationInfo location) {
                        return pack;
                    }

                    @Override
                    public net.minecraft.server.packs.PackResources openFull(
                            net.minecraft.server.packs.PackLocationInfo location,
                            Pack.Metadata metadata) {
                        return pack;
                    }
                },
                new Pack.Metadata(
                        pack.location().title(),
                        net.minecraft.server.packs.repository.PackCompatibility.COMPATIBLE,
                        net.minecraft.world.flag.FeatureFlagSet.of(),
                        java.util.List.of()),
                new PackSelectionConfig(true, Pack.Position.TOP, true))));
    }
}
