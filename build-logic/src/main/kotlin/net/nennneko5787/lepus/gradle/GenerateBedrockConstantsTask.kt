package net.nennneko5787.lepus.gradle

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File
import java.util.zip.ZipFile
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml

/**
 * Emits the Bedrock-to-Java vanilla name table from the pinned upstream snapshot.
 *
 * **Deliberately manual and its output committed**, like the lock it reads: the build works offline
 * and a contributor without network access still compiles. Running it is how a new upstream snapshot
 * reaches the table, and the diff is meant to be read.
 *
 * ## Why a table exists at all
 *
 * A pack renaming or retexturing a vanilla item writes Bedrock's internal short name for it -
 * `item.totem.name`, `textures/items/totem.png` - where Java spells the same item
 * `totem_of_undying`. The identifiers themselves already agree; only these two spellings do not.
 * Measured against the real files, **564 of 1,494 short names differ from the Java path**, so
 * assuming they match is wrong for more than a third of the game.
 *
 * ## The join, and why it refuses
 *
 * The only bridge between the two spellings is the English display name. So a mapping is kept only
 * when the name is **unique on both sides**. Bedrock has one `banner_pattern`; Java has nine items
 * called "Banner Pattern". Choosing one would be a fitted constant, which this project has paid for
 * before - so all nine are dropped and the item keeps its vanilla name and texture. Refusing is the
 * safe direction: nothing is renamed rather than the wrong thing being renamed.
 *
 * What the join cannot reach is written out as a worklist rather than guessed at, and
 * `vanilla-names.manual.yaml` is where a human's answer goes. Hand entries win over the join.
 *
 * ## What may be emitted
 *
 * An id beside an id, and nothing else. Mojang's display strings are the *key* of the join and do
 * not survive it - constitution rule 10 forbids shipping upstream content, and a table of 1,500
 * English item names would be exactly that. The correspondence is a fact about two games.
 */
abstract class GenerateBedrockConstantsTask : DefaultTask() {

    @get:InputDirectory
    abstract val cacheDir: DirectoryProperty

    @get:InputDirectory
    abstract val specDir: DirectoryProperty

    /**
     * Jars to search for Java's own `en_us.json`.
     *
     * A file collection rather than a path, because the path is not ours to know: the Minecraft jar
     * lives wherever the loader's Gradle plugin put it, and that differs per machine, per loader and
     * per version. The task looks inside each for the one entry it needs, so nothing here is a guess
     * that goes stale on somebody else's checkout.
     */
    @get:InputFiles
    abstract val minecraftJars: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    private val json = ObjectMapper()

    @TaskAction
    fun generate() {
        val bedrock = readBedrockNames(
            cacheDir.get().asFile.resolve("resource_pack/texts/en_US.lang")
        )
        val java = readJavaNames()

        // Unique on BOTH sides, or not at all. `uniqueBy` keeps the display names that name exactly
        // one thing in each game; everything else is ambiguous in a way no rule here can settle.
        val bedrockUnique = uniqueBy(bedrock)
        val javaUnique = uniqueBy(java)

        val joined = sortedMapOf<String, String>()
        for ((display, shortName) in bedrockUnique) {
            val path = javaUnique[display] ?: continue
            joined[shortName] = path
        }

        val manual = readManual(specDir.get().asFile.resolve("upstream/vanilla-names.manual.yaml"))
        // A human's answer wins. The join is evidence, not authority - it cannot see that Bedrock's
        // `banner_pattern` is a family rather than an item, and a person can.
        joined.putAll(manual)

        val out = outputFile.get().asFile
        out.parentFile.mkdirs()
        out.writeText(render(joined))

        val unmatched = bedrock.values.flatten().toSortedSet() - joined.keys
        val worklist = project.layout.buildDirectory.file("upstream/vanilla-names.unmatched.txt")
            .get().asFile
        worklist.parentFile.mkdirs()
        worklist.writeText(unmatched.joinToString("\n", postfix = "\n"))

        logger.lifecycle(
            "generateBedrockConstants: ${joined.size} mapping(s) " +
                "(${joined.count { it.key != it.value }} where the spellings differ, " +
                "${manual.size} by hand), ${unmatched.size} unmatched."
        )
        logger.lifecycle("  table    ${out.relativeTo(project.projectDir)}")
        logger.lifecycle("  worklist ${worklist.relativeTo(project.projectDir)}")
    }

