package com.harsh.desicrics

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps Simple and Full mode's screens apart. Simple mode may use Full's view models (state and actions)
 * and the shared helpers in ui/system, ui/text and ui/components, never its screens; Full mode never sees
 * Simple mode; and only shell/ knows about both.
 */
class ModeBoundaryTest {

    private val root = File("src/commonMain/kotlin/com/harsh/desicrics")
    private val pkg = "com.harsh.desicrics"

    private fun sources(dir: String) = File(root, dir).walkTopDown().filter { it.extension == "kt" }.toList()

    private fun File.imports() = readLines().filter { it.startsWith("import $pkg.") }.map { it.removePrefix("import ").substringBefore(" as ").trim() }

    /** The file under ui/ that declares [name] in [subPackage], if any. */
    private fun declaringFile(subPackage: String, name: String): File? =
        File(root, "ui/$subPackage").listFiles { f -> f.extension == "kt" }.orEmpty().firstOrNull { file ->
            Regex("""(class|object|interface|fun|val|typealias)\s+(<[^>]*>\s*)?([\w.]+\.)?$name\b""").containsMatchIn(file.readText())
        }

    @Test
    fun simpleUsesOnlyViewModelsAndSharedHelpersFromUi() {
        val shared = setOf("system", "text", "components")
        val offending = sources("simple").flatMap { file ->
            file.imports().filter { it.startsWith("$pkg.ui.") }.filterNot { import ->
                val parts = import.removePrefix("$pkg.ui.").split('.')
                val subPackage = parts.first()
                subPackage in shared || declaringFile(subPackage, parts.last())?.name?.let { it.endsWith("ViewModel.kt") || it.endsWith("ViewModels.kt") } == true
            }.map { "${file.name}: $it" }
        }
        assertTrue("Simple mode reaches into Full mode's screens:\n${offending.joinToString("\n")}", offending.isEmpty())
    }

    @Test
    fun simpleDoesNotUseFullNavigation() {
        val offending = sources("simple").flatMap { file -> file.imports().filter { it.startsWith("$pkg.navigation.") }.map { "${file.name}: $it" } }
        assertTrue("Simple mode uses Full mode's navigation:\n${offending.joinToString("\n")}", offending.isEmpty())
    }

    @Test
    fun fullDoesNotKnowSimple() {
        val offending = (sources("ui") + sources("navigation")).flatMap { file ->
            file.imports().filter { it.startsWith("$pkg.simple.") }.map { "${file.name}: $it" }
        }
        assertTrue("Full mode depends on Simple mode:\n${offending.joinToString("\n")}", offending.isEmpty())
    }

    @Test
    fun onlyTheShellKnowsBothModes() {
        val shell = File(root, "shell").canonicalPath
        val offending = root.walkTopDown().filter { it.extension == "kt" && !it.canonicalPath.startsWith(shell) }.filter { file ->
            val imports = file.imports()
            imports.any { it.startsWith("$pkg.simple.") } && imports.any { it.startsWith("$pkg.navigation.") }
        }.map { it.name }.toList()
        assertTrue("Only shell/ may use both modes: $offending", offending.isEmpty())
    }
}
