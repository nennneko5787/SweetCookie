package net.nennneko5787.lepus.core.format.render;

import net.nennneko5787.lepus.core.api.SpecImpl;
import net.nennneko5787.lepus.core.molang.MolangContext;
import net.nennneko5787.lepus.core.molang.MolangMath;
import net.nennneko5787.lepus.core.molang.MolangStrings;

/**
 * What the renderer can tell a pack's Molang about this frame. SC-130 §4, SC-170 §5.
 *
 * <p>Two answers, and they are the two a real attachable asks for:
 * {@code v.main_hand = c.item_slot == 'main_hand';} in {@code pre_animation}, and
 * {@code v.main_hand && c.is_first_person} as the condition that chooses the first-person pose.
 * Without them every such condition reads false and the pack's two poses collapse into one.
 *
 * <p><b>{@code item_slot} is a string, and strings are floats here.</b> Molang has no string type at
 * runtime; the compiler interns a literal to a number and a comparison is numeric (SC-130). So the
 * context answers with the same interned number the literal compiled to, and
 * {@code c.item_slot == 'main_hand'} is true because both sides are the same float.
 *
 * <p>Everything else delegates to a standalone context: variables and temporaries work, and queries
 * about the world still read zero until SC-130 §5 binds them.
 *
 * <p><b>In {@code core} rather than beside the renderer, and that is not tidiness.</b> It holds no
 * Minecraft type and never did. While it sat on the Minecraft side, the offline survey could not
 * build the context a frame is evaluated against, so it could pose a model with ONE animation and
 * never with the set a view actually plays — which is exactly where the composition bug that put a
 * piggybacking character two blocks off the player's shoulder was hiding. A measuring instrument
 * that cannot reach the thing being measured is the reason a bug survives.
 */
@SpecImpl("SC-130")
public final class AttachableContext implements MolangContext {

    private final MolangContext delegate = MolangContext.standalone();
    private final boolean firstPerson;
    private final float slot;
    private float targetXRotation;
    private float targetYRotation;

    private AttachableContext(boolean firstPerson, String slot) {
        this.firstPerson = firstPerson;
        this.slot = MolangStrings.intern(slot);
    }

    /**
     * Where the wearer is looking, as the engine reports it in degrees. SC-180 §4.3.
     *
     * <p><b>This is what aims an arm.</b> The corpus poses a rifle with
     * {@code query.target_x_rotation - 110.0 - this} on the arm bone and the matching
     * {@code query.target_y_rotation} beside it, so a query that answers zero — which is what an
     * unbound one does — leaves the arm at a fixed angle rather than following the wearer's gaze.
     * That reads on screen as "the arm is not doing anything", which is how it was reported.
     *
     * <p><b>Both pass straight through, and one of them briefly did not.</b> A character's head
     * turned the opposite way to the player's, and negating the yaw here fixed the symptom — for the
     * wrong reason. The cause was one level down: SC-180 §3.4.1's angle sense was being applied to
     * all three axes when it belongs to two, so every Y rotation in every model was reversed. That
     * bug crossed a character's legs as well, which is the report this compensation would not have
     * touched. Fixed at the source, both values are just the angles.
     *
     * <p>Returns itself so a call site stays one expression; the object is per frame and per draw,
     * so there is nothing to share and nothing to make immutable for.
     */
    public AttachableContext looking(float pitch, float yaw) {
        this.targetXRotation = pitch;
        this.targetYRotation = yaw;
        return this;
    }

    /**
     * A hand a player sees down their own arms.
     *
     * <p><b>Which hand still has to be told.</b> This said {@code main_hand} unconditionally while
     * first person was drawn by a special model renderer, which is handed a stack and never learns
     * where it came from. The seam is a hand hook now and the hand arrives with it, so a pack's
     * {@code v.main_hand = c.item_slot == 'main_hand'} can be false — as it is for the off hand,
     * whose whole point is a different pose.
     */
    public static AttachableContext firstPerson(boolean mainHand) {
        return new AttachableContext(true, mainHand ? "main_hand" : "off_hand");
    }

    /** The player as everyone else sees them. */
    public static AttachableContext thirdPerson(boolean mainHand) {
        return new AttachableContext(false, mainHand ? "main_hand" : "off_hand");
    }

    /**
     * A worn piece: an attachable in an armour slot rather than a hand.
     *
     * <p><b>The slot names are asserted.</b> Bedrock's {@code minecraft:wearable} spells them
     * {@code slot.armor.head}; what {@code c.item_slot} reports to an attachable is documented
     * nowhere this project can check, and the short form is what the community's packs compare
     * against. A pack testing for the long form simply finds its condition false and keeps its
     * default pose, which is the safe way to be wrong.
     *
     * @param slot {@code head}, {@code chest}, {@code legs} or {@code feet}
     */
    public static AttachableContext worn(String slot) {
        return new AttachableContext(false, slot);
    }

    @Override
    public boolean isDefined(Scope scope, String name) {
        return switch (scope) {
            case CONTEXT -> known(name);
            case QUERY -> queried(name) || delegate.isDefined(scope, name);
            default -> delegate.isDefined(scope, name);
        };
    }

    @Override
    public float read(Scope scope, String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (scope == Scope.QUERY) {
            return switch (lower) {
                // The angles that aim a limb. Bedrock states these as the rotation needed to face
                // the target; for something attached to a player, that is where the player is
                // looking, which is the head's own pitch and yaw.
                case "target_x_rotation" -> targetXRotation;
                case "target_y_rotation" -> targetYRotation;
                default -> delegate.read(scope, name);
            };
        }
        if (scope != Scope.CONTEXT) {
            return delegate.read(scope, name);
        }
        return switch (lower) {
            case "is_first_person" -> firstPerson ? 1f : 0f;
            // ANSWERED, because vanilla's own held attachable branches on it. SC-180 §4.4.
            //
            // This was refused for a while, on the grounds that Mojang's list of 315 queries does
            // not contain it. The shield's animation controller reads
            // `c.item_slot == 'main_hand'` in a condition, so the list is simply incomplete —
            // an absence there is not evidence. What keeps the corpus's own first-person animation
            // from playing is the indirection through `pre_animation`, not this query.
            case "item_slot" -> slot;
            // A context value this build does not answer reads zero, as an unbound query does.
            default -> 0f;
        };
    }

    @Override
    public void write(Scope scope, String name, float value) {
        delegate.write(scope, name, value);
    }

    @Override
    public float call(Scope scope, String name, float[] arguments) {
        return scope == Scope.CONTEXT ? read(scope, name) : delegate.call(scope, name, arguments);
    }

    @Override
    public MolangMath math() {
        return delegate.math();
    }

    private static boolean known(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.equals("is_first_person") || lower.equals("item_slot");
    }

    private static boolean queried(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.equals("target_x_rotation") || lower.equals("target_y_rotation");
    }
}
