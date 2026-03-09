package dev.plaing.compiler.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

class LexerTest {
    private fun tokenize(source: String): List<Token> = Lexer(source, "test.plaing").tokenize()
    private fun tokenTypes(source: String): List<TokenType> = tokenize(source).map { it.type }

    @Test
    fun `tokenizes simple entity declaration`() {
        val tokens = tokenize("entity User:\n  name is Text")
        val types = tokens.map { it.type }
        assertTrue(TokenType.ENTITY in types)
        assertTrue(TokenType.IDENTIFIER in types)
        assertTrue(TokenType.COLON in types)
        assertTrue(TokenType.INDENT in types)
        assertTrue(TokenType.IS in types)
        assertTrue(TokenType.TEXT_TYPE in types)
    }

    @Test
    fun `handles indentation and dedentation`() {
        val tokens = tokenize("entity User:\n  name is Text\n  email is Text\n")
        val types = tokens.map { it.type }
        assertEquals(1, types.count { it == TokenType.INDENT })
        assertEquals(1, types.count { it == TokenType.DEDENT })
    }

    @Test
    fun `tokenizes string literals`() {
        val tokens = tokenize("heading \"Hello World\"")
        val stringToken = tokens.find { it.type == TokenType.STRING_LITERAL }
        assertEquals("Hello World", stringToken?.value)
    }

    @Test
    fun `tokenizes number literals`() {
        val tokens = tokenize("42")
        val numberToken = tokens.find { it.type == TokenType.NUMBER_LITERAL }
        assertEquals("42", numberToken?.value)
    }

    @Test
    fun `tokenizes SCREAMING_SNAKE_CASE identifiers`() {
        val tokens = tokenize("event LOGIN_ATTEMPT:")
        val ident = tokens.find { it.type == TokenType.IDENTIFIER }
        assertEquals("LOGIN_ATTEMPT", ident?.value)
    }

    @Test
    fun `skips comments`() {
        val tokens = tokenize("# this is a comment\nentity User:")
        val types = tokens.map { it.type }
        assertTrue(TokenType.ENTITY in types)
        // comment content should not appear
        assertTrue(tokens.none { it.value == "this" || it.value == "comment" })
    }

    @Test
    fun `skips blank lines`() {
        val tokens = tokenize("entity User:\n\n  name is Text")
        val types = tokens.map { it.type }
        assertTrue(TokenType.ENTITY in types)
        assertTrue(TokenType.INDENT in types)
    }

    @Test
    fun `handles escape sequences in strings`() {
        val tokens = tokenize("\"hello\\nworld\"")
        val str = tokens.find { it.type == TokenType.STRING_LITERAL }
        assertEquals("hello\nworld", str?.value)
    }

    @Test
    fun `throws on unterminated string`() {
        assertFailsWith<LexerException> {
            tokenize("\"unterminated")
        }
    }

    @Test
    fun `nested indentation produces multiple indent tokens`() {
        val source = "page LoginPage:\n  layout main:\n    heading \"Hi\"\n"
        val tokens = tokenize(source)
        val types = tokens.map { it.type }
        assertEquals(2, types.count { it == TokenType.INDENT })
        assertEquals(2, types.count { it == TokenType.DEDENT })
    }

    @Test
    fun `tokenizes hyphenated identifiers`() {
        val tokens = tokenize("login-form")
        val ident = tokens.find { it.type == TokenType.IDENTIFIER }
        assertEquals("login-form", ident?.value)
    }

    @Test
    fun `tokenizes dot as separate token`() {
        val tokens = tokenize("User.email")
        val types = tokens.map { it.type }
        assertTrue(TokenType.IDENTIFIER in types)
        assertTrue(TokenType.DOT in types)
    }

    @Test
    fun `tokenizes keywords correctly`() {
        val tokens = tokenize("find all create update set delete emit if otherwise stop")
        val types = tokens.map { it.type }
        assertTrue(TokenType.FIND in types)
        assertTrue(TokenType.ALL in types)
        assertTrue(TokenType.CREATE in types)
        assertTrue(TokenType.UPDATE in types)
        assertTrue(TokenType.SET in types)
        assertTrue(TokenType.DELETE in types)
        assertTrue(TokenType.EMIT in types)
        assertTrue(TokenType.IF in types)
        assertTrue(TokenType.OTHERWISE in types)
        assertTrue(TokenType.STOP in types)
    }

    @Test
    fun `tokenizes type keywords`() {
        val types = tokenTypes("Text Number Boolean Date List Optional")
        assertTrue(TokenType.TEXT_TYPE in types)
        assertTrue(TokenType.NUMBER_TYPE in types)
        assertTrue(TokenType.BOOLEAN_TYPE in types)
        assertTrue(TokenType.DATE_TYPE in types)
        assertTrue(TokenType.LIST in types)
        assertTrue(TokenType.OPTIONAL in types)
    }

    @Test
    fun `tokenizes boolean and now literals`() {
        val types = tokenTypes("true false now")
        assertTrue(TokenType.TRUE in types)
        assertTrue(TokenType.FALSE in types)
        assertTrue(TokenType.NOW in types)
    }

