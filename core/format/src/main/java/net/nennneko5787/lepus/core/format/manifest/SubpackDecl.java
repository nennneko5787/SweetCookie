package net.nennneko5787.lepus.core.format.manifest;

import net.nennneko5787.lepus.core.api.SpecImpl;

/**
 * One {@code header.subpacks[]} entry. SC-100 §7.
 *
 * <pre>{@code
 * { "folder_name": "hd", "name": "HD Textures", "memory_tier": 4 }
 * }</pre>
 *
 * <p>One memory tier is 0.25 GiB on Bedrock. Lepus does <b>not</b> infer a tier from the
 * host's memory: a Java client and a dedicated server have unrelated memory characteristics, and a
 * server choosing a texture resolution on a client's behalf is simply wrong in multiplayer. The tier
 * is configuration (SC-100 §7).
 *
 * @param folderName the directory under {@code subpacks/}
 * @param name       the display name, which may be a {@code .lang} key
 * @param memoryTier the tier this variant is intended for
 */
@SpecImpl("SC-100")
public record SubpackDecl(String folderName, String name, int memoryTier) {

    /** Where this subpack's files live, relative to the pack root. */
    public String path() {
        return "subpacks/" + folderName;
    }
}
