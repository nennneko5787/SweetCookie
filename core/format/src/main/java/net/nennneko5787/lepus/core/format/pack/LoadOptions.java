package net.nennneko5787.lepus.core.format.pack;

import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.value.BedrockVersion;
import net.nennneko5787.lepus.core.format.value.PackId;

/**
 * Everything {@link AddonLoader} needs from outside {@code core/}.
 *
 * @param limits                 SC-100 §3
 * @param activationOrder        the world's explicit pack order, highest precedence last (SC-100 §5)
 * @param memoryTierCeiling      SC-100 §7; empty means "the highest tier the pack offers"
 * @param targetEngine           the Bedrock engine Lepus targets, for SC-100 §6 gating
 * @param supportedScriptModules the {@code @minecraft/*} APIs SC-200 implements
 */
@SpecImpl("SC-100")
public record LoadOptions(
        ExtractionLimits limits,
        List<PackId> activationOrder,
        OptionalInt memoryTierCeiling,
        BedrockVersion targetEngine,
        Set<String> supportedScriptModules) {

    /**
     * The Bedrock engine this build targets.
     *
     * <p>Must stay in step with {@code spec/upstream/bedrock-samples.lock.json}: it is the version
     * the coverage ledger was checked against, so claiming a different one here would make
     * {@code SCE-2002} fire on packs the ledger says are fully understood.
     */
    public static final BedrockVersion TARGET_ENGINE = BedrockVersion.of(1, 26, 30);

    /** SC-200's supported set. Everything else disables that script module only (SCE-2005). */
    public static final Set<String> SUPPORTED_SCRIPT_MODULES =
            Set.of("@minecraft/server", "@minecraft/server-ui");

    public static final LoadOptions DEFAULT = new LoadOptions(
            ExtractionLimits.DEFAULT,
            List.of(),
            OptionalInt.empty(),
            TARGET_ENGINE,
            SUPPORTED_SCRIPT_MODULES);

    public LoadOptions {
        activationOrder = List.copyOf(activationOrder);
        supportedScriptModules = Set.copyOf(supportedScriptModules);
    }

    public LoadOptions withActivationOrder(List<PackId> order) {
        return new LoadOptions(
                limits, order, memoryTierCeiling, targetEngine, supportedScriptModules);
    }

    public LoadOptions withMemoryTierCeiling(int ceiling) {
        return new LoadOptions(limits, activationOrder, OptionalInt.of(ceiling), targetEngine,
                supportedScriptModules);
    }

    public LoadOptions withLimits(ExtractionLimits newLimits) {
        return new LoadOptions(newLimits, activationOrder, memoryTierCeiling, targetEngine,
                supportedScriptModules);
    }
}
