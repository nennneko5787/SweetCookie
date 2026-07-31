package net.nennneko5787.sweetcookie.core.registry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.value.BedrockId;

/**
 * Derives a logical identifier from a Bedrock one. SC-120 §3.
 *
 * <p><b>This transformation exists once.</b> Reimplementing it elsewhere is a review-blocking defect
 * (SC-120 §3.2), because two copies that disagree produce two different identifiers for one piece of
 * content and the ledger records whichever ran first.
 *
 * <pre>
 * wizardry:magic_wand         -> sweetcookie:wizardry.magic_wand
 * my_pack:fire/ember_block    -> sweetcookie:my_pack.fire_ember_block
 * Cool-Pack:Thing             -> sweetcookie:cool_pack.thing
 * </pre>
 *
 * <p>A pure function, with one exception that takes its input explicitly: two distinct Bedrock
 * identifiers can sanitise to the same string, and the collision rule needs pack load order to
 * decide which keeps the plain form. {@link #resolve} takes that order as an argument rather than
 * reading it from anywhere.
 *
 * <p>SC-120 §3.2 places this class in the Minecraft-dependent tree. It lives in {@code core/}
 * instead: it has no Minecraft dependency, it decides what gets written into a world save, and code
 * that decides that should be testable without compiling a Minecraft node.
 */
@SpecImpl("SC-120")
public final class IdMapper {

    /** Everything SweetCookie derives is under this namespace. */
    public static final String NAMESPACE = "sweetcookie";

    private IdMapper() {
    }

    /**
     * The logical identifier for one Bedrock identifier, ignoring collisions.
     *
     * <p>Use {@link #resolve} for a real pack set. This is the raw transformation, exposed because a
     * diagnostic sometimes needs to show what an identifier <em>would</em> have been.
     */
    public static String logicalIdOf(BedrockId id) {
        return NAMESPACE + ":" + sanitise(id.namespace()) + "." + sanitise(id.path());
    }

    /** Lowercase under {@code Locale.ROOT}, then everything outside {@code [a-z0-9_.-]} becomes {@code _}. */
    public static String sanitise(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            boolean allowed = c >= 'a' && c <= 'z'
                    || c >= '0' && c <= '9'
                    || c == '_' || c == '.' || c == '-';
            sb.append(allowed ? c : '_');
        }
        return sb.toString();
    }

    /**
     * Logical identifiers for a whole pack set, with collisions resolved. SC-120 §3.1.
     *
     * <p>The identifier appearing <b>later in load order</b> loses the plain form and gains
     * {@code _h} plus the first eight hex digits of the SHA-1 of its original Bedrock identifier.
     * Hashing the <em>original</em> rather than the sanitised form is what makes the suffix
     * distinguishing at all: the sanitised forms are equal, which is why there is a collision.
     *
     * @param inLoadOrder Bedrock identifiers in pack load order, earliest first (SC-100 §5)
     * @return each identifier's logical form, in the order given
     */
    public static Map<BedrockId, String> resolve(List<BedrockId> inLoadOrder) {
        Map<String, BedrockId> claimed = new LinkedHashMap<>();
        Map<BedrockId, String> out = new LinkedHashMap<>();
        for (BedrockId id : inLoadOrder) {
            String plain = logicalIdOf(id);
            BedrockId owner = claimed.get(plain);
            if (owner == null || owner.equals(id)) {
                claimed.put(plain, id);
                out.put(id, plain);
            } else {
                out.put(id, plain + "_h" + shortHash(id.toString()));
            }
        }
        return out;
    }

    /** True when two Bedrock identifiers would produce the same logical form. */
    public static boolean collide(BedrockId a, BedrockId b) {
        return !a.equals(b) && logicalIdOf(a).equals(logicalIdOf(b));
    }

    /** The first eight lowercase hex digits of the SHA-1 of {@code text} as UTF-8. */
    static String shortHash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 4; i++) {
                sb.append(Character.forDigit((digest[i] >> 4) & 0xF, 16));
                sb.append(Character.forDigit(digest[i] & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException impossible) {
            // SHA-1 is required of every JRE by the platform specification.
            throw new AssertionError("SHA-1 unavailable", impossible);
        }
    }
}