    @Test
    fun `tokenizes negative numbers`() {
        val tokens = tokenize("-5")
        val num = tokens.find { it.type == TokenType.NUMBER_LITERAL }
        assertEquals("-5", num?.value)
    }

    @Test
    fun `tokenizes decimal numbers`() {
        val tokens = tokenize("3.14")
        val num = tokens.find { it.type == TokenType.NUMBER_LITERAL }
        assertEquals("3.14", num?.value)
    }

    @Test
    fun `ends token stream with EOF`() {
        val tokens = tokenize("entity User:")
        assertEquals(TokenType.EOF, tokens.last().type)
    }

    @Test
    fun `throws on unexpected character`() {
        assertFailsWith<LexerException> {
            tokenize("@invalid")
        }
    }
}

class ParserTest {
    private fun parse(source: String): PlaingProgram {
        val tokens = Lexer(source, "test.plaing").tokenize()
        return Parser(tokens, "test.plaing").parse()
    }

    // --- Entity Tests ---

    @Test
    fun `parses simple entity`() {
        val program = parse("""
entity User:
  name is Text, required
  email is Text, required, unique
""".trimIndent())

        assertEquals(1, program.declarations.size)
        val entity = assertIs<EntityDeclaration>(program.declarations[0])
        assertEquals("User", entity.name)
        assertEquals(2, entity.fields.size)

        val nameField = entity.fields[0]
        assertEquals("name", nameField.name)
        assertEquals(PlaingType.TextType, nameField.type)
        assertEquals(listOf(FieldModifier.Required), nameField.modifiers)

        val emailField = entity.fields[1]
        assertEquals("email", emailField.name)
        assertEquals(PlaingType.TextType, emailField.type)
        assertTrue(emailField.modifiers.contains(FieldModifier.Required))
        assertTrue(emailField.modifiers.contains(FieldModifier.Unique))
    }

    @Test
    fun `parses entity with all field types`() {
        val program = parse("""
entity Post:
  title is Text, required
  views is Number
  published is Boolean, default false
  created_at is Date, default now
  author is User
  tags is List of Text
  subtitle is Optional Text
""".trimIndent())

        val entity = assertIs<EntityDeclaration>(program.declarations[0])
        assertEquals(7, entity.fields.size)
        assertEquals(PlaingType.TextType, entity.fields[0].type)
        assertEquals(PlaingType.NumberType, entity.fields[1].type)
        assertEquals(PlaingType.BooleanType, entity.fields[2].type)
        assertEquals(PlaingType.DateType, entity.fields[3].type)
        assertEquals(PlaingType.EntityRef("User"), entity.fields[4].type)
        assertIs<PlaingType.ListType>(entity.fields[5].type)
        assertEquals(PlaingType.TextType, (entity.fields[5].type as PlaingType.ListType).elementType)
        assertIs<PlaingType.OptionalType>(entity.fields[6].type)
        assertEquals(PlaingType.TextType, (entity.fields[6].type as PlaingType.OptionalType).innerType)
    }

    @Test
    fun `parses entity with default values`() {
        val program = parse("""
entity User:
  role is Text, default "member"
  created_at is Date, default now
  active is Boolean, default true
""".trimIndent())

        val entity = assertIs<EntityDeclaration>(program.declarations[0])

        val roleDefault = entity.fields[0].modifiers.filterIsInstance<FieldModifier.Default>().first()
        assertIs<StringLiteral>(roleDefault.value)
        assertEquals("member", (roleDefault.value as StringLiteral).value)

        val dateDefault = entity.fields[1].modifiers.filterIsInstance<FieldModifier.Default>().first()
        assertIs<NowLiteral>(dateDefault.value)

        val activeDefault = entity.fields[2].modifiers.filterIsInstance<FieldModifier.Default>().first()
        assertIs<BooleanLiteral>(activeDefault.value)
        assertEquals(true, (activeDefault.value as BooleanLiteral).value)
    }

    @Test
    fun `parses entity with hidden modifier`() {
        val program = parse("""
entity User:
  password is Text, required, hidden
""".trimIndent())

        val entity = assertIs<EntityDeclaration>(program.declarations[0])
        assertTrue(entity.fields[0].modifiers.contains(FieldModifier.Hidden))
        assertTrue(entity.fields[0].modifiers.contains(FieldModifier.Required))
    }

    @Test
    fun `parses entity with multiple modifiers`() {
        val program = parse("""
entity Account:
  email is Text, required, unique, hidden
""".trimIndent())

        val entity = assertIs<EntityDeclaration>(program.declarations[0])
        val modifiers = entity.fields[0].modifiers
        assertEquals(3, modifiers.size)
        assertTrue(modifiers.contains(FieldModifier.Required))
        assertTrue(modifiers.contains(FieldModifier.Unique))
        assertTrue(modifiers.contains(FieldModifier.Hidden))
    }

    @Test
    fun `parses entity with no modifiers`() {
        val program = parse("""
entity Item:
  description is Text
""".trimIndent())

        val entity = assertIs<EntityDeclaration>(program.declarations[0])
        assertEquals(0, entity.fields[0].modifiers.size)
    }

    // --- Event Tests ---

