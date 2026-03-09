package dev.plaing.compiler.errors

import dev.plaing.compiler.parser.LexerException
import dev.plaing.compiler.parser.ParseException
import java.io.File

/**
 * Formats compiler errors as friendly, plain-English messages with source location context.
 */
object ErrorFormatter {

    fun formatLexerError(e: LexerException, file: File): String {
        val message = friendlyLexerMessage(e.message ?: "Unknown error")
        return formatError(message, file, e.line, e.column)
    }

    fun formatParseError(e: ParseException, file: File): String {
        val message = friendlyParseMessage(e.message ?: "Unknown error")
        return formatError(message, file, e.line, e.column)
    }

    fun formatError(message: String, file: File, line: Int, column: Int): String {
        val sb = StringBuilder()
        sb.appendLine("Something went wrong in ${file.name} on line $line:")
        sb.appendLine()

        // Show source context
        val lines = try { file.readLines() } catch (_: Exception) { emptyList() }
        if (lines.isNotEmpty() && line >= 1) {
            val start = maxOf(1, line - 2)
            val end = minOf(lines.size, line + 2)

            for (i in start..end) {
                val lineContent = lines[i - 1]
                val prefix = if (i == line) "  > " else "    "
                val lineNum = "$i".padStart(4)
                sb.appendLine("$prefix$lineNum | $lineContent")
            }

            // Show caret pointing to the error column
            if (column > 0) {
                val padding = " ".repeat(column + 9) // account for prefix + line num + " | "
                sb.appendLine("$padding^")
            }
        }

        sb.appendLine()
        sb.appendLine("  $message")
        return sb.toString()
    }

    private fun friendlyLexerMessage(raw: String): String {
        // Strip file:line:col prefix if present
        val msg = raw.substringAfter(": ", raw)

        return when {
            msg.contains("Unexpected character") -> {
                val char = Regex("'(.)'").find(msg)?.groupValues?.get(1)
                if (char != null) "I found a character '$char' that I don't understand. Check for typos or misplaced punctuation."
                else "There's an unexpected character here. Check for typos."
            }
            msg.contains("Unterminated string") ->
                "This string is missing its closing quote (\"). Make sure every string starts and ends with a quote mark."
            msg.contains("Inconsistent indentation") ->
                "The indentation here doesn't match the rest of the block. Use the same number of spaces for lines at the same level."
            else -> msg
        }
    }

    private fun friendlyParseMessage(raw: String): String {
        val msg = raw.substringAfter(": ", raw)

        return when {
            msg.contains("Expected COLON") ->
                "I expected a colon (:) here. Most declarations need a colon after their name, like \"entity User:\" or \"handle LOGIN_ATTEMPT:\""
            msg.contains("Expected INDENT") ->
                "I expected an indented block here. Add some content on the next line, indented with spaces."
            msg.contains("Expected a declaration") ->
                "I can only understand these at the top level: entity, event, handle, page, on, or style. Did you mean one of those?"
            msg.contains("Expected type") ->
                "I need a type here, like Text, Number, Boolean, Date, or the name of an entity."
            msg.contains("Expected field modifier") ->
                "After the comma, I expected a modifier like required, unique, hidden, or default."
            msg.contains("Expected comparison operator") ->
                "I need a comparison word here, like \"is\", \"matches\", \"contains\", or \"starts with\"."
            msg.contains("Expected expression") ->
                "I need a value here — a quoted string like \"hello\", a number, true/false, or a reference like User.name."
            msg.contains("Expected statement") ->
                "Inside a handler, I understand: find, create, update, delete, emit, if, and stop."
            msg.contains("Expected UI element") ->
                "Inside a page, I understand: layout, heading, form, input, and button."
            msg.contains("Expected input property") ->
                "Input properties can be: placeholder \"text\", type secret, or binds to fieldname."
            msg.contains("Expected reaction action") ->
                "Inside an \"on\" block, I understand: store, navigate to, and show alert."
            msg.contains("Expected") -> {
                // Generic "Expected X but found Y" — make it friendlier
                val match = Regex("Expected (\\w+) but found (\\w+) \\('(.+)'\\)").find(msg)
                if (match != null) {
                    val expected = tokenToFriendly(match.groupValues[1])
                    val foundVal = match.groupValues[3]
                    "I expected $expected here, but found \"$foundVal\" instead."
                } else {
                    msg
                }
            }
            else -> msg
        }
    }

    private fun tokenToFriendly(tokenType: String): String = when (tokenType) {
        "COLON" -> "a colon (:)"
        "COMMA" -> "a comma (,)"
        "INDENT" -> "an indented block"
        "IDENTIFIER" -> "a name"
        "STRING_LITERAL" -> "a quoted string"
        "NUMBER_LITERAL" -> "a number"
        "IS" -> "the word \"is\""
        "AS" -> "the word \"as\""
        "TO" -> "the word \"to\""
        "WITH" -> "the word \"with\""
        "WHERE" -> "the word \"where\""
        "EQUALS" -> "an equals sign (=)"
        "DOT" -> "a dot (.)"
        "FOUND" -> "the word \"found\""
        "THAN" -> "the word \"than\""
        "OF" -> "the word \"of\""
        "FROM" -> "the word \"from\""
        "ALERT" -> "the word \"alert\""
        "SET" -> "the word \"set\""
        else -> "\"${tokenType.lowercase()}\""
    }
}
