package dev.plaing.compiler.errors

import dev.plaing.compiler.parser.LexerException
import dev.plaing.compiler.parser.ParseException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertTrue

class ErrorFormatterTest {

    @TempDir
    lateinit var tempDir: File

    private fun createFile(content: String): File {
        val file = File(tempDir, "test.plaing")
        file.writeText(content)
        return file
    }

    @Test
    fun `formats lexer error with source context`() {
        val source = "entity User:\n  name is Text\n  @invalid\n  email is Text"
        val file = createFile(source)
        val error = LexerException("Unexpected character '@'", 3, 3, "test.plaing")

        val formatted = ErrorFormatter.formatLexerError(error, file)
        assertContains(formatted, "test.plaing")
        assertContains(formatted, "line 3")
        assertContains(formatted, "@invalid")
        assertContains(formatted, "don't understand")
    }

    @Test
    fun `formats parse error with source context`() {
        val source = "entity User\n  name is Text"
        val file = createFile(source)
        val error = ParseException("Expected COLON but found NEWLINE ('\\n')", 1, 12, "test.plaing")

        val formatted = ErrorFormatter.formatParseError(error, file)
        assertContains(formatted, "test.plaing")
        assertContains(formatted, "line 1")
        assertContains(formatted, "colon")
    }

    @Test
    fun `friendly message for unterminated string`() {
        val source = "entity User:\n  name is \"hello"
        val file = createFile(source)
        val error = LexerException("Unterminated string literal", 2, 12, "test.plaing")

        val formatted = ErrorFormatter.formatLexerError(error, file)
        assertContains(formatted, "missing its closing quote")
    }

    @Test
    fun `friendly message for inconsistent indentation`() {
        val source = "entity User:\n  name is Text\n    email is Text"
        val file = createFile(source)
        val error = LexerException("Inconsistent indentation", 3, 1, "test.plaing")

        val formatted = ErrorFormatter.formatLexerError(error, file)
        assertContains(formatted, "indentation")
        assertContains(formatted, "doesn't match")
    }

    @Test
    fun `friendly message for expected type`() {
        val file = createFile("entity User:\n  name is ???")
        val error = ParseException("Expected type but found '???'", 2, 12, "test.plaing")

        val formatted = ErrorFormatter.formatParseError(error, file)
        assertContains(formatted, "type")
    }

    @Test
    fun `friendly message for expected declaration`() {
        val file = createFile("foo bar baz")
        val error = ParseException(
            "Expected a declaration (entity, event, handle, page, on, style) but found 'foo'",
            1, 1, "test.plaing"
        )

        val formatted = ErrorFormatter.formatParseError(error, file)
        assertContains(formatted, "entity")
        assertContains(formatted, "event")
    }

    @Test
    fun `friendly message for expected INDENT`() {
        val file = createFile("entity User:")
        val error = ParseException("Expected INDENT but found EOF ('')", 1, 13, "test.plaing")

        val formatted = ErrorFormatter.formatParseError(error, file)
        assertContains(formatted, "indented block")
    }

    @Test
    fun `shows surrounding lines for context`() {
        val source = "entity User:\n  name is Text\n  bad-stuff!\n  email is Text\n  age is Number"
        val file = createFile(source)
        val error = LexerException("Unexpected character '!'", 3, 13, "test.plaing")

        val formatted = ErrorFormatter.formatLexerError(error, file)
        // Should show lines around the error
        assertContains(formatted, "name is Text")
        assertContains(formatted, "bad-stuff!")
        assertContains(formatted, "email is Text")
    }

    @Test
    fun `error at first line shows correct context`() {
        val source = "@invalid"
        val file = createFile(source)
        val error = LexerException("Unexpected character '@'", 1, 1, "test.plaing")

        val formatted = ErrorFormatter.formatLexerError(error, file)
        assertContains(formatted, "line 1")
        assertContains(formatted, "@invalid")
    }

    @Test
    fun `generic Expected X but found Y gets friendlier`() {
        val file = createFile("entity User\n  name is Text")
        val error = ParseException("Expected COLON but found IDENTIFIER ('name')", 1, 13, "test.plaing")

        val formatted = ErrorFormatter.formatParseError(error, file)
        assertContains(formatted, "colon")
        assertContains(formatted, "name")
    }

    @Test
    fun `friendly message for expected statement`() {
        val file = createFile("handle LOGIN:\n  foo bar")
        val error = ParseException("Expected statement but found 'foo'", 2, 3, "test.plaing")

        val formatted = ErrorFormatter.formatParseError(error, file)
        assertContains(formatted, "find")
        assertContains(formatted, "emit")
    }

    @Test
    fun `friendly message for expected expression`() {
        val file = createFile("handle TEST:\n  emit FOO with x =")
        val error = ParseException("Expected expression but found ''", 2, 22, "test.plaing")

        val formatted = ErrorFormatter.formatParseError(error, file)
        assertContains(formatted, "value")
    }
}