    @Test
    fun `parses event with carries`() {
        val program = parse("""
event LOGIN_ATTEMPT:
  carries email as Text, password as Text
""".trimIndent())

        assertEquals(1, program.declarations.size)
        val event = assertIs<EventDeclaration>(program.declarations[0])
        assertEquals("LOGIN_ATTEMPT", event.name)
        assertEquals(2, event.fields.size)
        assertEquals("email", event.fields[0].name)
        assertEquals(PlaingType.TextType, event.fields[0].type)
        assertEquals("password", event.fields[1].name)
        assertEquals(PlaingType.TextType, event.fields[1].type)
    }

    @Test
    fun `parses event with entity ref type`() {
        val program = parse("""
event LOGIN_SUCCESS:
  carries user as User, token as Text
""".trimIndent())

        val event = assertIs<EventDeclaration>(program.declarations[0])
        assertEquals(PlaingType.EntityRef("User"), event.fields[0].type)
        assertEquals(PlaingType.TextType, event.fields[1].type)
    }

    @Test
    fun `parses event with single field`() {
        val program = parse("""
event LOGOUT:
  carries session_id as Text
""".trimIndent())

        val event = assertIs<EventDeclaration>(program.declarations[0])
        assertEquals(1, event.fields.size)
        assertEquals("session_id", event.fields[0].name)
    }

    // --- Handler Tests ---

    @Test
    fun `parses handler with find and if`() {
        val program = parse("""
handle LOGIN_ATTEMPT:
  find User where email is LOGIN_ATTEMPT.email
  if no User found:
    emit LOGIN_FAILURE with message = "invalid credentials"
    stop
""".trimIndent())

        val handler = assertIs<HandlerDeclaration>(program.declarations[0])
        assertEquals("LOGIN_ATTEMPT", handler.eventName)
        assertEquals(2, handler.body.size)

        val find = assertIs<FindStatement>(handler.body[0])
        assertEquals("User", find.entityName)
        assertEquals(false, find.all)
        assertEquals(1, find.conditions.size)
        assertEquals("email", find.conditions[0].field)
        assertEquals(ComparisonOperator.IS, find.conditions[0].operator)

        val ifStmt = assertIs<IfStatement>(handler.body[1])
        assertIs<NoResultCondition>(ifStmt.condition)
        assertEquals("User", (ifStmt.condition as NoResultCondition).entityName)
        assertEquals(2, ifStmt.body.size)
        assertIs<EmitStatement>(ifStmt.body[0])
        assertIs<StopStatement>(ifStmt.body[1])
    }

    @Test
    fun `parses handler with comparison condition`() {
        val program = parse("""
handle LOGIN_ATTEMPT:
  if User.password matches LOGIN_ATTEMPT.password:
    emit LOGIN_SUCCESS with user = User, token = Session.token
  otherwise:
    emit LOGIN_FAILURE with message = "wrong password"
""".trimIndent())

        val handler = assertIs<HandlerDeclaration>(program.declarations[0])
        val ifStmt = assertIs<IfStatement>(handler.body[0])

        val cond = assertIs<ComparisonCondition>(ifStmt.condition)
        assertEquals(ComparisonOperator.MATCHES, cond.operator)
        assertIs<DotAccess>(cond.left)
        assertIs<DotAccess>(cond.right)

        assertTrue(ifStmt.otherwise != null)
        assertEquals(1, ifStmt.otherwise!!.size)
        assertIs<EmitStatement>(ifStmt.otherwise!![0])
    }

    @Test
    fun `parses create statement`() {
        val program = parse("""
handle REGISTER:
  create User with name = "Alice", email = "alice@test.com"
""".trimIndent())

        val handler = assertIs<HandlerDeclaration>(program.declarations[0])
        val create = assertIs<CreateStatement>(handler.body[0])
        assertEquals("User", create.entityName)
        assertNull(create.forEntity)
        assertEquals(2, create.assignments.size)
        assertEquals("name", create.assignments[0].name)
        assertIs<StringLiteral>(create.assignments[0].value)
        assertEquals("Alice", (create.assignments[0].value as StringLiteral).value)
        assertEquals("email", create.assignments[1].name)
    }

    @Test
    fun `parses create with for`() {
        val program = parse("""
handle LOGIN_ATTEMPT:
  create Session for User
""".trimIndent())

        val handler = assertIs<HandlerDeclaration>(program.declarations[0])
        val create = assertIs<CreateStatement>(handler.body[0])
        assertEquals("Session", create.entityName)
        assertEquals("User", create.forEntity)
        assertEquals(0, create.assignments.size)
    }

    @Test
    fun `parses find all`() {
        val program = parse("""
handle LIST_POSTS:
  find all Posts where published is true
""".trimIndent())

        val handler = assertIs<HandlerDeclaration>(program.declarations[0])
        val find = assertIs<FindStatement>(handler.body[0])
        assertTrue(find.all)
        assertEquals("Posts", find.entityName)
        assertEquals(1, find.conditions.size)
        assertEquals("published", find.conditions[0].field)
        assertEquals(ComparisonOperator.IS, find.conditions[0].operator)
        assertIs<BooleanLiteral>(find.conditions[0].value)
    }

