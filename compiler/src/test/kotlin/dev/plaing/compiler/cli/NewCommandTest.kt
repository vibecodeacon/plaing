package dev.plaing.compiler.cli

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NewCommandTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `creates project directory with correct structure`() {
        val cmd = NewCommand()
        cmd.execute("my-app", tempDir)

        val projectDir = File(tempDir, "my-app")
        assertTrue(projectDir.exists())
        assertTrue(File(projectDir, "src").exists())
        assertTrue(File(projectDir, "plaing.json").exists())
        assertTrue(File(projectDir, ".gitignore").exists())
    }

    @Test
    fun `creates all sample plaing files`() {
        val cmd = NewCommand()
        cmd.execute("test-project", tempDir)

        val src = File(tempDir, "test-project/src")
        assertTrue(File(src, "entities.plaing").exists())
        assertTrue(File(src, "events.plaing").exists())
        assertTrue(File(src, "handlers.plaing").exists())
        assertTrue(File(src, "pages.plaing").exists())
        assertTrue(File(src, "reactions.plaing").exists())
        assertTrue(File(src, "styles.plaing").exists())
    }

    @Test
    fun `generated plaing files are parseable`() {
        val cmd = NewCommand()
        cmd.execute("parse-test", tempDir)

        val src = File(tempDir, "parse-test/src")
        val plaingFiles = src.walkTopDown().filter { it.extension == "plaing" }.toList()
        assertTrue(plaingFiles.isNotEmpty())

        for (file in plaingFiles) {
            val source = file.readText()
            val tokens = dev.plaing.compiler.parser.Lexer(source, file.name).tokenize()
            val program = dev.plaing.compiler.parser.Parser(tokens, file.name).parse()
            assertTrue(program.declarations.isNotEmpty(), "Expected declarations in ${file.name}")
        }
    }

    @Test
    fun `plaing json has correct project name`() {
        val cmd = NewCommand()
        cmd.execute("cool-app", tempDir)

        val config = File(tempDir, "cool-app/plaing.json").readText()
        assertTrue(config.contains("\"name\": \"cool-app\""))
        assertTrue(config.contains("\"source\": \"src\""))
    }

    @Test
    fun `gitignore excludes build artifacts`() {
        val cmd = NewCommand()
        cmd.execute("git-test", tempDir)

        val gitignore = File(tempDir, "git-test/.gitignore").readText()
        assertTrue(gitignore.contains("build/"))
    }
}
