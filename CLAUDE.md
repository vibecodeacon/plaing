# Plaing — Claude Code Context

Plaing is a plain-English programming language that compiles `.plaing` files into Kotlin Multiplatform projects for building SaaS apps. The compiler and runtime are both Kotlin.

## Build & Test

```bash
# Requires JDK 21
export JAVA_HOME=/Users/vibecody/.jdk/jdk-21.0.10+7/Contents/Home

# Run all tests (184 total)
./gradlew :compiler:test :runtime:jvmTest

# Run compiler only (178 tests)
./gradlew :compiler:test

# Run runtime only (6 integration tests)
./gradlew :runtime:jvmTest

# Run the CLI
./gradlew :compiler:run --args="new my-app"
./gradlew :compiler:run --args="build src"
./gradlew :compiler:run --args="parse file.plaing"
```

## Project Structure

```
compiler/                          Kotlin JVM application
  src/main/kotlin/dev/plaing/compiler/
    cli/
      Main.kt                     CLI entry point (new, build, dev, parse)
      NewCommand.kt               Project scaffolding (plaing new <name>)
      DevCommand.kt               File watcher + hot recompilation
    parser/
      Ast.kt                      All AST node types (30+ nodes)
      Lexer.kt                    Tokenizer with INDENT/DEDENT (Python-style)
      Parser.kt                   Recursive descent parser
    codegen/
      CodeGenerator.kt            Orchestrator — produces all generated files
      TypeMapper.kt               PlaingType → Kotlin/SQL type mapping
      EntityAnalyzer.kt           Pre-processes entities (foreign keys, topological sort)
      KotlinDataClassGenerator.kt Entity → data class
      SqlSchemaGenerator.kt       Entity → CREATE TABLE
      RepositoryGenerator.kt      Entity → JDBC repository
      DatabaseInitGenerator.kt    PlaingDatabase object
      EventGen.kt                 Event → @Serializable data class
      HandlerGen.kt               Handler → EventHandler implementation
      ServerAppGen.kt             Server main() using PlaingServer
      PageGen.kt                  Page → @Composable function (handles text, list, etc.)
      StyleGen.kt                 Style → Modifier extension
      ReactionGen.kt              Reactions → handleReaction dispatcher (store, store all, etc.)
      ClientAppGen.kt             Client main() with Compose Desktop window
      ComparisonMapper.kt         Comparison operators → SQL
    errors/
      ErrorFormatter.kt           Plain-English error messages with source context

runtime/                           Kotlin Multiplatform library
  src/
    commonMain/kotlin/dev/plaing/runtime/
      EventBus.kt                 EventEnvelope, HandlerResult, EventBus (SharedFlow)
      Protocol.kt                 WebSocket JSON wire format
      ui/Components.kt            PlaingHeading, PlaingInput, PlaingButton, PlaingText, PlaingListItem, PlaingAlert
      state/StateStore.kt         Client-side state (currentPage, entities, entity collections, alerts)
      auth/AuthHelpers.kt         Password hashing, token generation
    jvmMain/kotlin/dev/plaing/runtime/
      server/PlaingServer.kt      High-level server (EventBus + WsServer + DB + Sessions)
      server/WsServer.kt          Ktor Netty WebSocket server
      client/WsClient.kt          Ktor CIO WebSocket client
      db/DatabaseManager.kt       JDBC wrapper (SQLite, WAL mode)
      session/SessionManager.kt   Token-based session management

tests/fixtures/                    Sample .plaing files used by tests
stdlib/                            (placeholder for built-in functions)
```

## Compilation Pipeline

Every feature goes through the full pipeline. If you add a new language construct, you must wire up ALL layers:

```
.plaing source → Lexer (tokens) → Parser (AST nodes) → CodeGenerator → Kotlin source files
                                                              ↓
                                              PageGen / HandlerGen / EventGen / etc.
                                                              ↓
                                              Runtime components (Components.kt, StateStore.kt)
```

**Checklist for adding a new language feature:**
1. Add AST node type in `Ast.kt`
2. Add token type in `Lexer.kt` (if needed — see contextual keywords below)
3. Add parsing logic in `Parser.kt`
4. Add code generation in the appropriate `*Gen.kt` file
5. Add runtime component in `Components.kt` or `StateStore.kt` (if needed)
6. Add tests in both `ParserTest.kt` and `CodeGeneratorTest.kt`
7. Update this CLAUDE.md with the new syntax

## Contextual Keywords (IMPORTANT)

Some keywords conflict with other uses. These are parsed by checking the identifier VALUE, not by token type:

- **`text`** — UI element for displaying values. Parsed as `check(TokenType.IDENTIFIER) && current().value == "text"` in `Parser.kt:parseUiElement()`. NOT a lexer keyword because `text` is also a valid entity field name (`text is Text`). The AST node is `TextElement`. CodeGen is in `PageGen.kt`.

- **`list`** — UI element for rendering collections. Parsed as `check(TokenType.IDENTIFIER) && current().value == "list"` in `Parser.kt:parseUiElement()`. NOT a lexer keyword because `List` (capital L) is the type keyword (`List of Post`). Lowercase `list` in a page context means the UI element. The AST node is `ListElement`. CodeGen is in `PageGen.kt`.

If you're looking for where these are handled in the parser, search for `current().value ==` — don't search for a token type constant.

## Language Syntax

### Entities (data models → DB tables + data classes)
```
entity User:
  name is Text, required
  email is Text, required, unique
  password is Text, required, hidden
  created_at is Date, default now
  role is Text, default "member"
  posts is List of Post
```

