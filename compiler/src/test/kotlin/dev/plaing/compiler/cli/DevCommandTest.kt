package dev.plaing.compiler.cli

import dev.plaing.compiler.codegen.CodeGenerator
import dev.plaing.compiler.parser.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertTrue

class DevCommandTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `build from scaffolded project produces output`() {
        // Create a project using NewCommand
        val cmd = NewCommand()
        cmd.execute("dev-test", tempDir)

        val sourceDir = File(tempDir, "dev-test/src")
        val plaingFiles = sourceDir.walkTopDown().filter { it.extension == "plaing" }.toList()
        assertTrue(plaingFiles.isNotEmpty())

        // Parse all files (simulates what dev command does)
        val allDeclarations = mutableListOf<Declaration>()
        for (f in plaingFiles) {
            val tokens = Lexer(f.readText(), f.name).tokenize()
            val program = Parser(tokens, f.name).parse()
            allDeclarations.addAll(program.declarations)
        }

        val combinedProgram = PlaingProgram(allDeclarations)
        val outputDir = File(tempDir, "dev-test/build/generated/plaing")
        val generator = CodeGenerator(outputDir)
        val result = generator.generate(combinedProgram)

        assertTrue(result.generatedFiles.isNotEmpty())
        assertTrue(result.entityCount > 0)
        // Verify output files exist
        for (path in result.generatedFiles) {
            assertTrue(File(path).exists(), "Expected generated file: $path")
        }
    }

    @Test
    fun `end to end new and build`() {
        // Scaffold a project
        NewCommand().execute("e2e-test", tempDir)

        // Build it using the same logic as CLI
        val sourceDir = File(tempDir, "e2e-test/src")
        val plaingFiles = sourceDir.walkTopDown().filter { it.extension == "plaing" }.toList()

        val allDeclarations = mutableListOf<Declaration>()
        for (f in plaingFiles) {
            val tokens = Lexer(f.readText(), f.name).tokenize()
            val program = Parser(tokens, f.name).parse()
            allDeclarations.addAll(program.declarations)
        }

        val combinedProgram = PlaingProgram(allDeclarations)
        val outputDir = File(tempDir, "e2e-test/build/generated/plaing")
        val generator = CodeGenerator(outputDir)
        val result = generator.generate(combinedProgram)

        // Should have entities, events, handlers, pages, reactions, styles
        val entities = combinedProgram.declarations.filterIsInstance<EntityDeclaration>()
        val events = combinedProgram.declarations.filterIsInstance<EventDeclaration>()
        val handlers = combinedProgram.declarations.filterIsInstance<HandlerDeclaration>()
        val pages = combinedProgram.declarations.filterIsInstance<PageDeclaration>()
        val reactions = combinedProgram.declarations.filterIsInstance<ReactionDeclaration>()
        val styles = combinedProgram.declarations.filterIsInstance<StyleDeclaration>()

        assertTrue(entities.size >= 2, "Expected at least 2 entities")
        assertTrue(events.size >= 3, "Expected at least 3 events")
        assertTrue(handlers.size >= 1, "Expected at least 1 handler")
        assertTrue(pages.size >= 1, "Expected at least 1 page")
        assertTrue(reactions.size >= 2, "Expected at least 2 reactions")
        assertTrue(styles.size >= 1, "Expected at least 1 style")

        // Generated files should include server, client, models, etc.
        val fileNames = result.generatedFiles.map { File(it).name }
        assertTrue(fileNames.any { it.endsWith("Repository.kt") }, "Expected repository files")
        assertTrue(fileNames.any { it == "ServerApp.kt" }, "Expected ServerApp.kt")
        assertTrue(fileNames.any { it == "ClientApp.kt" }, "Expected ClientApp.kt")
        assertTrue(fileNames.any { it == "Reactions.kt" }, "Expected Reactions.kt")
    }
}
