package dev.plaing.compiler.parser

data class Token(
    val type: TokenType,
    val value: String,
    val line: Int,
    val column: Int
)

enum class TokenType {
    // Structure
    INDENT, DEDENT, NEWLINE, EOF,

    // Keywords - declarations
    ENTITY, EVENT, HANDLE, PAGE, ON, STYLE,

    // Keywords - event fields
    CARRIES, AS,

    // Keywords - operators & connectors
    IS, WITH, WHERE, AND, OR, NOT,
    AFTER, BEFORE, CONTAINS, STARTS, GREATER, THAN, LESS, MATCHES,

    // Keywords - data operations
    FIND, ALL, CREATE, UPDATE, SET, DELETE, FOR,

    // Keywords - control flow
    IF, NO, FOUND, OTHERWISE, STOP,

    // Keywords - events
    EMIT,

    // Keywords - UI
    LAYOUT, HEADING, FORM, INPUT, BUTTON, EMITS, BINDS, TO,
    PLACEHOLDER, TYPE, EACH, CLICK, SELECT, SELECTED, FILLS,

    // Keywords - field modifiers
    DEFAULT, REQUIRED, UNIQUE, HIDDEN,

    // Keywords - reactions
    NAVIGATE, STORE, FROM, SHOW, ALERT,

    // Keywords - types
    TEXT_TYPE, NUMBER_TYPE, BOOLEAN_TYPE, DATE_TYPE, LIST, OF, OPTIONAL,

    // Literals
    STRING_LITERAL, NUMBER_LITERAL, TRUE, FALSE, NOW,

    // Identifiers
    IDENTIFIER,

    // Punctuation
    COLON, COMMA, DOT, EQUALS,
}

class Lexer(private val source: String, private val fileName: String = "") {
    private var pos = 0
    private var line = 1
    private var column = 1
    private val tokens = mutableListOf<Token>()
    private val indentStack = mutableListOf(0) // stack of indentation levels

    private val keywords = mapOf(
        "entity" to TokenType.ENTITY,
        "event" to TokenType.EVENT,
        "handle" to TokenType.HANDLE,
        "page" to TokenType.PAGE,
        "on" to TokenType.ON,
        "style" to TokenType.STYLE,
        "carries" to TokenType.CARRIES,
        "as" to TokenType.AS,
        "is" to TokenType.IS,
        "with" to TokenType.WITH,
        "where" to TokenType.WHERE,
        "and" to TokenType.AND,
        "or" to TokenType.OR,
        "not" to TokenType.NOT,
        "after" to TokenType.AFTER,
        "before" to TokenType.BEFORE,
        "contains" to TokenType.CONTAINS,
        "starts" to TokenType.STARTS,
        "greater" to TokenType.GREATER,
        "than" to TokenType.THAN,
        "less" to TokenType.LESS,
        "matches" to TokenType.MATCHES,
        "find" to TokenType.FIND,
        "all" to TokenType.ALL,
        "create" to TokenType.CREATE,
        "update" to TokenType.UPDATE,
        "set" to TokenType.SET,
        "delete" to TokenType.DELETE,
        "for" to TokenType.FOR,
        "if" to TokenType.IF,
        "no" to TokenType.NO,
        "found" to TokenType.FOUND,
        "otherwise" to TokenType.OTHERWISE,
        "stop" to TokenType.STOP,
        "emit" to TokenType.EMIT,
        "layout" to TokenType.LAYOUT,
        "heading" to TokenType.HEADING,
        "form" to TokenType.FORM,
        "input" to TokenType.INPUT,
        "button" to TokenType.BUTTON,
        "emits" to TokenType.EMITS,
        "binds" to TokenType.BINDS,
        "to" to TokenType.TO,
        "placeholder" to TokenType.PLACEHOLDER,
        "type" to TokenType.TYPE,
        "each" to TokenType.EACH,
        "click" to TokenType.CLICK,
        "select" to TokenType.SELECT,
        "selected" to TokenType.SELECTED,
        "fills" to TokenType.FILLS,
        "default" to TokenType.DEFAULT,
        "required" to TokenType.REQUIRED,
        "unique" to TokenType.UNIQUE,
        "hidden" to TokenType.HIDDEN,
        "navigate" to TokenType.NAVIGATE,
        "store" to TokenType.STORE,
        "from" to TokenType.FROM,
        "show" to TokenType.SHOW,
        "alert" to TokenType.ALERT,
        "Text" to TokenType.TEXT_TYPE,
        "Number" to TokenType.NUMBER_TYPE,
        "Boolean" to TokenType.BOOLEAN_TYPE,
        "Date" to TokenType.DATE_TYPE,
        "List" to TokenType.LIST,
        "of" to TokenType.OF,
        "Optional" to TokenType.OPTIONAL,
        "true" to TokenType.TRUE,
        "false" to TokenType.FALSE,
        "now" to TokenType.NOW,
    )

