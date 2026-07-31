package net.nennneko5787.sweetcookie.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;

/**
 * Resolves a platform service to its single provider. SC-230 §1.
 *
 * <p>A hand-rolled {@link ServiceLoader}, and no Architectury. Architectury's runtime would make
 * users install a second mod and would couple this project's release cadence to a third party's —
 * blocked whenever Architectury lags a Minecraft drop. The cost of doing it by hand is one
 * interface, two implementations and one {@code META-INF/services} file per hook, which is boring
 * and never blocks a release.
 *
 * <p><b>Zero providers and two providers are both fatal, at init.</b> A platform hook discovered
 * missing at world load is a far worse failure than one that refuses to start, and a service with
 * two providers silently picks one — which is the kind of bug that only shows on the loader nobody
 * tested.
 */
@SpecImpl("SC-230")
public final class Services {

    private Services() {
    }

    /**
     * The provider for {@code type}.
     *
     * <p>Resolve eagerly, once, into a {@code static final} field (SC-230 §2 rule 3). This method
     * walks the classpath; calling it on a hot path or during rendering is a defect.
     *
     * @throws IllegalStateException if there is not exactly one provider
     */
    public static <T> T load(Class<T> type) {
        List<T> providers = new ArrayList<>();
        for (T provider : ServiceLoader.load(type, Services.class.getClassLoader())) {
            providers.add(provider);
        }
        if (providers.isEmpty()) {
            // SCE-6001. Named rather than generic: "no provider" without the interface name sends a
            // contributor looking through every services file in the jar.
            throw new IllegalStateException(
                    "SCE-6001 no provider for platform service " + type.getName()
                            + ". Every service needs an implementation on BOTH loaders and a"
                            + " META-INF/services entry in that loader's resource directory.");
        }
        if (providers.size() > 1) {
            throw new IllegalStateException(
                    "SCE-6002 " + providers.size() + " providers for platform service "
                            + type.getName() + ": "
                            + providers.stream().map(p -> p.getClass().getName()).toList());
        }
        return providers.get(0);
    }
}
