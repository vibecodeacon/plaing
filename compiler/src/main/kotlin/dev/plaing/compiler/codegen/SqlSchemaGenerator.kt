package dev.plaing.compiler.codegen

import dev.plaing.compiler.parser.*

class SqlSchemaGenerator {
    fun generateCreateTable(entity: AnalyzedEntity): String {
        val sb = StringBuilder()
        sb.appendLine("CREATE TABLE IF NOT EXISTS ${entity.declaration.name} (")
        sb.append("    id INTEGER PRIMARY KEY AUTOINCREMENT")

        for (field in entity.storedFields) {
            sb.appendLine(",")
            val columnName = TypeMapper.toColumnName(field)
            val sqlType = TypeMapper.toSqlType(field.type)
            val nullable = TypeMapper.isNullable(field)

            sb.append("    $columnName $sqlType")

            if (!nullable) {
                sb.append(" NOT NULL")
            }

            if (TypeMapper.isUnique(field)) {
                sb.append(" UNIQUE")
            }

            val default = TypeMapper.getDefault(field)
            if (default != null) {
                sb.append(" DEFAULT ${TypeMapper.defaultValueToSql(default)}")
            }

            // Foreign key reference
            if (field.type is PlaingType.EntityRef) {
                val refName = (field.type as PlaingType.EntityRef).name
                sb.append(" REFERENCES $refName(id)")
            }
        }

        sb.appendLine()
        sb.appendLine(");")
        return sb.toString()
    }

    fun generateAllSchemas(entities: List<AnalyzedEntity>): String {
        return entities.joinToString("\n") { generateCreateTable(it) }
    }
}