    fun tokenize(): List<Token> {
        while (pos < source.length) {
            // At start of a new line, handle indentation
            if (column == 1) {
                processIndentation()
            }

            if (pos >= source.length) break

            val ch = source[pos]
            when {
                ch == '#' -> skipComment()
                ch == '\n' -> {
                    tokens.add(Token(TokenType.NEWLINE, "\\n", line, column))
                    advance()
                    line++
                    column = 1
                }
                ch == '"' -> readString()
                ch == ':' -> { tokens.add(Token(TokenType.COLON, ":", line, column)); advance() }
                ch == ',' -> { tokens.add(Token(TokenType.COMMA, ",", line, column)); advance() }
                ch == '.' -> { tokens.add(Token(TokenType.DOT, ".", line, column)); advance() }
                ch == '=' -> { tokens.add(Token(TokenType.EQUALS, "=", line, column)); advance() }
                ch.isDigit() || (ch == '-' && pos + 1 < source.length && source[pos + 1].isDigit()) -> readNumber()
                ch.isLetter() || ch == '_' -> readIdentifierOrKeyword()
                ch == ' ' || ch == '\t' -> advance() // skip whitespace mid-line
                ch == '\r' -> advance() // skip carriage return
                else -> throw LexerException("Unexpected character '$ch'", line, column, fileName)
            }
        }

        // Emit remaining DEDENT tokens at end of file
        while (indentStack.size > 1) {
            indentStack.removeLast()
            tokens.add(Token(TokenType.DEDENT, "", line, column))
        }
        tokens.add(Token(TokenType.EOF, "", line, column))

        return cleanTokens(tokens)
    }

    private fun processIndentation() {
        var indent = 0
        while (pos < source.length && (source[pos] == ' ' || source[pos] == '\t')) {
            if (source[pos] == '\t') indent += 2 // treat tabs as 2 spaces
            else indent++
            advance()
        }

        // Skip blank lines and comment-only lines
        if (pos >= source.length || source[pos] == '\n' || source[pos] == '#') {
            return
        }

        val currentIndent = indentStack.last()
        when {
            indent > currentIndent -> {
                indentStack.add(indent)
                tokens.add(Token(TokenType.INDENT, "", line, indent + 1))
            }
            indent < currentIndent -> {
                while (indentStack.size > 1 && indentStack.last() > indent) {
                    indentStack.removeLast()
                    tokens.add(Token(TokenType.DEDENT, "", line, indent + 1))
                }
                if (indentStack.last() != indent) {
                    throw LexerException("Inconsistent indentation", line, 1, fileName)
                }
            }
        }
    }

    private fun skipComment() {
        while (pos < source.length && source[pos] != '\n') {
            advance()
        }
    }

    private fun readString() {
        val startLine = line
        val startCol = column
        advance() // skip opening "
        val sb = StringBuilder()
        while (pos < source.length && source[pos] != '"') {
            if (source[pos] == '\\' && pos + 1 < source.length) {
                advance()
                when (source[pos]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    else -> sb.append(source[pos])
                }
            } else {
                sb.append(source[pos])
            }
            advance()
        }
        if (pos >= source.length) {
            throw LexerException("Unterminated string literal", startLine, startCol, fileName)
        }
        advance() // skip closing "
        tokens.add(Token(TokenType.STRING_LITERAL, sb.toString(), startLine, startCol))
    }

    private fun readNumber() {
        val startCol = column
        val sb = StringBuilder()
        if (source[pos] == '-') {
            sb.append('-')
            advance()
        }
        while (pos < source.length && (source[pos].isDigit() || source[pos] == '.')) {
            sb.append(source[pos])
            advance()
        }
        tokens.add(Token(TokenType.NUMBER_LITERAL, sb.toString(), line, startCol))
    }

    private fun readIdentifierOrKeyword() {
        val startCol = column
        val sb = StringBuilder()
        while (pos < source.length && (source[pos].isLetterOrDigit() || source[pos] == '_' || source[pos] == '-')) {
            sb.append(source[pos])
            advance()
        }
        val word = sb.toString()
        val tokenType = keywords[word] ?: TokenType.IDENTIFIER
        tokens.add(Token(tokenType, word, line, startCol))
    }

    private fun advance() {
        pos++
        column++
    }

    /**
     * Clean up token stream: remove redundant newlines, etc.
     */
    private fun cleanTokens(raw: List<Token>): List<Token> {
        val result = mutableListOf<Token>()
        for (token in raw) {
            // Skip consecutive newlines
            if (token.type == TokenType.NEWLINE && result.lastOrNull()?.type == TokenType.NEWLINE) {
                continue
            }
            // Skip newlines right after INDENT/DEDENT
            if (token.type == TokenType.NEWLINE && result.lastOrNull()?.type in listOf(TokenType.INDENT, TokenType.DEDENT)) {
                continue
            }
            result.add(token)
        }
        // Remove leading newline if present
        if (result.firstOrNull()?.type == TokenType.NEWLINE) {
            result.removeFirst()
        }
        return result
    }
}

class LexerException(
    message: String,
    val line: Int,
    val column: Int,
    val file: String
) : RuntimeException("$file:$line:$column: $message")
