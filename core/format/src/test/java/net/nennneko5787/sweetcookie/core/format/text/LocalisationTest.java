package net.nennneko5787.sweetcookie.core.format.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.nennneko5787.sweetcookie.core.api.ProvesSpec;
import net.nennneko5787.sweetcookie.core.format.pack.IndexedVfs;
import org.junit.jupiter.api.Test;

/** {@code texts/} and the {@code .lang} format. SC-100 §8. */
@ProvesSpec("SC-100")
class LocalisationTest {

    @Test
    @ProvesSpec("SC-100")
    void splitsOnTheFirstEqualsOnly() {
        Map<String, String> entries = LangFile.parse("item.wand=Wand = of Power");
        assertEquals("Wand = of Power", entries.get("item.wand"));
    }

    @Test
    @ProvesSpec("SC-100")
    void doesNotStripATrailingHashComment() {
        // Treating # as an end-of-line comment is what a properties parser does, and it silently
        // truncates any translation containing a hash - which is most translations involving a
        // number. Bedrock does not strip it, so neither do we.
        assertEquals("Slot #1", LangFile.parse("ui.slot=Slot #1").get("ui.slot"));
    }

    @Test
    @ProvesSpec("SC-100")
    void skipsDoubleHashCommentLinesAndKeepsEverythingElse() {
        Map<String, String> entries = LangFile.parse("""
                ## a comment
                   ## an indented comment
                a=1

                b=2
                not a pair
                c=
                """);
        assertEquals(Map.of("a", "1", "b", "2", "c", ""), entries);
    }

    @Test
    @ProvesSpec("SC-100")
    void trimsOnlyTheTrailingCarriageReturn() {
        Map<String, String> entries = LangFile.parse("a=1\r\n b = 2 \r\n");
        assertEquals("1", entries.get("a"));
        // Internal and surrounding whitespace is kept: packs rely on exact keys.
        assertEquals(" 2 ", entries.get(" b "));
    }

    @Test
    @ProvesSpec("SC-100")
    void readsEveryLocaleAPackShips() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("texts/languages.json", "[\"en_US\", \"ja_JP\"]".getBytes(StandardCharsets.UTF_8));
        files.put("texts/en_US.lang", "pack.name=Wizardry".getBytes(StandardCharsets.UTF_8));
        files.put("texts/ja_JP.lang", "pack.name=W".getBytes(StandardCharsets.UTF_8));
        files.put("texts/de_DE.lang", "pack.name=Zauberei".getBytes(StandardCharsets.UTF_8));

        Localisation texts = Localisation.read(IndexedVfs.of(files));

        // languages.json is a convenience; the .lang files on disk are the real answer, so a locale
        // it forgot to list still loads.
        assertEquals(List.of("en_US", "ja_JP", "de_DE"), texts.languages());
        assertEquals("Zauberei", texts.resolve("pack.name", "de_DE"));
    }

    @Test
    @ProvesSpec("SC-100")
    void fallsBackToEnglishAndThenToTheKeyItself() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("texts/en_US.lang", "pack.name=Wizardry".getBytes(StandardCharsets.UTF_8));
        Localisation texts = Localisation.read(IndexedVfs.of(files));

        assertEquals("Wizardry", texts.resolve("pack.name", "fr_FR"));
        // Returning the key beats returning nothing: an add-on with a blank name in the management
        // screen is strictly less useful than one showing an unresolved key.
        assertEquals("pack.description", texts.resolve("pack.description", "en_US"));
        assertTrue(texts.hasKey("pack.name"));
        assertFalse(texts.hasKey("pack.description"));
    }

    @Test
    @ProvesSpec("SC-100")
    void survivesAPackWithNoTextsAtAll() {
        Localisation texts = Localisation.read(IndexedVfs.of(Map.of("manifest.json", new byte[0])));
        assertEquals(List.of(), texts.languages());
        assertEquals("pack.name", texts.resolve("pack.name", "en_US"));
    }
}