    @Test
    fun `parses find without conditions`() {
        val program = parse("""
handle GET_USER:
  find User
""".trimIndent())

        val handler = assertIs<HandlerDeclaration>(program.declarations[0])
        val find = assertIs<FindStatement>(handler.body[0])
        assertEquals("User", find.entityName)
        assertEquals(false, find.all)
        assertEquals(0, find.conditions.size)
    }

    @Test
    fun `parses update statement`() {
        val program = parse("""
handle PROMOTE_USER:
  update User set role = "admin" where email is "alice@test.com"
""".trimIndent())

        val handler = assertIs<HandlerDeclaration>(program.declarations[0])
        val update = assertIs<UpdateStatement>(handler.body[0])
        assertEquals("User", update.entityName)
        assertEquals(1, update.assignments.size)
        assertEquals("role", update.assignments[0].name)
        assertIs<StringLiteral>(update.assignments[0].value)
        assertEquals("admin", (update.assignments[0].value as StringLiteral).value)
        assertEquals(1, update.conditions.size)
        assertEquals("email", update.conditions[0].field)
        assertEquals(ComparisonOperator.IS, update.conditions[0].operator)
    }

    @Test
    fun `parses delete statement`() {
        val program = parse("""
handle CLEANUP:
  delete Session where expires_at is before now
""".trimIndent())

        val handler = assertIs<HandlerDeclaration>(program.declarations[0])
        val delete = assertIs<DeleteStatement>(handler.body[0])
        assertEquals("Session", delete.entityName)
        assertEquals(1, delete.conditions.size)
        assertEquals("expires_at", delete.conditions[0].field)
        assertEquals(ComparisonOperator.IS_BEFORE, delete.conditions[0].operator)
        assertIs<NowLiteral>(delete.conditions[0].value)
    }

    @Test
    fun `parses emit statement with arguments`() {
        val program = parse("""
handle TEST:
  emit RESULT with value = "ok", code = 200
""".trimIndent())

        val handler = assertIs<HandlerDeclaration>(program.declarations[0])
        val emit = assertIs<EmitStatement>(handler.body[0])
        assertEquals("RESULT", emit.eventName)
        assertEquals(2, emit.arguments.size)
        assertEquals("value", emit.arguments[0].name)
        assertIs<StringLiteral>(emit.arguments[0].value)
        assertEquals("code", emit.arguments[1].name)
        assertIs<NumberLiteral>(emit.arguments[1].value)
        assertEquals(200.0, (emit.arguments[1].value as NumberLiteral).value)
    }

    @Test
    fun `parses emit statement without arguments`() {
        val program = parse("""
handle TEST:
  emit SIMPLE_EVENT
""".trimIndent())

        val handler = assertIs<HandlerDeclaration>(program.declarations[0])
        val emit = assertIs<EmitStatement>(handler.body[0])
        assertEquals("SIMPLE_EVENT", emit.eventName)
        assertEquals(0, emit.arguments.size)
    }

    @Test
    fun `parses stop statement`() {
        val program = parse("""
handle TEST:
  stop
""".trimIndent())

        val handler = assertIs<HandlerDeclaration>(program.declarations[0])
        assertIs<StopStatement>(handler.body[0])
    }

    @Test
    fun `parses if without otherwise`() {
        val program = parse("""
handle TEST:
  if no User found:
    emit ERROR with message = "not found"
""".trimIndent())

        val handler = assertIs<HandlerDeclaration>(program.declarations[0])
        val ifStmt = assertIs<IfStatement>(handler.body[0])
        assertIs<NoResultCondition>(ifStmt.condition)
        assertEquals(1, ifStmt.body.size)
        assertNull(ifStmt.otherwise)
    }

    @Test
    fun `parses is not comparison operator`() {
        val program = parse("""
handle TEST:
  find User where role is not "admin"
""".trimIndent())

        val handler = assertIs<HandlerDeclaration>(program.declarations[0])
        val find = assertIs<FindStatement>(handler.body[0])
        assertEquals(ComparisonOperator.IS_NOT, find.conditions[0].operator)
    }

    @Test
    fun `parses is after comparison operator`() {
        val program = parse("""
handle TEST:
  find Session where created_at is after now
""".trimIndent())

        val handler = assertIs<HandlerDeclaration>(program.declarations[0])
        val find = assertIs<FindStatement>(handler.body[0])
        assertEquals(ComparisonOperator.IS_AFTER, find.conditions[0].operator)
    }

    @Test
    fun `parses contains comparison operator`() {
        val program = parse("""
handle TEST:
  find Post where body contains "keyword"
""".trimIndent())

        val handler = assertIs<HandlerDeclaration>(program.declarations[0])
        val find = assertIs<FindStatement>(handler.body[0])
        assertEquals(ComparisonOperator.CONTAINS, find.conditions[0].operator)
    }

    @Test
    fun `parses starts with comparison operator`() {
        val program = parse("""
handle TEST:
  find User where name starts with "A"
""".trimIndent())

        val handler = assertIs<HandlerDeclaration>(program.declarations[0])
        val find = assertIs<FindStatement>(handler.body[0])
        assertEquals(ComparisonOperator.STARTS_WITH, find.conditions[0].operator)
    }

