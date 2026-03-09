package dev.plaing.compiler.cli

import dev.plaing.compiler.codegen.CodeGenerator
import dev.plaing.compiler.errors.ErrorFormatter
import dev.plaing.compiler.parser.*
import java.io.File
import java.nio.file.*

/**
 * Dev server that watches .plaing files for changes and recompiles automatically.
 */
class DevCommand {

    fun execute(sourceDir: File) {
        println("plaing dev server starting...")
        println()

        // Initial build
        val success = buildProject(sourceDir)
        if (!success) {
            println()
            println("Fix the errors above, then save to rebuild automatically.")
        }

        // Watch for changes
        println()
        println("Watching for changes in ${sourceDir.absolutePath}...")
        println("Press Ctrl+C to stop.")
        println()

        watchAndRebuild(sourceDir)
    }

    private fun buildProject(sourceDir: File): Boolean {
        val plaingFiles = sourceDir.walkTopDown().filter { it.extension == "plaing" }.toList()

        if (plaingFiles.isEmpty()) {
            println("No .plaing files found in ${sourceDir.absolutePath}")
            return false
        }

        val allDeclarations = mutableListOf<Declaration>()
        for (f in plaingFiles) {
            try {
                val tokens = Lexer(f.readText(), f.name).tokenize()
                val program = Parser(tokens, f.name).parse()
                allDeclarations.addAll(program.declarations)
            } catch (e: LexerException) {
                println(ErrorFormatter.formatLexerError(e, f))
                return false
            } catch (e: ParseException) {
                println(ErrorFormatter.formatParseError(e, f))
                return false
            }
        }

        val combinedProgram = PlaingProgram(allDeclarations)
        val outputDir = File(sourceDir.parentFile ?: sourceDir, "build/generated/plaing")
        val generator = CodeGenerator(outputDir)
        val result = generator.generate(combinedProgram)

        println("Build successful!")
        println("  ${result.entityCount} entity(s), ${result.generatedFiles.size} file(s) generated")
        return true
    }

    private fun watchAndRebuild(sourceDir: File) {
        val watchService = FileSystems.getDefault().newWatchService()
        val watchedDirs = mutableSetOf<Path>()

        fun registerDirs(dir: File) {
            dir.walkTopDown().filter { it.isDirectory }.forEach { d ->
                val path = d.toPath()
                if (path !in watchedDirs) {
                    path.register(
                        watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE,
                    )
                    watchedDirs.add(path)
                }
            }
        }

        registerDirs(sourceDir)

        while (true) {
            val key = watchService.take() // blocks until a change
            var needsRebuild = false

            for (event in key.pollEvents()) {
                val context = event.context()
                if (context is Path && context.toString().endsWith(".plaing")) {
                    needsRebuild = true
                }
            }

            key.reset()

            if (needsRebuild) {
                // Small debounce — skip events that arrive within 200ms
                Thread.sleep(200)
                // Drain any queued events
                val extraKey = watchService.poll()
                extraKey?.let {
                    it.pollEvents()
                    it.reset()
                }

                println("Change detected, rebuilding...")
                println()
                val success = buildProject(sourceDir)
                if (!success) {
                    println()
                    println("Fix the errors above, then save to rebuild automatically.")
                }
                println()
            }
        }
    }
}
