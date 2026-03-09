package dev.plaing.compiler.parser

class Parser(private val tokens: List<Token>, private val fileName: String = "") {
    private var pos = 0

    fun parse(): PlaingProgram {
        val declarations = mutableListOf<Declaration>()
        skipNewlines()
        while (!isAtEnd()) {
            declarations.add(parseDeclaration())
            skipNewlines()
        }
        return PlaingProgram(declarations)
    }

    // --- Utility ---

    private fun current(): Token = if (pos < tokens.size) tokens[pos] else tokens.last()
    private fun peek(): Token = current()
    private fun isAtEnd(): Boolean = current().type == TokenType.EOF

    private fun advance(): Token {
        val token = current()
        if (!isAtEnd()) pos++
        return token
    }

    private fun expect(type: TokenType): Token {
        val token = current()
        if (token.type != type) {
            throw ParseException("Expected ${type.name} but found ${token.type.name} ('${token.value}')", token.line, token.column, fileName)
        }
        return advance()
    }

    private fun check(type: TokenType): Boolean = current().type == type
    private fun check(vararg types: TokenType): Boolean = current().type in types

    private fun match(type: TokenType): Boolean {
        if (check(type)) { advance(); return true }
        return false
    }

    private fun skipNewlines() {
        while (check(TokenType.NEWLINE)) advance()
    }

    private fun loc(): SourceLocation = SourceLocation(current().line, current().column, fileName)

    // --- Top-level ---

    private fun parseDeclaration(): Declaration {
        return when (current().type) {
            TokenType.ENTITY -> parseEntity()
            TokenType.EVENT -> parseEvent()
            TokenType.HANDLE -> parseHandler()
            TokenType.PAGE -> parsePage()
            TokenType.ON -> parseReaction()
            TokenType.STYLE -> parseStyle()
            else -> throw ParseException(
                "Expected a declaration (entity, event, handle, page, on, style) but found '${current().value}'",
                current().line, current().column, fileName
            )
        }
    }

    // --- Entity ---

    private fun parseEntity(): EntityDeclaration {
        val location = loc()
        expect(TokenType.ENTITY)
        val name = expect(TokenType.IDENTIFIER).value
        expect(TokenType.COLON)
        skipNewlines()
        expect(TokenType.INDENT)

        val fields = mutableListOf<FieldDefinition>()
        while (!check(TokenType.DEDENT) && !isAtEnd()) {
            fields.add(parseFieldDefinition())
            skipNewlines()
        }
        if (check(TokenType.DEDENT)) advance()

        return EntityDeclaration(name, fields, location)
    }

    private fun parseFieldDefinition(): FieldDefinition {
        val location = loc()
        val name = expect(TokenType.IDENTIFIER).value
        expect(TokenType.IS)
        val type = parseType()

        val modifiers = mutableListOf<FieldModifier>()
        while (match(TokenType.COMMA)) {
            modifiers.add(parseFieldModifier())
        }

        return FieldDefinition(name, type, modifiers, location)
    }

    private fun parseFieldModifier(): FieldModifier {
        return when {
            match(TokenType.REQUIRED) -> FieldModifier.Required
            match(TokenType.UNIQUE) -> FieldModifier.Unique
            match(TokenType.HIDDEN) -> FieldModifier.Hidden
            match(TokenType.DEFAULT) -> FieldModifier.Default(parseExpression())
            else -> throw ParseException(
                "Expected field modifier (required, unique, hidden, default) but found '${current().value}'",
                current().line, current().column, fileName
            )
        }
    }