    @Test
    fun `parses is greater than comparison operator`() {
        val program = parse("""
handle TEST:
  find Post where views is greater than 100
""".trimIndent())

        val handler = assertIs<HandlerDeclaration>(program.declarations[0])
        val find = assertIs<FindStatement>(handler.body[0])
        assertEquals(ComparisonOperator.IS_GREATER_THAN, find.conditions[0].operator)
    }

    @Test
    fun `parses is less than comparison operator`() {
        val program = parse("""
handle TEST:
  find Post where views is less than 10
""".trimIndent())

        val handler = assertIs<HandlerDeclaration>(program.declarations[0])
        val find = assertIs<FindStatement>(handler.body[0])
        assertEquals(ComparisonOperator.IS_LESS_THAN, find.conditions[0].operator)
    }

    @Test
    fun `parses find with multiple where conditions`() {
        val program = parse("""
handle TEST:
  find User where role is "admin" and active is true
""".trimIndent())

        val handler = assertIs<HandlerDeclaration>(program.declarations[0])
        val find = assertIs<FindStatement>(handler.body[0])
        assertEquals(2, find.conditions.size)
        assertEquals("role", find.conditions[0].field)
        assertEquals("active", find.conditions[1].field)
    }

    // --- Page Tests ---

    @Test
    fun `parses page with layout`() {
        val program = parse("""
page LoginPage:
  layout main:
    heading "Welcome Back"
    form login-form:
      input username: placeholder "Email", binds to email
      button "Sign In": emits LOGIN_ATTEMPT with email, password
""".trimIndent())

        val page = assertIs<PageDeclaration>(program.declarations[0])
        assertEquals("LoginPage", page.name)
        assertEquals(1, page.body.size)

        val layout = assertIs<LayoutElement>(page.body[0])
        assertEquals("main", layout.name)
        assertEquals(2, layout.children.size)

        val heading = assertIs<HeadingElement>(layout.children[0])
        assertEquals("Welcome Back", heading.text)

        val form = assertIs<FormElement>(layout.children[1])
        assertEquals("login-form", form.name)
        assertEquals(2, form.children.size)

        val input = assertIs<InputElement>(form.children[0])
        assertEquals("username", input.name)

        val button = assertIs<ButtonElement>(form.children[1])
        assertEquals("Sign In", button.text)
    }

    @Test
    fun `parses input element properties`() {
        val program = parse("""
page TestPage:
  input email: placeholder "Email", type secret, binds to email_field
""".trimIndent())

        val page = assertIs<PageDeclaration>(program.declarations[0])
        val input = assertIs<InputElement>(page.body[0])
        assertEquals("email", input.name)
        assertEquals(3, input.properties.size)
        assertIs<InputProperty.Placeholder>(input.properties[0])
        assertEquals("Email", (input.properties[0] as InputProperty.Placeholder).text)
        assertIs<InputProperty.Type>(input.properties[1])
        assertEquals("secret", (input.properties[1] as InputProperty.Type).typeName)
        assertIs<InputProperty.BindsTo>(input.properties[2])
        assertEquals("email_field", (input.properties[2] as InputProperty.BindsTo).field)
    }

    @Test
    fun `parses button with action`() {
        val program = parse("""
page TestPage:
  button "Submit": emits SUBMIT_FORM with name, email
""".trimIndent())

        val page = assertIs<PageDeclaration>(program.declarations[0])
        val button = assertIs<ButtonElement>(page.body[0])
        assertEquals("Submit", button.text)
        assertTrue(button.action != null)
        assertEquals("SUBMIT_FORM", button.action!!.eventName)
        assertEquals(listOf("name", "email"), button.action!!.arguments)
    }

    @Test
    fun `parses button without action`() {
        val program = parse("""
page TestPage:
  button "Cancel"
""".trimIndent())

        val page = assertIs<PageDeclaration>(program.declarations[0])
        val button = assertIs<ButtonElement>(page.body[0])
        assertEquals("Cancel", button.text)
        assertNull(button.action)
    }

    @Test
    fun `parses page with multiple elements`() {
        val program = parse("""
page DashboardPage:
  heading "Dashboard"
  button "Logout": emits LOGOUT
""".trimIndent())

        val page = assertIs<PageDeclaration>(program.declarations[0])
        assertEquals(2, page.body.size)
        assertIs<HeadingElement>(page.body[0])
        assertIs<ButtonElement>(page.body[1])
    }

    // --- Reaction Tests ---

    @Test
    fun `parses store reaction`() {
        val program = parse("""
on LOGIN_SUCCESS:
  store User from LOGIN_SUCCESS.user
  navigate to Dashboard
""".trimIndent())

        val reaction = assertIs<ReactionDeclaration>(program.declarations[0])
        assertEquals("LOGIN_SUCCESS", reaction.eventName)
        assertEquals(2, reaction.actions.size)

        val store = assertIs<StoreAction>(reaction.actions[0])
        assertEquals("User", store.entityName)
        assertIs<DotAccess>(store.from)
        assertEquals("user", (store.from as DotAccess).field)

        val nav = assertIs<NavigateAction>(reaction.actions[1])
        assertEquals("Dashboard", nav.targetPage)
    }

