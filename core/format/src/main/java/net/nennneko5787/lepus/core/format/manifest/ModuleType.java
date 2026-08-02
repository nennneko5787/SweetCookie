package net.nennneko5787.lepus.core.format.manifest;

import java.util.Locale;
import net.nennneko5787.lepus.core.api.SpecImpl;

/** The {@code modules[].type} values SC-100 §4.2 recognises. */
@SpecImpl("SC-100")
public enum ModuleType {

    /** The resource-pack half. */
    RESOURCES,

    /** The behavior-pack half. */
    DATA,

    /** JavaScript. Carries {@code language} and {@code entry}. */
    SCRIPT,

    /**
     * Appears in Microsoft's own examples and nowhere in their field table.
     *
     * <p>Treated as {@link #DATA} with an {@code SCE-1021} note rather than as unknown, because
     * refusing it would break packs Microsoft themselves published as reference material.
     */
    CLIENT_DATA,

    /** A world template. */
    WORLD_TEMPLATE,

    /** Skins. Recognised so it can be reported, then skipped. */
    SKIN_PACK,

    /** Something Mojang added after this was written. Recorded, ignored, reported. */
    UNKNOWN;

    /** Maps a raw {@code type} string, case-insensitively. Never throws. */
    public static ModuleType parse(String raw) {
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "resources" -> RESOURCES;
            case "data" -> DATA;
            case "script" -> SCRIPT;
            case "client_data" -> CLIENT_DATA;
            case "world_template" -> WORLD_TEMPLATE;
            case "skin_pack" -> SKIN_PACK;
            default -> UNKNOWN;
        };
    }

    /** True when this module contributes behavior-pack content. */
    public boolean isBehavior() {
        return this == DATA || this == CLIENT_DATA;
    }

    /** True when this module contributes resource-pack content. */
    public boolean isResource() {
        return this == RESOURCES;
    }
}
