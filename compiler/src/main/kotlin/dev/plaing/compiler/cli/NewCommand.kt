package dev.plaing.compiler.cli

import java.io.File

/**
 * Scaffolds a new plaing project with the given name.
 * Creates a directory structure with sample .plaing files and build configuration.
 */
class NewCommand {

    fun execute(name: String, parentDir: File = File(".")) {
        val projectDir = File(parentDir, name)
        if (projectDir.exists()) {
            System.err.println("A folder called \"$name\" already exists here. Please choose a different name or delete the existing folder.")
            System.exit(1)
        }

        println("Creating new plaing project: $name")
        println()

        // Create directory structure
        projectDir.mkdirs()
        File(projectDir, "src").mkdirs()

        // Write .plaing source files
        writeFile(projectDir, "src/entities.plaing", sampleEntities())
        writeFile(projectDir, "src/events.plaing", sampleEvents())
        writeFile(projectDir, "src/handlers.plaing", sampleHandlers())
        writeFile(projectDir, "src/pages.plaing", samplePages())
        writeFile(projectDir, "src/reactions.plaing", sampleReactions())
        writeFile(projectDir, "src/styles.plaing", sampleStyles())

        // Write project config
        writeFile(projectDir, "plaing.json", projectConfig(name))
        writeFile(projectDir, ".gitignore", gitIgnore())

        println("  Created src/entities.plaing")
        println("  Created src/events.plaing")
        println("  Created src/handlers.plaing")
        println("  Created src/pages.plaing")
        println("  Created src/reactions.plaing")
        println("  Created src/styles.plaing")
        println("  Created plaing.json")
        println("  Created .gitignore")
        println()
        println("Your project is ready! Next steps:")
        println()
        println("  cd $name")
        println("  plaing dev       Start the dev server with hot reload")
        println("  plaing build     Build for production")
        println()
        println("Open the .plaing files in src/ to start building your app.")
    }

    private fun writeFile(projectDir: File, relativePath: String, content: String) {
        val file = File(projectDir, relativePath)
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    private fun projectConfig(name: String) = """{
  "name": "$name",
  "version": "0.1.0",
  "source": "src",
  "output": "build/generated",
  "server": {
    "port": 8080
  }
}
"""

    private fun gitIgnore() = """build/
*.class
.gradle/
.idea/
*.iml
"""

    private fun sampleEntities() = """# Data models for your app
# Each entity becomes a database table and a shared data class

entity User:
  name is Text, required
  email is Text, required, unique
  password is Text, required, hidden
  created_at is Date, default now

entity Task:
  title is Text, required
  completed is Boolean, default false
  owner is User, required
  created_at is Date, default now
"""

    private fun sampleEvents() = """# Events are messages that flow between client and server
# Use SCREAMING_SNAKE_CASE for event names

event LOGIN_ATTEMPT:
  carries email as Text, password as Text

event LOGIN_SUCCESS:
  carries user as User, token as Text

event LOGIN_FAILURE:
  carries message as Text

event CREATE_TASK:
  carries title as Text

event TASK_CREATED:
  carries task as Task
"""

    private fun sampleHandlers() = """# Handlers run on the server when an event arrives
# They can read/write the database and emit response events

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
"""

    private fun samplePages() = """# Pages define your UI using plain English
# Each page compiles to a native screen on every platform

page LoginPage:
  layout main:
    heading "Welcome"
    form login-form:
      input username: placeholder "Email", binds to email
      input password: placeholder "Password", type secret, binds to password
      button "Sign In": emits LOGIN_ATTEMPT with email, password
"""

    private fun sampleReactions() = """# Reactions run on the client when an event arrives from the server
# They update the UI state

on LOGIN_SUCCESS:
  store User from LOGIN_SUCCESS.user
  navigate to Dashboard

on LOGIN_FAILURE:
  show alert LOGIN_FAILURE.message on LoginPage
"""

    private fun sampleStyles() = """# Styles work like CSS but with plain English syntax
# "is" replaces ":", spaces replace hyphens

style login-form:
  background color is white
  border radius is 8px
  padding is 24px
  max width is 400px
"""
}
