# Plaing — Claude Code Context

Plaing is a plain-English programming language that compiles `.plaing` files into Kotlin Multiplatform projects for building SaaS apps. The compiler and runtime are both Kotlin.

## Build & Test

```bash
# Requires JDK 21
export JAVA_HOME=/Users/vibecody/.jdk/jdk-21.0.10+7/Contents/Home

# Run all tests (171 total)
./gradlew :compiler:test :runtime:jvmTest

# Run compiler only (165 tests)
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
      PageGen.kt                  Page → @Composable function
      StyleGen.kt                 Style → Modifier extension
      ReactionGen.kt              Reactions → handleReaction dispatcher
      ClientAppGen.kt             Client main() with Compose Desktop window
      ComparisonMapper.kt         Comparison operators → SQL
    errors/
      ErrorFormatter.kt           Plain-English error messages with source context

runtime/                           Kotlin Multiplatform library
  src/
    commonMain/kotlin/dev/plaing/runtime/
      EventBus.kt                 EventEnvelope, HandlerResult, EventBus (SharedFlow)
      Protocol.kt                 WebSocket JSON wire format
      ui/Components.kt            PlaingHeading, PlaingInput, PlaingButton, PlaingAlert
      state/StateStore.kt         Client-side state (currentPage, entities, alerts)
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
```

### Reactions (client-side event responses)
```
on LOGIN_SUCCESS:
  store User from LOGIN_SUCCESS.user
  navigate to Dashboard

on LOGIN_FAILURE:
  show alert LOGIN_FAILURE.message on LoginPage
```

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

- **Event-driven**: All client↔server communication is WebSocket events (JSON)
- **Wire format**: `{ "event": "LOGIN_ATTEMPT", "payload": {...}, "correlationId": "uuid" }`
- **Compilation**: .plaing → Lexer → Parser → AST → CodeGenerator → Kotlin source files
- **Runtime**: Ktor Netty (server), Ktor CIO (client), Compose Multiplatform (UI), SQLite/JDBC (DB)
- **No raw SQL in .plaing**: Data operations use `find`, `create`, `update`, `delete` with plain English

## Key Conventions

- Event names: `SCREAMING_SNAKE_CASE`
- Entity names: `PascalCase`
- Field names: `snake_case`
- Indentation-based blocks (2 spaces)
- `otherwise` instead of `else`
- `is` as the primary comparison operator
- EntityRef fields generate `_id` foreign key columns
- Generated package: `dev.plaing.generated.*`