    private fun parseType(): PlaingType {
        return when {
            match(TokenType.TEXT_TYPE) -> PlaingType.TextType
            match(TokenType.NUMBER_TYPE) -> PlaingType.NumberType
            match(TokenType.BOOLEAN_TYPE) -> PlaingType.BooleanType
            match(TokenType.DATE_TYPE) -> PlaingType.DateType
            match(TokenType.LIST) -> {
                expect(TokenType.OF)
                val elementType = parseType()
                PlaingType.ListType(elementType)
            }
            match(TokenType.OPTIONAL) -> {
                val innerType = parseType()
                PlaingType.OptionalType(innerType)
            }
            check(TokenType.IDENTIFIER) -> {
                PlaingType.EntityRef(advance().value)
            }
            else -> throw ParseException(
                "Expected type but found '${current().value}'",
                current().line, current().column, fileName
            )
        }
    }

    // --- Event ---

    private fun parseEvent(): EventDeclaration {
        val location = loc()
        expect(TokenType.EVENT)
        val name = expect(TokenType.IDENTIFIER).value
        expect(TokenType.COLON)
        skipNewlines()
        expect(TokenType.INDENT)

        val fields = mutableListOf<EventField>()
        // "carries email as Text, password as Text"
        if (match(TokenType.CARRIES)) {
            fields.add(parseEventField())
            while (match(TokenType.COMMA)) {
                fields.add(parseEventField())
            }
        }
        skipNewlines()
        if (check(TokenType.DEDENT)) advance()

        return EventDeclaration(name, fields, location)
    }

    private fun parseEventField(): EventField {
        val location = loc()
        val name = expect(TokenType.IDENTIFIER).value
        expect(TokenType.AS)
        val type = parseType()
        return EventField(name, type, location)
    }

    // --- Handler ---

    private fun parseHandler(): HandlerDeclaration {
        val location = loc()
        expect(TokenType.HANDLE)
        val eventName = expect(TokenType.IDENTIFIER).value
        expect(TokenType.COLON)
        skipNewlines()
        expect(TokenType.INDENT)

        val body = parseStatementBlock()

        return HandlerDeclaration(eventName, body, location)
    }

    private fun parseStatementBlock(): List<Statement> {
        val statements = mutableListOf<Statement>()
        while (!check(TokenType.DEDENT) && !isAtEnd()) {
            statements.add(parseStatement())
            skipNewlines()
        }
        if (check(TokenType.DEDENT)) advance()
        return statements
    }

    private fun parseStatement(): Statement {
        return when (current().type) {
            TokenType.FIND -> parseFindStatement()
            TokenType.CREATE -> parseCreateStatement()
            TokenType.UPDATE -> parseUpdateStatement()
            TokenType.DELETE -> parseDeleteStatement()
            TokenType.EMIT -> parseEmitStatement()
            TokenType.IF -> parseIfStatement()
            TokenType.STOP -> { val l = loc(); advance(); StopStatement(l) }
            else -> throw ParseException(
                "Expected statement but found '${current().value}'",
                current().line, current().column, fileName
            )
        }
    }

    private fun parseFindStatement(): FindStatement {
        val location = loc()
        expect(TokenType.FIND)
        val all = match(TokenType.ALL)
        val entityName = expect(TokenType.IDENTIFIER).value

        val conditions = mutableListOf<WhereCondition>()
        if (match(TokenType.WHERE)) {
            conditions.add(parseWhereCondition())
            while (match(TokenType.AND)) {
                conditions.add(parseWhereCondition())
            }
        }

        return FindStatement(entityName, all, conditions, location)
    }

    private fun parseWhereCondition(): WhereCondition {
        val location = loc()
        val field = expect(TokenType.IDENTIFIER).value
        val operator = parseComparisonOperator()
        val value = parseExpression()
        return WhereCondition(field, operator, value, location)
    }

