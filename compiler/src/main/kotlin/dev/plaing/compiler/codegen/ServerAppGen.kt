package dev.plaing.compiler.codegen

import dev.plaing.compiler.parser.*

class ServerAppGen {
    fun generate(
        entities: List<EntityDeclaration>,
        handlers: List<HandlerDeclaration>,
        packageName: String,
    ): String {
        val sb = StringBuilder()

        sb.appendLine("package $packageName")
        sb.appendLine()
        sb.appendLine("import ${packageName}.handler.*")
        sb.appendLine("import dev.plaing.runtime.server.PlaingServer")
        sb.appendLine()
        sb.appendLine("fun main() {")
        sb.appendLine("    val server = PlaingServer(port = 8080)")
        sb.appendLine("    server.db.connect()")
        sb.appendLine()

        // Create tables
        if (entities.isNotEmpty()) {
            sb.appendLine("    // Create tables")
            val analyzer = EntityAnalyzer()
            val analyzed = analyzer.analyze(entities)
            val sqlGen = SqlSchemaGenerator()
            sb.appendLine("    server.createTables(")
            for (entity in analyzed) {
                val sql = sqlGen.generateCreateTable(entity).trimEnd()
                sb.appendLine("        \"\"\"$sql\"\"\",")
            }
            sb.appendLine("    )")
            sb.appendLine()
        }

        // Register handlers
        if (handlers.isNotEmpty()) {
            sb.appendLine("    // Register handlers")
            for (handler in handlers) {
                val handlerClassName = EventGen.eventClassName(handler.eventName).removeSuffix("Event") + "Handler"
                sb.appendLine("    server.registerHandler(\"${handler.eventName}\", $handlerClassName(server.db.getConnection()))")
            }
            sb.appendLine()
        }

        // Start server
        sb.appendLine("    // Start server")
        sb.appendLine("    server.start()")
        sb.appendLine("}")

        return sb.toString()
    }
}
