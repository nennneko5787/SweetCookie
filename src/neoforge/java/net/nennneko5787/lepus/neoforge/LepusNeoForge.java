package net.nennneko5787.lepus.neoforge;

import net.minecraft.core.registries.Registries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.RegisterEvent;
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

    public LepusNeoForge(ModContainer container, IEventBus modBus) {
        // NOT in the constructor. NeoForge freezes the vanilla registries before it constructs a
        // mod and reopens them for exactly one phase — RegisterEvent — so registering a block here
        // is `IllegalStateException: Registry is already frozen` and the mod never loads at all.
        // Fabric leaves them open through mod initialisation, which is why the shared init() could
        // be called directly there and had been on both loaders.
        //
        // Any one RegisterEvent will do, and that is not luck: NeoForge unfreezes EVERY registry at
        // once (GameData.unfreezeData) and re-freezes them all after the last event, so the block
        // pool, the carrier item and the creative tab can all be registered from one of them. The
        // block registry is named rather than "the first one" because the order is NeoForge's to
        // change and a fixed name cannot drift with it.
        modBus.addListener(RegisterEvent.class, event -> {
            if (event.getRegistryKey().equals(Registries.BLOCK)) {
                Lepus.init();
            }
        });

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
            // The layer that draws a held item's Bedrock attachable ON the player in third person,
            // which is where Bedrock draws an attachable at all. See AttachableLayer. Through a
            // method reference for the same reason as the line above: the class it names must not
            // load on a server.
            modBus.addListener(NeoForgeAttachableLayer::addLayers);
            // And first person, which the layer cannot serve because no player model is drawn there
            // at all. SC-170 §5.
            //
            // The GAME bus, not the mod bus. RenderHandEvent is fired per frame from
            // ItemInHandRenderer, and the mod bus carries only the startup lifecycle — registering
            // it there is accepted at load and then never fires, which is the silent half of this
            // distinction.
            NeoForge.EVENT_BUS.addListener(NeoForgeFirstPersonAttachables::onRenderHand);
        }
    }
}
