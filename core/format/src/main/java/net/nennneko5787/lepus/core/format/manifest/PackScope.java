package net.nennneko5787.lepus.core.format.manifest;

import java.util.Locale;
import net.nennneko5787.lepus.core.api.SpecImpl;

/** {@code header.pack_scope}. SC-100 §4.2. Defaults to {@link #ANY}. */
@SpecImpl("SC-100")
public enum PackScope {

    /** Applies to one world. */
    WORLD,

    /** Applies globally. */
    GLOBAL,

    /** Either. The default, and by far the most common. */
    ANY;

    /** Never throws: an unrecognised scope is {@link #ANY}, matching the absent case. */
    public static PackScope parse(String raw) {
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "world" -> WORLD;
            case "global" -> GLOBAL;
            default -> ANY;
        };
    }
}
