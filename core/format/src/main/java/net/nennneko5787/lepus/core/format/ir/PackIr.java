package net.nennneko5787.lepus.core.format.ir;

import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.pack.LoadedPack;
import net.nennneko5787.lepus.core.format.value.PackId;

/**
 * One pack's parsed content. SC-110 §8.
 *
 * <p>Holds the {@link LoadedPack} rather than copying its header, modules and texts: SC-100 already
 * owns those, and a second copy is a second place for them to disagree. The same argument as
 * {@code LoadedPack} holding a {@code Manifest}.
 *
 * @param source   the pack this was parsed from, with its VFS and manifest
 * @param behavior the behavior-pack half
 * @param resource the resource-pack half
 */
@SpecImpl("SC-110")
public record PackIr(LoadedPack source, BehaviorIr behavior, ResourceIr resource) {

    public PackId id() {
        return source.id();
    }

    public int loadOrder() {
        return source.loadOrder();
    }
}