    private fun parseComparisonOperator(): ComparisonOperator {
        return when {
            check(TokenType.IS) -> {
                advance()
                when {
                    match(TokenType.NOT) -> ComparisonOperator.IS_NOT
                    match(TokenType.AFTER) -> ComparisonOperator.IS_AFTER
                    match(TokenType.BEFORE) -> ComparisonOperator.IS_BEFORE
                    match(TokenType.GREATER) -> { expect(TokenType.THAN); ComparisonOperator.IS_GREATER_THAN }
                    match(TokenType.LESS) -> { expect(TokenType.THAN); ComparisonOperator.IS_LESS_THAN }
                    else -> ComparisonOperator.IS
                }
            }
            match(TokenType.MATCHES) -> ComparisonOperator.MATCHES
            match(TokenType.CONTAINS) -> ComparisonOperator.CONTAINS
            check(TokenType.STARTS) -> {
                advance()
                expect(TokenType.WITH)
                ComparisonOperator.STARTS_WITH
            }
            else -> throw ParseException(
                "Expected comparison operator but found '${current().value}'",
                current().line, current().column, fileName
            )
        }
    }

    private fun parseCreateStatement(): CreateStatement {
        val location = loc()
        expect(TokenType.CREATE)
        val entityName = expect(TokenType.IDENTIFIER).value

        var forEntity: String? = null
        if (match(TokenType.FOR)) {
            forEntity = expect(TokenType.IDENTIFIER).value
        }

        val assignments = mutableListOf<Assignment>()
        if (match(TokenType.WITH)) {
            assignments.add(parseAssignment())
            while (match(TokenType.COMMA)) {
                assignments.add(parseAssignment())
            }
        }

        return CreateStatement(entityName, forEntity, assignments, location)
    }

    private fun parseUpdateStatement(): UpdateStatement {
        val location = loc()
        expect(TokenType.UPDATE)
        val entityName = expect(TokenType.IDENTIFIER).value
        expect(TokenType.SET)

        val assignments = mutableListOf<Assignment>()
        assignments.add(parseAssignment())
        while (match(TokenType.COMMA)) {
            assignments.add(parseAssignment())
        }

        val conditions = mutableListOf<WhereCondition>()
        if (match(TokenType.WHERE)) {
            conditions.add(parseWhereCondition())
            while (match(TokenType.AND)) {
                conditions.add(parseWhereCondition())
            }
        }

        return UpdateStatement(entityName, assignments, conditions, location)
    }

    private fun parseDeleteStatement(): DeleteStatement {
        val location = loc()
        expect(TokenType.DELETE)
        val entityName = expect(TokenType.IDENTIFIER).value

        val conditions = mutableListOf<WhereCondition>()
        if (match(TokenType.WHERE)) {
            conditions.add(parseWhereCondition())
            while (match(TokenType.AND)) {
                conditions.add(parseWhereCondition())
            }
        }

        return DeleteStatement(entityName, conditions, location)
    }

    private fun parseEmitStatement(): EmitStatement {
        val location = loc()
        expect(TokenType.EMIT)
        val eventName = expect(TokenType.IDENTIFIER).value

        val arguments = mutableListOf<Assignment>()
        if (match(TokenType.WITH)) {
            arguments.add(parseAssignment())
            while (match(TokenType.COMMA)) {
                arguments.add(parseAssignment())
            }
        }

        return EmitStatement(eventName, arguments, location)
    }

    private fun parseIfStatement(): IfStatement {
        val location = loc()
        expect(TokenType.IF)
        val condition = parseCondition()
        expect(TokenType.COLON)
        skipNewlines()
        expect(TokenType.INDENT)
        val body = parseStatementBlock()

        var otherwise: List<Statement>? = null
        skipNewlines()
        if (match(TokenType.OTHERWISE)) {
            expect(TokenType.COLON)
            skipNewlines()
            expect(TokenType.INDENT)
            otherwise = parseStatementBlock()
        }

        return IfStatement(condition, body, otherwise, location)
    }

    private fun parseCondition(): Condition {
        val location = loc()
        // "no User found"
        if (check(TokenType.NO)) {
            advance()
            val entityName = expect(TokenType.IDENTIFIER).value
            expect(TokenType.FOUND)
            return NoResultCondition(entityName, location)
        }
        // expression operator expression
        val left = parseExpression()
        val operator = parseComparisonOperator()
        val right = parseExpression()
        return ComparisonCondition(left, operator, right, location)
    }

