package net.nennneko5787.sweetcookie.fabric.mixin;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import net.nennneko5787.sweetcookie.runtime.resource.AddonResourcePack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the generated add-on pack to the client's resource packs, on Fabric. SC-180 §8.1.
 *
 * <p><b>The first mixin in this project</b>, and it exists because Fabric has no API for this.
 * NeoForge has {@code AddPackFindersEvent}; Fabric API's {@code registerBuiltinResourcePack} serves
 * a pack out of the mod jar and cannot serve a generated one, {@code registerReloadListener} is for
 * listeners, and Fabric Loader's own {@code ModResourcePack} is likewise jar-built. §8.1 records
 * that search so it is not repeated.
 *
 * <p><b>The client's repository, not every repository.</b> {@code PackRepository} carries no
 * {@code PackType}, so a mixin on the class alone would add the pack to the server's data-pack
 * repository too — nearly harmless, since this pack answers with no namespaces for
 * {@code SERVER_DATA}, but it would list an empty data pack. {@link ClientPackSource} appears only
 * in the client's repository and is therefore the discriminator: a marker Minecraft already
 * maintains, rather than one we would have to keep true ourselves.
 *
 * <p>The field is replaced rather than mutated. Vanilla builds it with {@code Set.of}, which is
 * immutable, so adding to it in place throws — and would throw on every launch, which is at least
 * the loud kind of wrong.
 */
@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {

    @Mutable
    @Shadow
    @Final
    private Set<RepositorySource> sources;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void sweetcookie$addAddonPack(RepositorySource[] given, CallbackInfo callback) {
        if (Arrays.stream(given).noneMatch(source -> source instanceof ClientPackSource)) {
            return;
        }
        Set<RepositorySource> updated = new LinkedHashSet<>(this.sources);
        updated.add(consumer -> consumer.accept(AddonResourcePack.asPack()));
        this.sources = Set.copyOf(updated);
    }
}
