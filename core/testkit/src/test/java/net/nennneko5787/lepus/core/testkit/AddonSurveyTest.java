package net.nennneko5787.lepus.core.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.nennneko5787.lepus.core.api.ProvesSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The survey, against a pack built for the purpose. {@code spec/process.md} §1.
 *
 * <p>The survey's whole value is that its answers are true, so the thing worth testing is that it
 * reports a component as unread when nothing reads it and read when something does — from the
 * parsers' own constants rather than from a list of its own.
 */
@ProvesSpec("SC-110")
class AddonSurveyTest {

    private static final String MANIFEST = """
            {
              "format_version": 2,
              "header": {
                "name": "Survey Fixture",
                "description": "Authored for this test.",
                "uuid": "5c00c1e0-0000-4000-8000-0000000000f1",
                "version": [1, 0, 0],
                "min_engine_version": [1, 21, 0]
              },
              "modules": [
                { "type": "data", "uuid": "5c00c1e0-0000-4000-8000-0000000000f2",
                  "version": [1, 0, 0] },
                { "type": "resources", "uuid": "5c00c1e0-0000-4000-8000-0000000000f3",
                  "version": [1, 0, 0] }
              ]
            }""";

    /** A block using one component this build reads and one it does not. */
    private static final String BLOCK = """
            {
              "format_version": "1.21.0",
              "minecraft:block": {
                "description": { "identifier": "survey:stone" },
                "components": {
                  "minecraft:light_emission": 7,
                  "minecraft:flammable": true,
                  "minecraft:material_instances": { "*": { "texture": "survey_stone" } }
                }
              }
            }""";

    private static final String TERRAIN = """
            {
              "texture_data": {
                "survey_stone": { "textures": "textures/blocks/survey_stone" }
              }
            }""";

    private static Path fixture(Path root, boolean withTexture) throws IOException {
        Path pack = root.resolve("survey_pack");
        Files.createDirectories(pack.resolve("blocks"));
        Files.createDirectories(pack.resolve("textures/blocks"));
        Files.writeString(pack.resolve("manifest.json"), MANIFEST);
        Files.writeString(pack.resolve("blocks/stone.json"), BLOCK);
        Files.writeString(pack.resolve("textures/terrain_texture.json"), TERRAIN);
        if (withTexture) {
            // Content is never read - the survey asks whether the path resolves, not what is in it.
            Files.write(pack.resolve("textures/blocks/survey_stone.png"), new byte[] {1, 2, 3});
        }
        return root;
    }

    @Test
    void reportsWhatIsUsedAndWhetherAnythingReadsIt(@TempDir Path root) throws IOException {
        AddonSurvey.Report report = AddonSurvey.of(fixture(root, true));

        assertEquals(1, report.packs());
        assertEquals(1, report.blocks());

        // Read, because BlockPhysics names it. Unread, because nothing does - and the survey knows
        // which is which by asking the parsers, so this assertion fails the day that changes.
        assertTrue(usage(report, "minecraft:light_emission").read());
        assertFalse(usage(report, "minecraft:flammable").read());
        assertTrue(usage(report, "minecraft:material_instances").read());
    }

    @Test
    void namesTheBlocksWhoseTextureResolvesToNothing(@TempDir Path root) throws IOException {
        // The offline form of SCE-2032. Three separate misdiagnoses in this project's history were
        // this exact question answered by hand, twice wrongly.
        AddonSurvey.Report present = AddonSurvey.of(fixture(root.resolve("with"), true));
        assertEquals(List.of(), present.blocksWithoutTexture());

        AddonSurvey.Report absent = AddonSurvey.of(fixture(root.resolve("without"), false));
        assertEquals(List.of("survey:stone"), absent.blocksWithoutTexture());
    }

    @Test
    void rendersSomethingAPersonCanRead(@TempDir Path root) throws IOException {
        List<String> lines = AddonSurvey.render(AddonSurvey.of(fixture(root, true)));
        assertTrue(lines.get(0).contains("blocks 1"), lines.get(0));
        assertTrue(lines.stream().anyMatch(line -> line.contains("minecraft:flammable")));
    }

    private static AddonSurvey.Usage usage(AddonSurvey.Report report, String id) {
        return report.blockComponents().stream()
                .filter(entry -> entry.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError(id + " was not reported at all"));
    }
}