    private fun parseAssignment(): Assignment {
        val location = loc()
        val name = expect(TokenType.IDENTIFIER).value
        expect(TokenType.EQUALS)
        val value = parseExpression()
        return Assignment(name, value, location)
    }

    // --- Expression ---

    private fun parseExpression(): Expression {
        var expr = parsePrimaryExpression()
        // Handle dot access: User.email, LOGIN_ATTEMPT.password
        while (match(TokenType.DOT)) {
            val field = expect(TokenType.IDENTIFIER).value
            expr = DotAccess(expr, field, expr.location)
        }
        return expr
    }

    private fun parsePrimaryExpression(): Expression {
        val location = loc()
        return when {
            check(TokenType.STRING_LITERAL) -> StringLiteral(advance().value, location)
            check(TokenType.NUMBER_LITERAL) -> NumberLiteral(advance().value.toDouble(), location)
            check(TokenType.TRUE) -> { advance(); BooleanLiteral(true, location) }
            check(TokenType.FALSE) -> { advance(); BooleanLiteral(false, location) }
            check(TokenType.NOW) -> { advance(); NowLiteral(location) }
            check(TokenType.IDENTIFIER) -> Identifier(advance().value, location)
            else -> throw ParseException(
                "Expected expression but found '${current().value}'",
                current().line, current().column, fileName
            )
        }
    }

    // --- Page ---

    private fun parsePage(): PageDeclaration {
        val location = loc()
        expect(TokenType.PAGE)
        val name = expect(TokenType.IDENTIFIER).value
        expect(TokenType.COLON)
        skipNewlines()
        expect(TokenType.INDENT)

        val body = parseUiElementBlock()

        return PageDeclaration(name, body, location)
    }

    private fun parseUiElementBlock(): List<UiElement> {
        val elements = mutableListOf<UiElement>()
        while (!check(TokenType.DEDENT) && !isAtEnd()) {
            elements.add(parseUiElement())
            skipNewlines()
        }
        if (check(TokenType.DEDENT)) advance()
        return elements
    }

    private fun parseUiElement(): UiElement {
        return when {
            check(TokenType.LAYOUT) -> parseLayoutElement()
            check(TokenType.HEADING) -> parseHeadingElement()
            check(TokenType.FORM) -> parseFormElement()
            check(TokenType.INPUT) -> parseInputElement()
            check(TokenType.BUTTON) -> parseButtonElement()
            check(TokenType.IDENTIFIER) && current().value == "list" -> parseListElement()
            check(TokenType.IDENTIFIER) && current().value == "text" -> parseTextElement()
            else -> throw ParseException(
                "Expected UI element but found '${current().value}'",
                current().line, current().column, fileName
            )
        }
    }

    private fun parseLayoutElement(): LayoutElement {
        val location = loc()
        expect(TokenType.LAYOUT)
        val name = expect(TokenType.IDENTIFIER).value
        expect(TokenType.COLON)
        skipNewlines()
        expect(TokenType.INDENT)
        val children = parseUiElementBlock()
        return LayoutElement(name, children, location)
    }

    private fun parseHeadingElement(): HeadingElement {
        val location = loc()
        expect(TokenType.HEADING)
        val text = expect(TokenType.STRING_LITERAL).value
        return HeadingElement(text, location)
    }

    private fun parseFormElement(): FormElement {
        val location = loc()
        expect(TokenType.FORM)
        val name = expect(TokenType.IDENTIFIER).value
        expect(TokenType.COLON)
        skipNewlines()
        expect(TokenType.INDENT)
        val children = parseUiElementBlock()
        return FormElement(name, children, location)
    }

    private fun parseInputElement(): InputElement {
        val location = loc()
        expect(TokenType.INPUT)
        val name = expect(TokenType.IDENTIFIER).value
        expect(TokenType.COLON)

        val properties = mutableListOf<InputProperty>()
        properties.add(parseInputProperty())
        while (match(TokenType.COMMA)) {
            properties.add(parseInputProperty())
        }

        return InputElement(name, properties, location)
    }

