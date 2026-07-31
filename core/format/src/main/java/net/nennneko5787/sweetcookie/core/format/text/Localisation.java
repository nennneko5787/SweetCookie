package net.nennneko5787.sweetcookie.core.format.text;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.nennneko5787.sweetcookie.core.api.SpecImpl;
import net.nennneko5787.sweetcookie.core.format.json.Json;
import net.nennneko5787.sweetcookie.core.format.json.JsonValue;
import net.nennneko5787.sweetcookie.core.format.pack.ByteSource;
import net.nennneko5787.sweetcookie.core.format.pack.PackVfs;
import net.nennneko5787.sweetcookie.core.format.pack.VfsPath;

/**
 * A pack's {@code texts/} directory. SC-100 §8.
 *
 * <p>Manifest {@code name} and {@code description} are frequently {@code .lang} keys, so this is
 * read at pack-load time rather than lazily — it is small, and the add-on screen needs it before
 * anything else is parsed.
 *
 * <p>Which locale to resolve against is <b>not</b> decided here. A dedicated server and each of its
 * clients want different ones, so this holds every locale the pack ships and resolution takes the
 * locale as an argument.
 *
 * @param byLocale  locale to entries, in the order the pack listed them
 * @param languages {@code texts/languages.json}, or the locales found on disk when it is absent
 */
@SpecImpl({"SC-100", "SC-100#texts/lang", "SC-100#texts/languages"})
public record Localisation(Map<String, Map<String, String>> byLocale, List<String> languages) {

    /** The locale used when a pack ships no matching one. Bedrock's own default. */
    public static final String FALLBACK_LOCALE = "en_US";

    public static final Localisation EMPTY = new Localisation(Map.of(), List.of());

    public Localisation {
        Map<String, Map<String, String>> copy = new LinkedHashMap<>();
        byLocale.forEach((locale, entries) -> copy.put(locale, Map.copyOf(entries)));
        byLocale = Map.copyOf(copy);
        languages = List.copyOf(languages);
    }

    /**
     * Resolves {@code key} in {@code locale}, falling back to {@code en_US} and then to the key.
     *
     * <p>Returning the key rather than empty is deliberate: a manifest {@code name} of
     * {@code pack.name} that resolves to nothing would show an add-on with a blank name in the
     * management screen, which is strictly less useful than showing the unresolved key.
     */
    public String resolve(String key, String locale) {
        String direct = byLocale.getOrDefault(locale, Map.of()).get(key);
        if (direct != null) {
            return direct;
        }
        String fallback = byLocale.getOrDefault(FALLBACK_LOCALE, Map.of()).get(key);
        return fallback != null ? fallback : key;
    }

    /** True when {@code text} looks like a {@code .lang} key rather than a literal name. */
    public boolean hasKey(String key) {
        return byLocale.values().stream().anyMatch(entries -> entries.containsKey(key));
    }

    /**
     * Reads {@code texts/} out of a pack.
     *
     * <p>Never fails. An unreadable {@code .lang} file yields no entries for that locale, and the
     * pack loads with unresolved keys — SC-000 §10, and the alternative is a pack that refuses to
     * appear because one translation is broken.
     */
    public static Localisation read(PackVfs vfs) {
        List<String> languages = declaredLanguages(vfs);
        Map<String, Map<String, String>> byLocale = new LinkedHashMap<>();

        List<String> files = vfs.walk("texts")
                .filter(path -> VfsPath.extension(path).equals("lang"))
                .toList();
        List<String> ordered = new ArrayList<>(languages);
        for (String path : files) {
            String locale = stripExtension(VfsPath.fileName(path));
            if (!ordered.contains(locale)) {
                ordered.add(locale);
            }
        }
        for (String locale : ordered) {
            Optional<ByteSource> source = vfs.read("texts/" + locale + ".lang");
            if (source.isEmpty()) {
                continue;
            }
            try {
                byLocale.put(locale, LangFile.parse(source.get().readUtf8()));
            } catch (IOException unreadable) {
                // Nothing to report against here: the caller has the provenance and this is one
                // file of a pack that is otherwise fine.
                byLocale.put(locale, Map.of());
            }
        }
        return new Localisation(byLocale, ordered);
    }

    private static List<String> declaredLanguages(PackVfs vfs) {
        Optional<ByteSource> source = vfs.read("texts/languages.json");
        if (source.isEmpty()) {
            return List.of();
        }
        try {
            JsonValue root = Json.parse(source.get().readUtf8());
            return root.asArray()
                    .map(array -> array.values().stream().flatMap(v -> v.asString().stream()).toList())
                    .orElse(List.of());
        } catch (IOException | RuntimeException malformed) {
            // languages.json is a convenience: the .lang files on disk are the real answer.
            return List.of();
        }
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }
}