    @Test
    fun `parses show alert reaction`() {
        val program = parse("""
on LOGIN_FAILURE:
  show alert LOGIN_FAILURE.message on LoginPage
""".trimIndent())

        val reaction = assertIs<ReactionDeclaration>(program.declarations[0])
        val alert = assertIs<ShowAlertAction>(reaction.actions[0])
        assertIs<DotAccess>(alert.message)
        assertEquals("message", (alert.message as DotAccess).field)
        assertEquals("LoginPage", alert.targetPage)
    }

    @Test
    fun `parses navigate reaction`() {
        val program = parse("""
on LOGOUT_COMPLETE:
  navigate to LoginPage
""".trimIndent())

        val reaction = assertIs<ReactionDeclaration>(program.declarations[0])
        assertEquals(1, reaction.actions.size)
        val nav = assertIs<NavigateAction>(reaction.actions[0])
        assertEquals("LoginPage", nav.targetPage)
    }

    // --- Style Tests ---

    @Test
    fun `parses style block`() {
        val program = parse("""
style login-form:
  background color is white
  border radius is 8px
  padding is 24px
""".trimIndent())

        val style = assertIs<StyleDeclaration>(program.declarations[0])
        assertEquals("login-form", style.targetName)
        assertEquals(3, style.properties.size)
        assertEquals("background color", style.properties[0].name)
        assertEquals("white", style.properties[0].value)
        assertEquals("border radius", style.properties[1].name)
        assertEquals("8px", style.properties[1].value)
        assertEquals("padding", style.properties[2].name)
        assertEquals("24px", style.properties[2].value)
    }

    @Test
    fun `parses style with pseudo state`() {
        val program = parse("""
style submit-button:
  background color is primary
  on hover:
    background color is primary-dark
""".trimIndent())

        val style = assertIs<StyleDeclaration>(program.declarations[0])
        assertEquals(1, style.properties.size)
        assertEquals("background color", style.properties[0].name)
        assertEquals("primary", style.properties[0].value)
        assertEquals(1, style.pseudoStates.size)
        assertEquals("hover", style.pseudoStates[0].state)
        assertEquals(1, style.pseudoStates[0].properties.size)
        assertEquals("background color", style.pseudoStates[0].properties[0].name)
        assertEquals("primary-dark", style.pseudoStates[0].properties[0].value)
    }

    @Test
    fun `parses style with multiple pseudo states`() {
        val program = parse("""
style input-field:
  border color is gray
  on hover:
    border color is blue
  on focus:
    border color is primary
""".trimIndent())

        val style = assertIs<StyleDeclaration>(program.declarations[0])
        assertEquals(1, style.properties.size)
        assertEquals(2, style.pseudoStates.size)
        assertEquals("hover", style.pseudoStates[0].state)
        assertEquals("focus", style.pseudoStates[1].state)
    }

    // --- Multiple Declarations ---

    @Test
    fun `parses multiple declarations`() {
        val program = parse("""
entity User:
  name is Text, required

event LOGIN_ATTEMPT:
  carries email as Text
""".trimIndent())

        assertEquals(2, program.declarations.size)
        assertIs<EntityDeclaration>(program.declarations[0])
        assertIs<EventDeclaration>(program.declarations[1])
    }

    @Test
    fun `parses all declaration types together`() {
        val program = parse("""
entity User:
  name is Text, required

event LOGIN:
  carries email as Text

handle LOGIN:
  find User where email is LOGIN.email

page LoginPage:
  heading "Login"

on LOGIN:
  navigate to Dashboard

style login-form:
  padding is 16px
""".trimIndent())

        assertEquals(6, program.declarations.size)
        assertIs<EntityDeclaration>(program.declarations[0])
        assertIs<EventDeclaration>(program.declarations[1])
        assertIs<HandlerDeclaration>(program.declarations[2])
        assertIs<PageDeclaration>(program.declarations[3])
        assertIs<ReactionDeclaration>(program.declarations[4])
        assertIs<StyleDeclaration>(program.declarations[5])
    }

    // --- Expression Tests ---

    @Test
    fun `parses dot access expressions`() {
        val program = parse("""
handle TEST:
  emit RESULT with value = User.email
""".trimIndent())

        val handler = assertIs<HandlerDeclaration>(program.declarations[0])
        val emit = assertIs<EmitStatement>(handler.body[0])
        val dotAccess = assertIs<DotAccess>(emit.arguments[0].value)
        assertEquals("email", dotAccess.field)
        val target = assertIs<Identifier>(dotAccess.target)
        assertEquals("User", target.name)
    }

    @Test
    fun `parses string literal expression`() {
        val program = parse("""
handle TEST:
  emit RESULT with value = "hello world"
""".trimIndent())

        val handler = assertIs<HandlerDeclaration>(program.declarations[0])
        val emit = assertIs<EmitStatement>(handler.body[0])
        val str = assertIs<StringLiteral>(emit.arguments[0].value)
        assertEquals("hello world", str.value)
    }

    @Test
    fun `parses number literal expression`() {
        val program = parse("""
handle TEST:
  emit RESULT with count = 42
""".trimIndent())

        val handler = assertIs<HandlerDeclaration>(program.declarations[0])
        val emit = assertIs<EmitStatement>(handler.body[0])
        val num = assertIs<NumberLiteral>(emit.arguments[0].value)
        assertEquals(42.0, num.value)
    }