    /**
     * Bedrock's own names, as display name -> the short names carrying it.
     *
     * `item.` and `tile.` together: a block's item form is spelled `tile.` in Bedrock and
     * `block.minecraft.` in Java, and both games mean one item by it. Reading only `item.` lost
     * every door and sign.
     */
    private fun readBedrockNames(file: File): Map<String, MutableSet<String>> {
        val out = mutableMapOf<String, MutableSet<String>>()
        file.readText().removePrefix("﻿").lineSequence().forEach { raw ->
            val line = raw.trimEnd('\r')
            if (!line.startsWith("item.") && !line.startsWith("tile.")) return@forEach
            val equals = line.indexOf('=')
            if (equals < 0) return@forEach
            val key = line.substring(0, equals)
            if (!key.endsWith(".name")) return@forEach
            val shortName = key.substringAfter('.').removeSuffix(".name")
            // A value may carry a translator's note after a tab, and a comment after whitespace.
            // Both are in the real file; splitting on '=' alone kept "Totem of Undying\t#" once.
            val value = line.substring(equals + 1)
                .substringBefore('\t')
                .replace(Regex("\\s+#.*$"), "")
                .trim()
            if (shortName.isNotEmpty() && value.isNotEmpty()) {
                out.getOrPut(value) { mutableSetOf() } += shortName
            }
        }
        return out
    }

    /** Java's own names, as display name -> the item paths carrying it. */
    private fun readJavaNames(): Map<String, MutableSet<String>> {
        val entry = "assets/minecraft/lang/en_us.json"
        val jar = minecraftJars.files.firstOrNull { candidate ->
            candidate.isFile && candidate.extension == "jar" && runCatching {
                ZipFile(candidate).use { it.getEntry(entry) != null }
            }.getOrDefault(false)
        } ?: throw org.gradle.api.GradleException(
            "None of the ${minecraftJars.files.size} candidate jar(s) contains $entry.\n" +
                "This task reads Java's own language file to join against Bedrock's; without it\n" +
                "there is no bridge between the two spellings. See spec/upstream/fetch.md."
        )

        val text = ZipFile(jar).use { zip ->
            zip.getInputStream(zip.getEntry(entry)).readBytes().toString(Charsets.UTF_8)
        }
        val out = mutableMapOf<String, MutableSet<String>>()
        val root = json.readTree(text)
        root.fieldNames().forEach { key ->
            val path = when {
                key.startsWith("item.minecraft.") -> key.removePrefix("item.minecraft.")
                key.startsWith("block.minecraft.") -> key.removePrefix("block.minecraft.")
                else -> return@forEach
            }
            // A key with a further dot is a variant or a subtitle, not an item.
            if (path.contains('.')) return@forEach
            val value = root[key].asText().trim()
            // The WHOLE key, not the path. Java names a block's item form under `block.minecraft.`
            // and everything else under `item.minecraft.`, and a caller renaming white wool needs
            // the first - deriving it from the path alone is not possible, and guessing `item.`
            // renames nothing at all. The path is still one substring away for the texture side.
            if (value.isNotEmpty()) out.getOrPut(value) { mutableSetOf() } += key
        }
        logger.lifecycle("generateBedrockConstants: Java names from ${jar.name}")
        return out
    }

    /** The display names naming exactly one thing, as name -> that thing. */
    private fun uniqueBy(byDisplay: Map<String, Set<String>>): Map<String, String> =
        byDisplay.filterValues { it.size == 1 }.mapValues { it.value.first() }

    @Suppress("UNCHECKED_CAST")
    private fun readManual(file: File): Map<String, String> {
        if (!file.isFile) return emptyMap()
        val options = LoaderOptions().apply { isAllowDuplicateKeys = false }
        val root = Yaml(options).load<Map<String, Any?>>(file.readText()) ?: return emptyMap()
        return (root["names"] as? Map<String, String>).orEmpty()
    }

    private fun render(mapping: Map<String, String>): String = buildString {
        appendLine("# GENERATED by ./gradlew generateBedrockConstants. Do not edit.")
        appendLine("#")
        appendLine("# Bedrock's internal short name for a vanilla item, and Java's translation key for it.")
        appendLine("# The whole key, because a block's item form is `block.minecraft.` in Java and")
        appendLine("# everything else `item.minecraft.`, and that is not derivable from the path.")
        appendLine("# Joined on the English display name and kept only where that name is unique in BOTH")
        appendLine("# games; a hand-written answer in spec/upstream/vanilla-names.manual.yaml overrides.")
        appendLine("#")
        appendLine("# No upstream content is reproduced here - an id beside an id is a fact about two")
        appendLine("# games, not Mojang's data. Constitution rule 10, spec/upstream/fetch.md.")
        mapping.forEach { (shortName, path) -> appendLine("$shortName\t$path") }
    }
}
