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

    /** Where the Bedrock-texture-path to Java-texture-path table is written. */
    @get:OutputFile
    abstract val textureOutputFile: RegularFileProperty

    // Mojang's item_texture.json opens with a `//` line telling you not to copy it. Bedrock's
    // parser takes comments and so must this one; the default mapper stops on the first slash.
    private val json = ObjectMapper()
        .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_COMMENTS, true)
        .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_TRAILING_COMMA, true)

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

        val textures = textureTable(joined)
        val textureOut = textureOutputFile.get().asFile
        textureOut.parentFile.mkdirs()
        textureOut.writeText(renderTextures(textures))
        logger.lifecycle("generateBedrockConstants: ${textures.size} texture path(s)")

        val unmatched = bedrock.values.flatten().toSortedSet() - joined.keys
        val worklist = project.layout.buildDirectory.file("upstream/vanilla-names.unmatched.txt")
            .get().asFile
        worklist.parentFile.mkdirs()
        worklist.writeText(unmatched.joinToString("\n", postfix = "\n"))

        logger.lifecycle(
            "generateBedrockConstants: ${joined.size} mapping(s) " +
                // Against the PATH, not the whole key: every value now carries a `item.minecraft.`
                // or `block.minecraft.` prefix, so comparing the whole thing says "all of them".
                "(${joined.count { it.key != it.value.substringAfterLast('.') }} " +
                "where the spellings differ, " +
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

    /** Java's own language file, parsed once. */
    private val javaLang by lazy {
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
        logger.lifecycle("generateBedrockConstants: Java names from ${jar.name}")
        val text = ZipFile(jar).use { zip ->
            zip.getInputStream(zip.getEntry(entry)).readBytes().toString(Charsets.UTF_8)
        }
        json.readTree(text)
    }

    private fun readJavaLangKeys(): List<String> =
        javaLang.fieldNames().asSequence().toList()

    /** Java's own names, as display name -> the keys carrying it. */
    private fun readJavaNames(): Map<String, MutableSet<String>> {
        val out = mutableMapOf<String, MutableSet<String>>()
        val root = javaLang
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
        return out
    }

    /**
     * Bedrock's item texture paths, as path -> the Java texture the same picture lives at.
     *
     * <p>Two ways in, and both are checks rather than guesses.
     *
     * <p><b>By key.</b> `item_texture.json` names one picture under a key, and where that key is
     * also a language short name the name table already says which item it is - `totem` is
     * `textures/items/totem` and `item.minecraft.totem_of_undying`, so the picture belongs at
     * `item/totem_of_undying`.
     *
     * <p><b>By file name.</b> A key may hold an ARRAY instead: Bedrock puts all seven swords under
     * `sword` and picks by aux value, so the key names a family and no language entry matches it.
     * Every common tool and weapon is in one of those thirty keys. The file name is then checked
     * against Java's own item paths and accepted only on an exact hit - `diamond_axe` is a Java
     * item so it is taken, `gold_axe` is not (Java spells it `golden_axe`) so it is dropped rather
     * than bent into place. Refusing on a near-miss is the point: a wrong texture is worse than an
     * unchanged one, and only an exact name is evidence.
     *
     * <p>Java items only. A block's icon is drawn from its model rather than from a sprite, so
     * writing `item/<name>.png` for one would replace a picture nothing reads.
     */
    private fun textureTable(names: Map<String, String>): Map<String, String> {
        val javaItems = javaItemPaths()
        val out = sortedMapOf<String, String>()
        val root = json.readTree(
            cacheDir.get().asFile.resolve("resource_pack/textures/item_texture.json")
        )
        val data = root["texture_data"] ?: return out

        data.fieldNames().forEach { key ->
            val paths = mutableListOf<String>()
            val textures = data[key]["textures"] ?: return@forEach
            when {
                textures.isTextual -> paths += textures.asText()
                textures.isArray -> textures.forEach { element ->
                    when {
                        element.isTextual -> paths += element.asText()
                        element.isObject -> element["path"]?.asText()?.let { paths += it }
                    }
                }
            }

            // The key's own answer, when the name table has one and it names an ITEM.
            val byKey = names[key]?.takeIf { it.startsWith("item.minecraft.") }
                ?.removePrefix("item.minecraft.")

            for (path in paths) {
                val fileName = path.substringAfterLast('/')
                val target = when {
                    // One picture under a key the name table resolved: that key IS the item.
                    paths.size == 1 && byKey != null -> byKey
                    // Otherwise the file name has to prove itself against Java's own item list.
                    fileName in javaItems -> fileName
                    else -> null
                } ?: continue
                out[path] = "item/$target"
            }
        }
        return out
    }

    /** Every path Java names an item under, for the file-name check to be a check. */
    private fun javaItemPaths(): Set<String> {
        val out = mutableSetOf<String>()
        readJavaLangKeys().forEach { key ->
            if (key.startsWith("item.minecraft.")) {
                val path = key.removePrefix("item.minecraft.")
                if (!path.contains('.')) out += path
            }
        }
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

    private fun renderTextures(mapping: Map<String, String>): String = buildString {
        appendLine("# GENERATED by ./gradlew generateBedrockConstants. Do not edit.")
        appendLine("#")
        appendLine("# Where a picture Bedrock keeps at one path belongs in Java. Java ITEMS only: a")
        appendLine("# block's icon is drawn from its model rather than from a sprite, so replacing")
        appendLine("# item/<name>.png for one would change a picture nothing reads.")
        appendLine("#")
        appendLine("# Resolved either through the key's language entry, or - for the thirty keys that")
        appendLine("# hold a whole family, where every common tool and weapon lives - by checking the")
        appendLine("# file name against Java's own item paths and taking only an exact hit.")
        mapping.forEach { (bedrock, java) -> appendLine("$bedrock\t$java") }
    }
}
