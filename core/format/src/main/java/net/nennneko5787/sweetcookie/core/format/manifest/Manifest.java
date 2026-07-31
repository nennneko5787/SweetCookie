package net.nennneko5787.sweetcookie.core.format.manifest;

import java.util.List;
import java.util.Set;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.value.SemanticVersion;

/**
 * A parsed {@code manifest.json}. SC-100 §4.
 *
 * @param formatVersion       as declared; 1, 2, 3, or whatever unrecognised value was written
 * @param header              {@code header}
 * @param modules             {@code modules}, in declared order, at least one
 * @param dependencies        {@code dependencies}, unusable entries already dropped
 * @param capabilities        recognised {@code capabilities}
 * @param unknownCapabilities capabilities Mojang added after this was written
 * @param metadata            {@code metadata}
 */
@SpecImpl("SC-100")
public record Manifest(
        int formatVersion,
        PackHeader header,
        List<PackModule> modules,
        List<PackDependency> dependencies,
        Set<Capability> capabilities,
        Set<String> unknownCapabilities,
        PackMetadata metadata) {

    public Manifest {
        modules = List.copyOf(modules);
        dependencies = List.copyOf(dependencies);
        capabilities = Set.copyOf(capabilities);
        unknownCapabilities = Set.copyOf(unknownCapabilities);
    }

    /** True when any module contributes behavior-pack content. */
    public boolean hasBehavior() {
        return modules.stream().anyMatch(m -> m.type().isBehavior());
    }

    /** True when any module contributes resource-pack content. */
    public boolean hasResources() {
        return modules.stream().anyMatch(m -> m.type().isResource());
    }

    public boolean hasScripts() {
        return modules.stream().anyMatch(m -> m.type() == ModuleType.SCRIPT);
    }

    public boolean isWorldTemplate() {
        return modules.stream().anyMatch(m -> m.type() == ModuleType.WORLD_TEMPLATE);
    }

    /** Script-module dependencies only — the {@code @minecraft/*} APIs a pack asks for. */
    public List<PackDependency.OnModule> moduleDependencies() {
        return dependencies.stream()
                .filter(PackDependency.OnModule.class::isInstance)
                .map(PackDependency.OnModule.class::cast)
                .toList();
    }

    /** Pack dependencies only. */
    public List<PackDependency.OnPack> packDependencies() {
        return dependencies.stream()
                .filter(PackDependency.OnPack.class::isInstance)
                .map(PackDependency.OnPack.class::cast)
                .toList();
    }

    public SemanticVersion version() {
        return header.version();
    }
}
