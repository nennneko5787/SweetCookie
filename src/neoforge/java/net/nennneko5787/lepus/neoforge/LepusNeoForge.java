package net.nennneko5787.lepus.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.nennneko5787.lepus.Lepus;
import net.nennneko5787.lepus.client.ui.AddonPackScreen;

/**
 * NeoForge entry point.
 *
 * <p>Lives in {@code src/neoforge/java}, which only the NeoForge nodes compile. See
 * {@code LepusFabric} for why loader code is separated by directory rather than by
 * {@code //?} comment.
 */
@Mod(Lepus.MOD_ID)
public final class LepusNeoForge {

    public LepusNeoForge(ModContainer container) {
        Lepus.init();

        // The mod-list "Config" button. Registered only on a client: IConfigScreenFactory returns a
        // Screen, and touching that class on a dedicated server would pull client rendering into a
        // process that has none — SC-230 §3's client-only rule, and SCE-6003's territory.
        //
        // No platform service for this. SC-280 §3 sketched a ConfigScreenProvider, but both loaders
        // PULL — ModMenu calls our entry point, NeoForge reads this extension point — so nothing
        // ever needs to ask for a screen through an interface. And ViewScreen has one name across
        // both version directories, so shared code that wanted one could just construct it.
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            container.registerExtensionPoint(IConfigScreenFactory.class,
                    (modContainer, parent) -> AddonPackScreen.open(parent));
        }
    }
}