    @Test
    fun `parses boolean literal expression`() {
        val program = parse("""
handle TEST:
  emit RESULT with active = true
""".trimIndent())

        val handler = assertIs<HandlerDeclaration>(program.declarations[0])
        val emit = assertIs<EmitStatement>(handler.body[0])
        val bool = assertIs<BooleanLiteral>(emit.arguments[0].value)
        assertEquals(true, bool.value)
    }

    @Test
    fun `parses identifier expression`() {
        val program = parse("""
handle TEST:
  emit RESULT with user = CurrentUser
""".trimIndent())

        val handler = assertIs<HandlerDeclaration>(program.declarations[0])
        val emit = assertIs<EmitStatement>(handler.body[0])
        val ident = assertIs<Identifier>(emit.arguments[0].value)
        assertEquals("CurrentUser", ident.name)
    }

    @Test
    fun `parses now literal expression`() {
        val program = parse("""
handle TEST:
  create Session with created_at = now
""".trimIndent())

        val handler = assertIs<HandlerDeclaration>(program.declarations[0])
        val create = assertIs<CreateStatement>(handler.body[0])
        assertIs<NowLiteral>(create.assignments[0].value)
    }

    // --- Error Tests ---

    @Test
    fun `throws on invalid declaration`() {
        assertFailsWith<ParseException> {
            parse("foobar something")
        }
    }

    @Test
    fun `throws on missing colon after entity name`() {
        assertFailsWith<ParseException> {
            parse("entity User\n  name is Text")
        }
    }

    @Test
    fun `throws on invalid field modifier`() {
        assertFailsWith<ParseException> {
            parse("entity User:\n  name is Text, foobar")
        }
    }

    @Test
    fun `throws on invalid type`() {
        assertFailsWith<ParseException> {
            parse("entity User:\n  name is 42")
        }
    }

    @Test
    fun `throws on invalid statement in handler`() {
        assertFailsWith<ParseException> {
            parse("handle TEST:\n  invalid_statement something")
        }
    }

    @Test
    fun `parses empty program`() {
        val program = parse("")
        assertEquals(0, program.declarations.size)
    }

    // ---------------------------------------------------------------
    // Text element tests
    // ---------------------------------------------------------------

    @Test
    fun `parses text element with string literal`() {
        val program = parse("""
page TestPage:
  layout main:
    text "Hello World"
""".trimIndent())
        val page = program.declarations[0] as PageDeclaration
        val layout = page.body[0] as LayoutElement
        val textEl = layout.children[0] as TextElement
        assertTrue(textEl.value is StringLiteral)
        assertEquals("Hello World", (textEl.value as StringLiteral).value)
    }

    @Test
    fun `parses text element with dot access`() {
        val program = parse("""
page TestPage:
  layout main:
    text User.name
""".trimIndent())
        val page = program.declarations[0] as PageDeclaration
        val layout = page.body[0] as LayoutElement
        val textEl = layout.children[0] as TextElement
        assertTrue(textEl.value is DotAccess)
        assertEquals("name", (textEl.value as DotAccess).field)
    }

    @Test
    fun `text keyword does not conflict with text field in entity`() {
        val program = parse("""
entity Comment:
  text is Text
  author is Text
""".trimIndent())
        val entity = program.declarations[0] as EntityDeclaration
        assertEquals("text", entity.fields[0].name)
        assertEquals(PlaingType.TextType, entity.fields[0].type)
    }

    // ---------------------------------------------------------------
    // List element tests
    // ---------------------------------------------------------------

    @Test
    fun `parses list element with single field`() {
        val program = parse("""
page TestPage:
  layout main:
    list items: each Note show Note.title
""".trimIndent())
        val page = program.declarations[0] as PageDeclaration
        val layout = page.body[0] as LayoutElement
        val listEl = layout.children[0] as ListElement
        assertEquals("items", listEl.name)
        assertEquals("Note", listEl.entityName)
        assertEquals(listOf("title"), listEl.fields)
    }

    @Test
    fun `parses list element with multiple fields`() {
        val program = parse("""
page TestPage:
  layout main:
    list notes: each Note show Note.title, Note.body
""".trimIndent())
        val page = program.declarations[0] as PageDeclaration
        val layout = page.body[0] as LayoutElement
        val listEl = layout.children[0] as ListElement
        assertEquals("notes", listEl.name)
        assertEquals("Note", listEl.entityName)
        assertEquals(listOf("title", "body"), listEl.fields)
    }

    // ---------------------------------------------------------------
    // Store all reaction tests
    // ---------------------------------------------------------------

    @Test
    fun `parses store all reaction`() {
        val program = parse("""
on NOTES_LOADED:
  store all Notes from NOTES_LOADED.notes
""".trimIndent())
        val reaction = program.declarations[0] as ReactionDeclaration
        val action = reaction.actions[0] as StoreAllAction
        assertEquals("Notes", action.entityName)
        assertTrue(action.from is DotAccess)
    }

    @Test
    fun `parses store all alongside regular store`() {
        val program = parse("""
on DATA_LOADED:
  store User from DATA_LOADED.user
  store all Notes from DATA_LOADED.notes
""".trimIndent())
        val reaction = program.declarations[0] as ReactionDeclaration
        assertTrue(reaction.actions[0] is StoreAction)
        assertTrue(reaction.actions[1] is StoreAllAction)
    }

