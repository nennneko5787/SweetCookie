package net.nennneko5787.sweetcookie.core.format.manifest;

import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.value.PackId;
import net.nennneko5787.sweetcookie.core.format.value.SemanticVersion;

/**
 * One {@code modules[]} entry. SC-100 §4.2.
 *
 * <p>A pack declaring both {@code resources} and {@code data} is <b>one</b> pack providing two
 * halves, not two packs. Modelling it as two would give it two identities in the block ledger and
 * two entries in the add-on screen, and the user would be able to disable half of it.
 *
 * @param type        the recognised type
 * @param declaredType the raw string, kept because {@link ModuleType#UNKNOWN} needs to name itself
 * @param uuid        the module's own identity, distinct from the pack's
 * @param version     the module version
 * @param description free text
 * @param language    {@code script} modules only; empty otherwise
 * @param entry       {@code script} modules only; defaults to {@code scripts/main.js} (SCE-1022)
 */
@SpecImpl("SC-100")
public record PackModule(
        ModuleType type,
        String declaredType,
        PackId uuid,
        SemanticVersion version,
        String description,
        String language,
        String entry) {

    /** What a {@code script} module with no {@code entry} gets. Universal in practice. */
    public static final String DEFAULT_SCRIPT_ENTRY = "scripts/main.js";

    public PackModule {
        description = description == null ? "" : description;
        language = language == null ? "" : language;
        entry = entry == null ? "" : entry;
    }
}
