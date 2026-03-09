package dev.plaing.compiler.codegen

class DatabaseInitGenerator {
    fun generate(entities: List<AnalyzedEntity>, dbPackage: String): String {
        val sqlGen = SqlSchemaGenerator()

        val sb = StringBuilder()
        sb.appendLine("package $dbPackage")
        sb.appendLine()
        sb.appendLine("import java.sql.Connection")
        sb.appendLine("import java.sql.DriverManager")
        sb.appendLine()
        sb.appendLine("object PlaingDatabase {")
        sb.appendLine("    private var connection: Connection? = null")
        sb.appendLine()
        sb.appendLine("    fun connect(url: String = \"jdbc:sqlite:plaing.db\"): Connection {")
        sb.appendLine("        val conn = DriverManager.getConnection(url)")
        sb.appendLine("        connection = conn")
        sb.appendLine("        createTables(conn)")
        sb.appendLine("        return conn")
        sb.appendLine("    }")
        sb.appendLine()
        sb.appendLine("    fun getConnection(): Connection = connection ?: error(\"Database not connected. Call connect() first.\")")
        sb.appendLine()
        sb.appendLine("    fun close() {")
        sb.appendLine("        connection?.close()")
        sb.appendLine("        connection = null")
        sb.appendLine("    }")
        sb.appendLine()
        sb.appendLine("    private fun createTables(conn: Connection) {")

        for (entity in entities) {
            val sql = sqlGen.generateCreateTable(entity).trimEnd()
            // Escape the SQL for embedding in Kotlin string
            val escapedSql = sql.replace("\"", "\\\"")
            sb.appendLine("        conn.createStatement().execute(\"\"\"")
            sb.appendLine("            $escapedSql")
            sb.appendLine("        \"\"\".trimIndent())")
            sb.appendLine()
        }

        sb.appendLine("    }")
        sb.appendLine("}")
        return sb.toString()
    }
}
