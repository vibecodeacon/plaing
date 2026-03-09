package dev.plaing.compiler.cli

import dev.plaing.compiler.codegen.CodeGenerator
import dev.plaing.compiler.errors.ErrorFormatter
import dev.plaing.compiler.parser.*
import java.io.File

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("plaing v0.1.0")
        println()
        println("Usage:")
        println("  plaing new <name>             Create a new plaing project")
        println("  plaing build [dir]            Build the project")
        println("  plaing dev [dir]              Start dev server with hot reload")
        println("  plaing parse <file.plaing>    Parse a .plaing file and print the AST")
        return
    }

    when (args[0]) {
        "new" -> {
            if (args.size < 2) {
                System.err.println("Please provide a project name, like: plaing new my-app")
                System.exit(1)
            }
            NewCommand().execute(args[1])
        }
        "dev" -> {
            val sourceDir = if (args.size > 1) File(args[1]) else File("src")
            if (!sourceDir.exists()) {
                System.err.println("I couldn't find a \"${sourceDir.path}\" directory. Are you in a plaing project folder?")
                System.err.println("You can create one with: plaing new my-app")
                System.exit(1)
            }
            DevCommand().execute(sourceDir)
        }
        "build" -> {
            val sourceDir = if (args.size > 1) File(args[1]) else File("src")
            if (!sourceDir.exists()) {
                // Fall back to current dir if no src/ exists
                val fallback = File(".")
                val hasPlaingFiles = fallback.walkTopDown().any { it.extension == "plaing" }
                if (hasPlaingFiles) {
                    buildProject(fallback)
                } else {
                    System.err.println("I couldn't find any .plaing files. Are you in a plaing project folder?")
                    System.err.println("You can create one with: plaing new my-app")
                    System.exit(1)
                }
                return
            }
            buildProject(sourceDir)
        }
        "parse" -> {
            if (args.size < 2) {
                System.err.println("Please provide a .plaing file to parse, like: plaing parse src/entities.plaing")
                System.exit(1)
            }
            val file = File(args[1])
            if (!file.exists()) {
                System.err.println("I couldn't find the file \"${args[1]}\". Check that the path is correct.")
                System.exit(1)
            }
            parseFile(file)
        }
        else -> {
            System.err.println("I don't know the command \"${args[0]}\".")
            println()
            println("Available commands:")
            println("  plaing new <name>    Create a new project")
            println("  plaing build         Build the project")
            println("  plaing dev           Start dev server")
            println("  plaing parse <file>  Parse and inspect a file")
            System.exit(1)
        }
    }
}

private fun buildProject(sourceDir: File) {
    val plaingFiles = sourceDir.walkTopDown().filter { it.extension == "plaing" }.toList()

    if (plaingFiles.isEmpty()) {
        System.err.println("No .plaing files found in ${sourceDir.absolutePath}")
        System.exit(1)
    }

    val allDeclarations = mutableListOf<Declaration>()
    for (f in plaingFiles) {
        try {
            val tokens = Lexer(f.readText(), f.name).tokenize()
            val program = Parser(tokens, f.name).parse()
            allDeclarations.addAll(program.declarations)
        } catch (e: LexerException) {
            System.err.println(ErrorFormatter.formatLexerError(e, f))
            System.exit(1)
        } catch (e: ParseException) {
            System.err.println(ErrorFormatter.formatParseError(e, f))
            System.exit(1)
        }
    }

    val combinedProgram = PlaingProgram(allDeclarations)
    val outputDir = File(sourceDir.parentFile ?: sourceDir, "build/generated/plaing")
    val generator = CodeGenerator(outputDir)
    val result = generator.generate(combinedProgram)

    println("Build successful!")
    println("  ${result.entityCount} entity(s), ${result.generatedFiles.size} file(s) generated")
    println("  Output: ${outputDir.absolutePath}")
}

private fun parseFile(file: File) {
    try {
        val source = file.readText()
        val tokens = Lexer(source, file.name).tokenize()
        val program = Parser(tokens, file.name).parse()
        println("Parsed ${program.declarations.size} declaration(s) from ${file.name}:")
        for (decl in program.declarations) {
            println("  - $decl")
        }
    } catch (e: LexerException) {
        System.err.println(ErrorFormatter.formatLexerError(e, file))
        System.exit(1)
    } catch (e: ParseException) {
        System.err.println(ErrorFormatter.formatParseError(e, file))
        System.exit(1)
    }
}
