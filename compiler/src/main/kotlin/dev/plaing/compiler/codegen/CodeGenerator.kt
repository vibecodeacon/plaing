package dev.plaing.compiler.codegen

import dev.plaing.compiler.parser.*
import java.io.File

class CodeGenerator(private val outputDir: File) {
    private val packageBase = "dev.plaing.generated"
    private val modelPackage = "$packageBase.model"
    private val dbPackage = "$packageBase.db"
    private val eventPackage = "$packageBase.event"
    private val handlerPackage = "$packageBase.handler"

    fun generate(program: PlaingProgram): GenerationResult {
        val entities = program.declarations.filterIsInstance<EntityDeclaration>()
        val events = program.declarations.filterIsInstance<EventDeclaration>()
        val handlers = program.declarations.filterIsInstance<HandlerDeclaration>()
        val generatedFiles = mutableListOf<String>()

        // Generate entity data classes, SQL, repositories, and DB init
        if (entities.isNotEmpty()) {
            val analyzer = EntityAnalyzer()
            val analyzed = analyzer.analyze(entities)

            // Generate data classes
            val dataClassGen = KotlinDataClassGenerator()
            for (entity in analyzed) {
                val code = dataClassGen.generate(entity, modelPackage)
                val path = writeFile("kotlin/${modelPackage.replace('.', '/')}/${entity.declaration.name}.kt", code)
                generatedFiles.add(path)
            }

            // Generate SQL schemas
            val sqlGen = SqlSchemaGenerator()
            for (entity in analyzed) {
                val sql = sqlGen.generateCreateTable(entity)
                val path = writeFile("sql/${entity.declaration.name}.sql", sql)
                generatedFiles.add(path)
            }

            // Generate repositories
            val repoGen = RepositoryGenerator()
            for (entity in analyzed) {
                val code = repoGen.generate(entity, modelPackage, dbPackage)
                val path = writeFile("kotlin/${dbPackage.replace('.', '/')}/${entity.declaration.name}Repository.kt", code)
                generatedFiles.add(path)
            }

            // Generate database initializer
            val dbGen = DatabaseInitGenerator()
            val dbCode = dbGen.generate(analyzed, dbPackage)
            val dbPath = writeFile("kotlin/${dbPackage.replace('.', '/')}/PlaingDatabase.kt", dbCode)
            generatedFiles.add(dbPath)
        }

        // Generate event data classes
        if (events.isNotEmpty()) {
            val eventGen = EventGen()
            for (event in events) {
                val code = eventGen.generate(event, eventPackage)
                val className = EventGen.eventClassName(event.name)
                val path = writeFile("kotlin/${eventPackage.replace('.', '/')}/$className.kt", code)
                generatedFiles.add(path)
            }
        }

        // Generate handler classes
        if (handlers.isNotEmpty()) {
            val handlerGen = HandlerGen()
            val eventMap = events.associateBy { it.name }
            for (handler in handlers) {
                val code = handlerGen.generate(handler, eventMap, handlerPackage)
                val handlerClassName = EventGen.eventClassName(handler.eventName).removeSuffix("Event") + "Handler"
                val path = writeFile("kotlin/${handlerPackage.replace('.', '/')}/$handlerClassName.kt", code)
                generatedFiles.add(path)
            }
        }

        // Generate ServerApp.kt if there are handlers
        if (handlers.isNotEmpty()) {
            val serverAppGen = ServerAppGen()
            val serverCode = serverAppGen.generate(entities, handlers, packageBase)
            val path = writeFile("kotlin/${packageBase.replace('.', '/')}/ServerApp.kt", serverCode)
            generatedFiles.add(path)
        }

        // Collect style names for wiring into pages
        val styles = program.declarations.filterIsInstance<StyleDeclaration>()
        val styleNames = styles.map { it.targetName }.toSet()

        // Generate page composables
        val pages = program.declarations.filterIsInstance<PageDeclaration>()
        if (pages.isNotEmpty()) {
            val pageGen = PageGen()
            for (page in pages) {
                val code = pageGen.generate(page, "$packageBase.ui", styleNames)
                val path = writeFile("kotlin/${packageBase.replace('.', '/')}/ui/${page.name}.kt", code)
                generatedFiles.add(path)
            }
        }

        // Generate style modifiers
        if (styles.isNotEmpty()) {
            val styleGen = StyleGen()
            for (style in styles) {
                val code = styleGen.generate(style, "$packageBase.style")
                val functionName = style.targetName.split("-").mapIndexed { i, part ->
                    if (i == 0) part.lowercase() else part.replaceFirstChar { it.uppercase() }
                }.joinToString("") + "Style"
                val fileName = functionName.replaceFirstChar { it.uppercase() }
                val path = writeFile("kotlin/${packageBase.replace('.', '/')}/style/$fileName.kt", code)
                generatedFiles.add(path)
            }
        }

        // Generate reactions
        val reactions = program.declarations.filterIsInstance<ReactionDeclaration>()
        if (reactions.isNotEmpty()) {
            val reactionGen = ReactionGen()
            val code = reactionGen.generate(reactions, "$packageBase.reaction")
            val path = writeFile("kotlin/${packageBase.replace('.', '/')}/reaction/Reactions.kt", code)
            generatedFiles.add(path)
        }

        // Generate ClientApp.kt if there are pages
        if (pages.isNotEmpty()) {
            val clientAppGen = ClientAppGen()
            val code = clientAppGen.generate(pages, reactions, packageBase)
            val path = writeFile("kotlin/${packageBase.replace('.', '/')}/ClientApp.kt", code)
            generatedFiles.add(path)
        }

        return GenerationResult(entities.size, generatedFiles)
    }

    private fun writeFile(relativePath: String, content: String): String {
        val file = File(outputDir, relativePath)
        file.parentFile.mkdirs()
        file.writeText(content)
        return file.absolutePath
    }
}

data class GenerationResult(
    val entityCount: Int,
    val generatedFiles: List<String>,
)