    // ---------------------------------------------------------------
    // Full notes app test
    // ---------------------------------------------------------------

    @Test
    fun `parses a complete notes app`() {
        val program = parse("""
entity Note:
  title is Text, required
  body is Text, required
  created_at is Date, default now

event CREATE_NOTE:
  carries title as Text, body as Text

event NOTE_CREATED:
  carries note as Note

event NOTES_LOADED:
  carries notes as Text

page NotesPage:
  layout main:
    heading "My Notes"
    list notes: each Note show Note.title, Note.body
    form new-note:
      input title: placeholder "Title", binds to title
      input body: placeholder "Write your note...", binds to body
      button "Save": emits CREATE_NOTE with title, body

on NOTE_CREATED:
  store all Notes from NOTE_CREATED.notes
""".trimIndent())

        val entities = program.declarations.filterIsInstance<EntityDeclaration>()
        val events = program.declarations.filterIsInstance<EventDeclaration>()
        val pages = program.declarations.filterIsInstance<PageDeclaration>()
        val reactions = program.declarations.filterIsInstance<ReactionDeclaration>()

        assertEquals(1, entities.size)
        assertEquals(3, events.size)
        assertEquals(1, pages.size)
        assertEquals(1, reactions.size)
        assertEquals("Note", entities[0].name)
    }

    // --- Select + Edit features ---

    @Test
    fun `parses list with on click select`() {
        val program = parse("""
page NotesPage:
  layout main:
    list notes: each Note show Note.title, Note.body, on click select Note
""".trimIndent())
        val page = program.declarations[0] as PageDeclaration
        val layout = page.body[0] as LayoutElement
        val list = layout.children[0] as ListElement
        assertEquals("notes", list.name)
        assertEquals("Note", list.entityName)
        assertEquals(listOf("title", "body"), list.fields)
        assertEquals("Note", list.onClickSelect)
    }

    @Test
    fun `parses list without on click select`() {
        val program = parse("""
page NotesPage:
  layout main:
    list notes: each Note show Note.title
""".trimIndent())
        val page = program.declarations[0] as PageDeclaration
        val layout = page.body[0] as LayoutElement
        val list = layout.children[0] as ListElement
        assertNull(list.onClickSelect)
    }

    @Test
    fun `parses input with fills from`() {
        val program = parse("""
page EditPage:
  layout main:
    form edit-form:
      input title: placeholder "Title", binds to title, fills from Note.title
""".trimIndent())
        val page = program.declarations[0] as PageDeclaration
        val layout = page.body[0] as LayoutElement
        val form = layout.children[0] as FormElement
        val input = form.children[0] as InputElement
        val fillsFrom = input.properties.filterIsInstance<InputProperty.FillsFrom>().first()
        assertEquals("Note", fillsFrom.entityName)
        assertEquals("title", fillsFrom.fieldName)
    }

    @Test
    fun `parses full select and edit page`() {
        val program = parse("""
page NotesPage:
  layout main:
    heading "My Notes"
    list notes: each Note show Note.title, Note.body, on click select Note
    form edit-form:
      input title: placeholder "Title", binds to title, fills from Note.title
      input body: placeholder "Body", binds to body, fills from Note.body
      button "Save": emits UPDATE_NOTE with title, body
""".trimIndent())
        val page = program.declarations[0] as PageDeclaration
        val layout = page.body[0] as LayoutElement
        assertEquals(3, layout.children.size)
        val list = layout.children[1] as ListElement
        assertEquals("Note", list.onClickSelect)
        val form = layout.children[2] as FormElement
        assertEquals(3, form.children.size)
    }

    @Test
    fun `parses conditional button`() {
        val program = parse("""
page NotesPage:
  layout main:
    form note-form:
      input title: placeholder "Title", binds to title
      button "Save": if Note selected emits UPDATE_NOTE with title otherwise emits CREATE_NOTE with title
""".trimIndent())
        val page = program.declarations[0] as PageDeclaration
        val layout = page.body[0] as LayoutElement
        val form = layout.children[0] as FormElement
        val button = form.children[1] as ButtonElement

        // Default action (otherwise)
        assertNotNull(button.action)
        assertEquals("CREATE_NOTE", button.action!!.eventName)
        assertEquals(listOf("title"), button.action!!.arguments)

        // Conditional action (if selected)
        assertNotNull(button.conditionalAction)
        assertEquals("Note", button.conditionalAction!!.entityName)
        assertEquals("UPDATE_NOTE", button.conditionalAction!!.action.eventName)
        assertEquals(listOf("title"), button.conditionalAction!!.action.arguments)
    }

    @Test
    fun `parses regular button without conditional`() {
        val program = parse("""
page LoginPage:
  layout main:
    button "Sign In": emits LOGIN_ATTEMPT with email
""".trimIndent())
        val page = program.declarations[0] as PageDeclaration
        val layout = page.body[0] as LayoutElement
        val button = layout.children[0] as ButtonElement
        assertNotNull(button.action)
        assertNull(button.conditionalAction)
    }
}
