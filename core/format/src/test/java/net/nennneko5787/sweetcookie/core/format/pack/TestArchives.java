package net.nennneko5787.sweetcookie.core.format.pack;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds archives for tests.
 *
 * <p>Written by hand rather than committed as binary fixtures so that a test asserting "this entry
 * name is rejected" can contain the entry name in its own source. A checked-in zip with a
 * {@code ../} entry is a fixture nobody can read and everybody's virus scanner objects to.
 */
final class TestArchives {

    private final Map<String, byte[]> entries = new LinkedHashMap<>();

    private TestArchives() {
    }

    static TestArchives zip() {
        return new TestArchives();
    }

    TestArchives with(String path, String content) {
        entries.put(path, content.getBytes(StandardCharsets.UTF_8));
        return this;
    }

    TestArchives with(String path, byte[] content) {
        entries.put(path, content);
        return this;
    }

    byte[] bytes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    Path writeTo(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, bytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }

    /** A minimal, valid behavior-pack manifest. */
    static String manifest(String uuid, String name, int[] version) {
        return """
                {
                  "format_version": 2,
                  "header": {
                    "uuid": "%s",
                    "name": "%s",
                    "version": [%d, %d, %d],
                    "min_engine_version": [1, 21, 0]
                  },
                  "modules": [
                    { "type": "data", "uuid": "%s", "version": [%d, %d, %d] }
                  ]
                }
                """.formatted(uuid, name, version[0], version[1], version[2],
                uuid, version[0], version[1], version[2]);
    }
}
