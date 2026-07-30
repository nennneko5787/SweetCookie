package net.nennneko5787.sweetcookie.gradle

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes

/**
 * Emits a minimal class file carrying `@SpecImpl` or `@ProvesSpec`, for testing the bytecode scan.
 *
 * Generating the class rather than compiling one means the test needs no javac, runs in
 * milliseconds, and — importantly — exercises the scanner against the *array-valued* annotation
 * encoding that the real annotations use, which is where a naive visitor gets it wrong.
 */
object AnnotatedClassWriter {

    private const val PACKAGE = "net/nennneko5787/sweetcookie/core/api"

    fun write(spec: AnnotationSpec): ByteArray {
        val internalName = spec.className.replace('.', '/')
        val writer = ClassWriter(0)
        writer.visit(
            Opcodes.V21,
            Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER,
            internalName,
            null,
            "java/lang/Object",
            null,
        )

        // Retention is CLASS, so the annotation is present in the bytecode but not visible at
        // runtime — visible = false. A scanner that only reads runtime-visible annotations would
        // find nothing here, which is exactly the failure mode worth pinning down.
        val descriptor = "L$PACKAGE/${spec.annotation};"
        val annotation = writer.visitAnnotation(descriptor, false)
        val array = annotation.visitArray("value")
        spec.values.forEach { array.visit(null, it) }
        array.visitEnd()
        annotation.visitEnd()

        writer.visitEnd()
        return writer.toByteArray()
    }
}
