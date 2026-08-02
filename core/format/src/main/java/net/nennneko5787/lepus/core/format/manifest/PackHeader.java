package net.nennneko5787.lepus.core.format.manifest;

import java.util.List;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.value.BedrockVersion;
import net.nennneko5787.lepus.core.format.value.PackId;
import net.nennneko5787.lepus.core.format.value.SemanticVersion;

/**
 * {@code manifest.json}'s {@code header}, normalised across format versions 1, 2 and 3. SC-100 §4.
 *
 * <p>{@code name} and {@code description} are kept <b>raw</b>. They are frequently {@code .lang}
 * keys such as {@code pack.name}, and resolving one at parse time would need a locale that this
 * layer has no business knowing — a dedicated server and each of its clients want different ones.
 * Resolution happens against {@code Localisation} at display time.
 *
 * @param id               {@code header.uuid}, possibly derived from a malformed one (SCE-1025)
 * @param name             raw; may be a {@code .lang} key
 * @param description      raw; may be a {@code .lang} key
 * @param version          {@code header.version}
 * @param minEngineVersion {@code header.min_engine_version}, defaulted to 1.16.0 when absent
 * @param baseGameVersion  world templates only; {@link BedrockVersion#ZERO} when absent
 * @param scope            {@code header.pack_scope}
 * @param subpacks         {@code header.subpacks}, in declared order
 */
@SpecImpl("SC-100")
public record PackHeader(
        PackId id,
        String name,
        String description,
        SemanticVersion version,
        BedrockVersion minEngineVersion,
        BedrockVersion baseGameVersion,
        PackScope scope,
        List<SubpackDecl> subpacks) {

    /** What an absent {@code min_engine_version} on an RP or BP is treated as. SC-100 §6. */
    public static final BedrockVersion ASSUMED_MIN_ENGINE_VERSION = BedrockVersion.of(1, 16, 0);

    public PackHeader {
        subpacks = List.copyOf(subpacks);
        name = name == null ? "" : name;
        description = description == null ? "" : description;
    }
}