Types: `Text`, `Number`, `Boolean`, `Date`, `List of X`, `Optional X`, or entity name (foreign key).
Modifiers: `required`, `unique`, `hidden`, `default <value>`.

### Events (SCREAMING_SNAKE_CASE, always)
```
event LOGIN_ATTEMPT:
  carries email as Text, password as Text
```

### Handlers (server-side logic)
```
handle LOGIN_ATTEMPT:
  find User where email is LOGIN_ATTEMPT.email
  if no User found:
    emit LOGIN_FAILURE with message = "invalid credentials"
    stop
  if User.password matches LOGIN_ATTEMPT.password:
    create Session for User
    emit LOGIN_SUCCESS with user = User, token = Session.token
  otherwise:
    emit LOGIN_FAILURE with message = "invalid credentials"
```

Statements: `find`/`find all`, `create`, `update ... set`, `delete`, `emit`, `if`/`otherwise`, `stop`.
Operators: `is`, `is not`, `matches`, `is after`, `is before`, `contains`, `starts with`, `is greater than`, `is less than`.

### Pages (UI → @Composable functions)
```
page LoginPage:
  layout main:
    heading "Welcome Back"
    form login-form:
      input username: placeholder "Email", binds to email
      input password: placeholder "Password", type secret, binds to password
      button "Sign In": emits LOGIN_ATTEMPT with email, password

page NotesPage:
  layout main:
    heading "My Notes"
    text User.name
    list notes: each Note show Note.title, Note.body
    form new-note:
      input title: placeholder "Title", binds to title
      button "Save": emits CREATE_NOTE with title
```

UI elements and where they're implemented:

| Element | Syntax | AST Node | Parser | CodeGen | Runtime |
|---------|--------|----------|--------|---------|---------|
| `layout` | `layout name:` | `LayoutElement` | `parseLayoutElement()` | `PageGen` → `Column` | — |
| `heading` | `heading "text"` | `HeadingElement` | `parseHeadingElement()` | `PageGen` → `PlaingHeading` | `Components.kt` |
| `form` | `form name:` | `FormElement` | `parseFormElement()` | `PageGen` → `Column` | — |
| `input` | `input name: props` | `InputElement` | `parseInputElement()` | `PageGen` → `PlaingInput` | `Components.kt` |
| `button` | `button "text": action` | `ButtonElement` | `parseButtonElement()` | `PageGen` → `PlaingButton` | `Components.kt` |
| `text` | `text "str"` / `text E.f` | `TextElement` | contextual keyword* | `PageGen` → `PlaingText` | `Components.kt` |
| `list` | `list n: each E show E.f` | `ListElement` | contextual keyword* | `PageGen` → `LazyColumn` | `Components.kt` |

*See "Contextual Keywords" section above.

### Reactions (client-side event responses)
```
on LOGIN_SUCCESS:
  store User from LOGIN_SUCCESS.user
  navigate to Dashboard

on LOGIN_FAILURE:
  show alert LOGIN_FAILURE.message on LoginPage

on NOTES_LOADED:
  store all Notes from NOTES_LOADED.notes
```

| Action | Syntax | AST Node | StateStore Method |
|--------|--------|----------|-------------------|
| Store single entity | `store E from expr` | `StoreAction` | `storeEntity()` |
| Store collection | `store all E from expr` | `StoreAllAction` | `storeEntityList()` |
| Navigate | `navigate to Page` | `NavigateAction` | `navigateTo()` |
| Show alert | `show alert expr on Page` | `ShowAlertAction` | `showAlert()` |

### Styles (CSS with plain English)
```
style login-form:
  background color is white
  border radius is 8px
  padding is 24px
  on hover:
    background color is gray
```

## Architecture

- **Event-driven**: All client-server communication is WebSocket events (JSON)
- **Wire format**: `{ "event": "LOGIN_ATTEMPT", "payload": {...}, "correlationId": "uuid" }`
- **Compilation**: .plaing → Lexer → Parser → AST → CodeGenerator → Kotlin source files
- **Runtime**: Ktor Netty (server), Ktor CIO (client), Compose Multiplatform (UI), SQLite/JDBC (DB)
- **No raw SQL in .plaing**: Data operations use `find`, `create`, `update`, `delete` with plain English
- **StateStore**: Holds single entities (`storeEntity`/`getEntity`) AND collections (`storeEntityList`/`getEntityList`/`addToEntityList`). Collections use `mutableStateListOf` for Compose reactivity.

## Key Conventions

- Event names: `SCREAMING_SNAKE_CASE`
- Entity names: `PascalCase`
- Field names: `snake_case`
- Indentation-based blocks (2 spaces)
- `otherwise` instead of `else`
- `is` as the primary comparison operator
- EntityRef fields generate `_id` foreign key columns
- Generated package: `dev.plaing.generated.*`

## Common Mistakes

- **Don't look for `text`/`list` as lexer token types.** They are contextual keywords parsed by identifier value, not token type. See "Contextual Keywords" section.
- **Don't add `text` or `list` to the Lexer keyword map.** `text` conflicts with entity field names. `list` (lowercase) conflicts with `List` (uppercase, the type keyword).
- **Always run the full test suite** after changes: `./gradlew :compiler:test :runtime:jvmTest`
- **The `when` block in `parseUiElement()` uses `when {}` with boolean conditions**, not `when (tokenType)`, because some elements need identifier value checks.
- **Test helpers in CodeGeneratorTest**: use `parseProgram(source)` to parse, then cast declarations. Use `assertTrue(code.contains(...))` for assertions, not `assertContains`.
