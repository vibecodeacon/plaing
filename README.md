# plaing

A plain-English programming language for building SaaS products. Write in English, ship on every platform.

```
entity User:
  name is Text, required
  email is Text, required, unique
  password is Text, required, hidden

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

Plaing compiles `.plaing` files into a Kotlin Multiplatform project — one codebase that runs on web, desktop, iOS, and Android. UI is rendered natively via Compose Multiplatform. All client-server communication happens over WebSockets.

## Why

When you vibe code, AI writes hundreds of lines of code you can't read. If something goes wrong — or right — you have no way to know why.

Plaing is an auditable layer. Instead of generating opaque code, the AI generates plain English that compiles to real software. You can read what it wrote, catch mistakes, and suggest changes — without learning to program.

The goal is to make software creation transparent, not just fast.

## Quick Start

```bash
# Create a new project
plaing new my-app

# Start the dev server (watches for changes, auto-rebuilds)
cd my-app
plaing dev

# Or build once
plaing build
```

## What You Write

| Block | What It Does |
|-------|-------------|
| `entity` | Defines a data model — becomes a DB table + data class |
| `event` | Defines a message type that flows between client and server |
| `handle` | Server-side logic triggered by an event |
| `page` | UI definition — becomes a native screen |
| `on` | Client-side reaction to a server event |
| `style` | CSS-like styling with plain English syntax |

## Language Reference

### Types

`Text`, `Number`, `Boolean`, `Date`, `List of <Type>`, `Optional <Type>`, or any entity name (creates a foreign key).

### Field Modifiers

`required`, `unique`, `hidden`, `default <value>`

### Data Operations (in handlers)

```
find User where email is "test@example.com"
find all Posts where author is User and published is true
create User with name = "Alice", email = "alice@example.com"
update User set role = "admin" where email is "alice@example.com"
delete Session where expires_at is before now
```

### Comparison Operators

`is`, `is not`, `matches`, `is after`, `is before`, `contains`, `starts with`, `is greater than`, `is less than`

### Page Elements

`layout`, `heading`, `form`, `input`, `button`, `text`, `list`

```
text "Hello"                                     # static text
text User.name                                   # display entity field
list notes: each Note show Note.title, Note.body  # scrollable list from state
```

### Input Properties

`placeholder "text"`, `type secret`, `binds to fieldname`

### Reaction Actions

`store Entity from Event.field`, `store all Entity from Event.field` (collections), `navigate to PageName`, `show alert Event.message on PageName`

### Styles

```
style my-button:
  background color is blue
  text color is white
  padding is 12px 24px
  border radius is 6px
  on hover:
    background color is dark-blue
```

Every CSS property is available — `is` replaces `:`, spaces replace hyphens.

## Architecture

```
.plaing files --> Compiler --> Kotlin Multiplatform Project
                                    |
                    +---------------+---------------+
                    |               |               |
                 Server          Desktop         Mobile
                (Ktor/JVM)     (Compose)       (Compose)
                    |
                 SQLite DB
```

All communication is event-driven over WebSockets:

```json
{ "event": "LOGIN_ATTEMPT", "payload": { "email": "...", "password": "..." }, "correlationId": "uuid" }
```

## Building from Source

Requires JDK 21.

```bash
# Run all tests
./gradlew :compiler:test :runtime:jvmTest

# Run the compiler CLI
./gradlew :compiler:run --args="new my-app"
```

## Design Principles

- **Plain English over syntax** — `otherwise` not `else`, `is` not `==`, `find` not `SELECT`
- **Constrained by design** — limited flexibility enables maximum readability
- **No escape hatches** — you can't drop into raw Kotlin or SQL
- **Events are the only primitive** — no REST, no GraphQL, just events over WebSockets
- **Styling is separate** — CSS's model is right, CSS's syntax is the problem
- **One language, every platform** — Kotlin Multiplatform compiles natively everywhere

## Status

This is v0 — a working proof of concept. The compiler and runtime are functional:

- Full parser for all 6 declaration types
- Code generation for entities, events, handlers, pages, styles, reactions
- Working WebSocket server + client
- SQLite database with generated repositories
- Compose Multiplatform UI generation
- CLI with `new`, `build`, `dev`, `parse` commands
- 184 passing tests

What's not done yet: `plaing run` (compile + launch), runtime error messages in plain English, more UI elements, validation rules, permissions, pagination. See [Contributing](#contributing) if any of that interests you.

## Contributing

Plaing is open to contributions of all kinds — code, language design ideas, documentation, and bug reports.

If you're interested in contributing:

1. **Fork and clone** the repo
2. **Run the tests** to make sure everything passes: `./gradlew :compiler:test :runtime:jvmTest`
3. **Pick something to work on** — check the issues, or propose something new
4. **Open a PR** with a clear description of what you changed and why

Some areas where help is especially welcome:

- **Language design** — what constructs would make `.plaing` files more expressive while staying readable?
- **More UI elements** — tables, lists, images, modals, navigation bars
- **`plaing run`** — compile and launch both server + client in one command
- **Runtime error messages** — translate Java stack traces into plain English with source locations
- **Mobile targets** — wire up Android/iOS build configuration in generated projects
- **Web target** — Compose for Web / WASM entry point generation
- **Examples** — sample apps that show off what plaing can do

No contribution is too small. Even fixing a typo or improving an error message matters.

### Development Notes

The project has two modules:

- **`compiler/`** — Kotlin JVM app. The parser, code generators, and CLI live here. This is where most language work happens.
- **`runtime/`** — Kotlin Multiplatform library. The event bus, WebSocket server/client, UI components, and state management. This ships with every generated project.

The `CLAUDE.md` file at the repo root contains detailed context for AI-assisted development — file map, syntax reference, and architecture. If you're using Claude Code or similar tools, it will be loaded automatically.

## Acknowledgments

This project was vibe coded — the initial implementation was built collaboratively between a human and [Claude Code](https://claude.ai/claude-code). We believe in being transparent about how software is made. AI is a tool; the vision, direction, and decisions are human.

## License

MIT License. See [LICENSE](LICENSE) for details.
