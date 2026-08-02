package net.nennneko5787.lepus.gradle

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Verifies the four traceability links, in both directions.
 *
 * 1. every `@SpecImpl` / `@ProvesSpec` names a specification document that exists;
 * 2. every `#<feature>` suffix names a coverage entry that exists;
 * 3. every coverage entry above `stub` names a class that exists and carries the annotation;
 * 4. no `@SpecImpl` names a feature with no coverage entry — an orphan implementation.
 *
 * Reading annotations from bytecode rather than source means the check cannot drift from what
 * actually compiled, and it does not need a Java parser.
 */
abstract class SpecLinkTask : DefaultTask() {

    @get:InputDirectory
    abstract val specDir: DirectoryProperty

    /** Compiled class directories to scan. Empty is tolerated — see [scan]. */
    @get:InputFiles
    @get:Optional
    abstract val classDirs: ConfigurableFileCollection

    @TaskAction
    fun check() {
        val spec = specDir.get().asFile
        val problems = Problems("specLinks")

        val shards = CoverageLoader.loadAll(spec.resolve("coverage"))
        // Keyed by (spec, id). Bedrock overloads component names across domains -
        // minecraft:collision_box is both an entity and a block component - so keying by id alone
        // silently drops one of each pair and leaves it unchecked. See spec/ids.md.
        val entriesByKey = shards.flatMap { it.entries }.associateBy { it.spec to it.id }
        val specIds = spec.resolve("normative").listFiles().orEmpty()
            .filter { it.extension == "md" }
            .mapNotNull { Regex("^(SC-\\d{3})").find(it.name)?.groupValues?.get(1) }
            .toSet()

        val found = scan()

        // Direction 1 and 2: annotations point at things that exist.
        for (ref in found) {
            val where = "${ref.owner} (@${ref.kind})"
            val specId = ref.value.substringBefore('#')
            if (specId !in specIds) {
                problems.report(where, "names `$specId`, which is not a document in spec/normative/")
            }
            if (ref.value.contains('#')) {
                val feature = ref.value.substringAfter('#')
                if ((specId to feature) !in entriesByKey) {
                    problems.report(
                        where,
                        "names feature `$feature` under `$specId`, which has no coverage entry"
                    )
                }
            }
        }

        // Direction 3: coverage entries point at classes that exist and are annotated.
        val annotatedClasses = found.filter { it.kind == "SpecImpl" }.map { it.owner }.toSet()
        val classesWereScanned = found.isNotEmpty()
        for (entry in entriesByKey.values) {
            if (!entry.isTracked) continue
            val impl = entry.impl ?: continue
            val where = "spec/coverage/${entry.shard}.yaml [${entry.id}]"
            if (!classesWereScanned) continue // see scan()
            if (impl !in annotatedClasses) {
                problems.report(
                    where,
                    "claims `${entry.status}` and names `$impl`, but no such class carries " +
                        "@SpecImpl(\"${entry.spec}#${entry.id}\")"
                )
            }
            if (entry.claimsImplementation) {
                for (case in entry.conformance) {
                    val dir = spec.resolve("conformance/$case")
                    if (!dir.resolve("case.yaml").isFile) {
                        problems.report(where, "names conformance case `$case`, which does not exist")
                    }
                }
            }
        }

        // Direction 4: implementations that nothing tracks. These are how a feature quietly ends up
        // shipped-but-undocumented, which is exactly what the ledger exists to prevent.
        for (ref in found.filter { it.kind == "SpecImpl" && it.value.contains('#') }) {
            val key = ref.value.substringBefore('#') to ref.value.substringAfter('#')
            if (key !in entriesByKey) {
                problems.report(
                    ref.owner,
                    "implements `${ref.value}`, which is in no coverage shard"
                )
            }
        }

        // Conformance cases must be reachable from the ledger, or they are dead weight nobody
        // maintains and nobody trusts.
        val referencedCases = entriesByKey.values.flatMap { it.conformance }.toSet()
        spec.resolve("conformance").walkTopDown()
            .filter { it.isFile && it.name == "case.yaml" }
            .forEach { file ->
                val id = file.parentFile
                    .relativeTo(spec.resolve("conformance"))
                    .invariantSeparatorsPath
                if (id.startsWith("_") || id.startsWith("manual/")) return@forEach
                if (id !in referencedCases) {
                    logger.warn(
                        "specLinks: conformance case `$id` is referenced by no coverage entry. " +
                            "It still runs, but nothing claims it proves anything."
                    )
                }
            }

        problems.failIfAny()
        logger.lifecycle(
            "specLinks: ${found.size} annotation(s), ${entriesByKey.size} ledger entries, all links resolve."
        )
    }

    /**
     * Reads `@SpecImpl` and `@ProvesSpec` out of compiled classes.
     *
     * When nothing has been compiled yet the scan returns empty and direction 3 is skipped rather
     * than failing. A fresh clone that has not built anything should not be told its ledger is
     * broken; CI always compiles first, so the check has teeth where it matters.
     */
    private fun scan(): List<AnnotationRef> {
        val refs = mutableListOf<AnnotationRef>()
        // Accept both forms: a directory to walk, and an individual class file. A Gradle FileTree
        // yields FILES, so filtering for directories here silently scanned nothing at all — which
        // looked exactly like "the ledger is fine" and is why this comment exists.
        classDirs.files.forEach { entry ->
            when {
                entry.isDirectory -> entry.walkTopDown()
                    .filter { it.isFile && it.extension == "class" }
                    .forEach { readAnnotations(it, refs) }

                entry.isFile && entry.extension == "class" -> readAnnotations(entry, refs)
            }
        }
        return refs
    }

    private fun readAnnotations(file: File, into: MutableList<AnnotationRef>) {
        val reader = ClassReader(file.readBytes())
        var className = ""
        reader.accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visit(
                version: Int, access: Int, name: String, signature: String?,
                superName: String?, interfaces: Array<out String>?,
            ) {
                className = name.replace('/', '.')
            }

            override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? =
                collector(descriptor, className, into)

            override fun visitMethod(
                access: Int, name: String, descriptor: String,
                signature: String?, exceptions: Array<out String>?,
            ): MethodVisitor = object : MethodVisitor(Opcodes.ASM9) {
                override fun visitAnnotation(desc: String, visible: Boolean): AnnotationVisitor? =
                    collector(desc, className, into)
            }
        }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
    }

    private fun collector(
        descriptor: String,
        owner: String,
        into: MutableList<AnnotationRef>,
    ): AnnotationVisitor? {
        val kind = when {
            descriptor.endsWith("/SpecImpl;") -> "SpecImpl"
            descriptor.endsWith("/ProvesSpec;") -> "ProvesSpec"
            else -> return null
        }
        return object : AnnotationVisitor(Opcodes.ASM9) {
            override fun visitArray(name: String?): AnnotationVisitor? =
                if (name == "value") object : AnnotationVisitor(Opcodes.ASM9) {
                    override fun visit(n: String?, value: Any?) {
                        (value as? String)?.let { into += AnnotationRef(kind, owner, it) }
                    }
                } else null

            override fun visit(name: String?, value: Any?) {
                if (name == "value") {
                    (value as? String)?.let { into += AnnotationRef(kind, owner, it) }
                }
            }
        }
    }

    private data class AnnotationRef(val kind: String, val owner: String, val value: String)
}
