package net.nennneko5787.lepus.core.format.ir.geometry;

import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.format.value.Vec3f;

/**
 * A named attachment point on a bone. SC-180 §3.
 *
 * <p>Bedrock writes these two ways: {@code "name": [x, y, z]} and
 * {@code "name": {"offset": [...], "rotation": [...]}}. Both normalise here, so nothing downstream
 * has to know which a pack used.
 *
 * @param name                 the locator's name, as the pack spells it
 * @param offset               position relative to the owning bone
 * @param rotation             degrees; zero for the short form, which cannot express one
 * @param ignoreInheritedScale whether the attached thing ignores the bone's scale
 */
@SpecImpl("SC-180#geometry/locators")
public record LocatorIr(String name, Vec3f offset, Vec3f rotation, boolean ignoreInheritedScale) {

    public static LocatorIr of(String name, Vec3f offset) {
        return new LocatorIr(name, offset, Vec3f.ZERO, false);
    }
}