    private fun parseInputProperty(): InputProperty {
        return when {
            match(TokenType.PLACEHOLDER) -> InputProperty.Placeholder(expect(TokenType.STRING_LITERAL).value)
            match(TokenType.TYPE) -> InputProperty.Type(expect(TokenType.IDENTIFIER).value)
            match(TokenType.BINDS) -> { expect(TokenType.TO); InputProperty.BindsTo(expect(TokenType.IDENTIFIER).value) }
            match(TokenType.FILLS) -> {
                expect(TokenType.FROM)
                val entityName = expect(TokenType.IDENTIFIER).value
                expect(TokenType.DOT)
                val fieldName = expect(TokenType.IDENTIFIER).value
                InputProperty.FillsFrom(entityName, fieldName)
            }
            else -> throw ParseException(
                "Expected input property but found '${current().value}'",
                current().line, current().column, fileName
            )
        }
    }

    private fun parseButtonElement(): ButtonElement {
        val location = loc()
        expect(TokenType.BUTTON)
        val text = expect(TokenType.STRING_LITERAL).value

        var action: ButtonAction? = null
        if (match(TokenType.COLON)) {
            action = parseButtonAction()
        }

        return ButtonElement(text, action, location)
    }

    private fun parseButtonAction(): ButtonAction {
        val location = loc()
        expect(TokenType.EMITS)
        val eventName = expect(TokenType.IDENTIFIER).value

        val arguments = mutableListOf<String>()
        if (match(TokenType.WITH)) {
            arguments.add(expect(TokenType.IDENTIFIER).value)
            while (match(TokenType.COMMA)) {
                arguments.add(expect(TokenType.IDENTIFIER).value)
            }
        }

        return ButtonAction(eventName, arguments, location)
    }

    // --- Text element ---
    // text "static string" OR text Entity.field
    private fun parseTextElement(): TextElement {
        val location = loc()
        advance() // consume "text" identifier
        val value = parseExpression()
        return TextElement(value, location)
    }

    // --- List element ---
    // list notes: each Note show Note.title, Note.body
    // list notes: each Note show Note.title, Note.body, on click select Note
    private fun parseListElement(): ListElement {
        val location = loc()
        advance() // consume "list" identifier
        val name = expect(TokenType.IDENTIFIER).value
        expect(TokenType.COLON)
        expect(TokenType.EACH)
        val entityName = expect(TokenType.IDENTIFIER).value
        expect(TokenType.SHOW)

        val fields = mutableListOf<String>()
        // Parse Entity.field references
        expect(TokenType.IDENTIFIER) // skip entity name prefix
        expect(TokenType.DOT)
        fields.add(expect(TokenType.IDENTIFIER).value)

        var onClickSelect: String? = null
        while (match(TokenType.COMMA)) {
            // Check for "on click select Entity"
            if (check(TokenType.ON)) {
                expect(TokenType.ON)
                expect(TokenType.CLICK)
                expect(TokenType.SELECT)
                onClickSelect = expect(TokenType.IDENTIFIER).value
                break
            }
            expect(TokenType.IDENTIFIER) // skip entity name prefix
            expect(TokenType.DOT)
            fields.add(expect(TokenType.IDENTIFIER).value)
        }

        return ListElement(name, entityName, fields, onClickSelect, location)
    }

    // --- Reaction ---

    private fun parseReaction(): ReactionDeclaration {
        val location = loc()
        expect(TokenType.ON)
        val eventName = expect(TokenType.IDENTIFIER).value
        expect(TokenType.COLON)
        skipNewlines()
        expect(TokenType.INDENT)

        val actions = mutableListOf<ReactionAction>()
        while (!check(TokenType.DEDENT) && !isAtEnd()) {
            actions.add(parseReactionAction())
            skipNewlines()
        }
        if (check(TokenType.DEDENT)) advance()

        return ReactionDeclaration(eventName, actions, location)
    }

    private fun parseReactionAction(): ReactionAction {
        val location = loc()
        return when {
            match(TokenType.STORE) -> {
                if (match(TokenType.ALL)) {
                    val entityName = expect(TokenType.IDENTIFIER).value
                    expect(TokenType.FROM)
                    val from = parseExpression()
                    StoreAllAction(entityName, from, location)
                } else {
                    val entityName = expect(TokenType.IDENTIFIER).value
                    expect(TokenType.FROM)
                    val from = parseExpression()
                    StoreAction(entityName, from, location)
                }
            }
            match(TokenType.NAVIGATE) -> {
                expect(TokenType.TO)
                val target = expect(TokenType.IDENTIFIER).value
                NavigateAction(target, location)
            }
            match(TokenType.SHOW) -> {
                expect(TokenType.ALERT)
                val message = parseExpression()
                expect(TokenType.ON)
                val targetPage = expect(TokenType.IDENTIFIER).value
                ShowAlertAction(message, targetPage, location)
            }
            else -> throw ParseException(
                "Expected reaction action but found '${current().value}'",
                current().line, current().column, fileName
            )
        }
    }

    // --- Style ---

    private fun parseStyle(): StyleDeclaration {
        val location = loc()
        expect(TokenType.STYLE)
        val name = expect(TokenType.IDENTIFIER).value
        expect(TokenType.COLON)
        skipNewlines()
        expect(TokenType.INDENT)

        val properties = mutableListOf<StyleProperty>()
        val pseudoStates = mutableListOf<PseudoStateBlock>()

        while (!check(TokenType.DEDENT) && !isAtEnd()) {
            if (check(TokenType.ON)) {
                pseudoStates.add(parsePseudoStateBlock())
            } else {
                properties.add(parseStyleProperty())
            }
            skipNewlines()
        }
        if (check(TokenType.DEDENT)) advance()

        return StyleDeclaration(name, properties, pseudoStates, location)
    }

    private fun parseStyleProperty(): StyleProperty {
        val location = loc()
        // Read property name (may be multi-word like "background color", "border radius")
        val nameParts = mutableListOf<String>()
        // Consume identifier-like tokens until we hit IS
        while (!check(TokenType.IS) && !check(TokenType.NEWLINE) && !check(TokenType.DEDENT) && !isAtEnd()) {
            nameParts.add(advance().value)
        }
        expect(TokenType.IS)

        // Read value (everything until newline or dedent)
        // Reconstruct value preserving adjacency (e.g. "8px" = number "8" + identifier "px")
        val valueBuilder = StringBuilder()
        var lastEndColumn = -1
        while (!check(TokenType.NEWLINE) && !check(TokenType.DEDENT) && !isAtEnd()) {
            val token = advance()
            if (valueBuilder.isNotEmpty()) {
                // If this token starts right after the previous one ended, no space
                if (lastEndColumn >= 0 && token.column == lastEndColumn) {
                    // adjacent - no space
                } else {
                    valueBuilder.append(' ')
                }
            }
            valueBuilder.append(token.value)
            lastEndColumn = token.column + token.value.length
        }

        return StyleProperty(
            name = nameParts.joinToString(" "),
            value = valueBuilder.toString(),
            location = location
        )
    }

    private fun parsePseudoStateBlock(): PseudoStateBlock {
        val location = loc()
        expect(TokenType.ON)
        val state = expect(TokenType.IDENTIFIER).value
        expect(TokenType.COLON)
        skipNewlines()
        expect(TokenType.INDENT)

        val properties = mutableListOf<StyleProperty>()
        while (!check(TokenType.DEDENT) && !isAtEnd()) {
            properties.add(parseStyleProperty())
            skipNewlines()
        }
        if (check(TokenType.DEDENT)) advance()

        return PseudoStateBlock(state, properties, location)
    }
}

class ParseException(
    message: String,
    val line: Int,
    val column: Int,
    val file: String
) : RuntimeException("$file:$line:$column: $message")
