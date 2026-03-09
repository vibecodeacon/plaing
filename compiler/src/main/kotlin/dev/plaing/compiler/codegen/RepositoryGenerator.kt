package dev.plaing.compiler.codegen

import dev.plaing.compiler.parser.*

class RepositoryGenerator {
    fun generate(entity: AnalyzedEntity, modelPackage: String, dbPackage: String): String {
        val name = entity.declaration.name
        val className = "${name}Repository"
        val columns = entity.storedFields.map { TypeMapper.toColumnName(it) }
        val allColumns = listOf("id") + columns
        val columnList = allColumns.joinToString(", ")

        val sb = StringBuilder()
        sb.appendLine("package $dbPackage")
        sb.appendLine()
        sb.appendLine("import $modelPackage.$name")
        sb.appendLine("import java.sql.Connection")
        sb.appendLine("import java.sql.ResultSet")
        sb.appendLine("import java.sql.Statement")
        sb.appendLine("import java.sql.Types")
        sb.appendLine()
        sb.appendLine("class $className(private val conn: Connection) {")
        sb.appendLine()

        // findById
        sb.appendLine("    fun findById(id: Long): $name? {")
        sb.appendLine("        val stmt = conn.prepareStatement(\"SELECT $columnList FROM $name WHERE id = ?\")")
        sb.appendLine("        stmt.setLong(1, id)")
        sb.appendLine("        val rs = stmt.executeQuery()")
        sb.appendLine("        return if (rs.next()) mapRow(rs) else null")
        sb.appendLine("    }")
        sb.appendLine()

        // findWhere
        sb.appendLine("    fun findWhere(vararg conditions: Pair<String, Any?>): $name? {")
        sb.appendLine("        if (conditions.isEmpty()) return null")
        sb.appendLine("        val where = conditions.joinToString(\" AND \") { \"\${it.first} = ?\" }")
        sb.appendLine("        val sql = \"SELECT $columnList FROM $name WHERE \$where\"")
        sb.appendLine("        val stmt = conn.prepareStatement(sql)")
        sb.appendLine("        conditions.forEachIndexed { i, (_, v) -> setParam(stmt, i + 1, v) }")
        sb.appendLine("        val rs = stmt.executeQuery()")
        sb.appendLine("        return if (rs.next()) mapRow(rs) else null")
        sb.appendLine("    }")
        sb.appendLine()

        // findAllWhere
        sb.appendLine("    fun findAllWhere(vararg conditions: Pair<String, Any?>): List<$name> {")
        sb.appendLine("        val list = mutableListOf<$name>()")
        sb.appendLine("        val where = if (conditions.isEmpty()) \"\" else \" WHERE \" + conditions.joinToString(\" AND \") { \"\${it.first} = ?\" }")
        sb.appendLine("        val sql = \"SELECT $columnList FROM $name\$where\"")
        sb.appendLine("        val stmt = conn.prepareStatement(sql)")
        sb.appendLine("        conditions.forEachIndexed { i, (_, v) -> setParam(stmt, i + 1, v) }")
        sb.appendLine("        val rs = stmt.executeQuery()")
        sb.appendLine("        while (rs.next()) list.add(mapRow(rs))")
        sb.appendLine("        return list")
        sb.appendLine("    }")
        sb.appendLine()

        // create
        generateCreate(sb, entity, name, columns)

        // updateWhere
        sb.appendLine("    fun updateWhere(assignments: Map<String, Any?>, vararg conditions: Pair<String, Any?>): Int {")
        sb.appendLine("        val setClauses = assignments.entries.joinToString(\", \") { \"\${it.key} = ?\" }")
        sb.appendLine("        val where = if (conditions.isEmpty()) \"\" else \" WHERE \" + conditions.joinToString(\" AND \") { \"\${it.first} = ?\" }")
        sb.appendLine("        val sql = \"UPDATE $name SET \$setClauses\$where\"")
        sb.appendLine("        val stmt = conn.prepareStatement(sql)")
        sb.appendLine("        var idx = 1")
        sb.appendLine("        assignments.values.forEach { setParam(stmt, idx++, it) }")
        sb.appendLine("        conditions.forEach { setParam(stmt, idx++, it.second) }")
        sb.appendLine("        return stmt.executeUpdate()")
        sb.appendLine("    }")
        sb.appendLine()

        // deleteWhere
        sb.appendLine("    fun deleteWhere(vararg conditions: Pair<String, Any?>): Int {")
        sb.appendLine("        val where = if (conditions.isEmpty()) \"\" else \" WHERE \" + conditions.joinToString(\" AND \") { \"\${it.first} = ?\" }")
        sb.appendLine("        val sql = \"DELETE FROM $name\$where\"")
        sb.appendLine("        val stmt = conn.prepareStatement(sql)")
        sb.appendLine("        conditions.forEachIndexed { i, (_, v) -> setParam(stmt, i + 1, v) }")
        sb.appendLine("        return stmt.executeUpdate()")
        sb.appendLine("    }")
        sb.appendLine()

        // setParam helper
        sb.appendLine("    private fun setParam(stmt: java.sql.PreparedStatement, index: Int, value: Any?) {")
        sb.appendLine("        when (value) {")
        sb.appendLine("            null -> stmt.setNull(index, Types.NULL)")
        sb.appendLine("            is String -> stmt.setString(index, value)")
        sb.appendLine("            is Long -> stmt.setLong(index, value)")
        sb.appendLine("            is Int -> stmt.setInt(index, value)")
        sb.appendLine("            is Double -> stmt.setDouble(index, value)")
        sb.appendLine("            is Boolean -> stmt.setInt(index, if (value) 1 else 0)")
        sb.appendLine("            else -> stmt.setObject(index, value)")
        sb.appendLine("        }")
        sb.appendLine("    }")
        sb.appendLine()

        // mapRow
        generateMapRow(sb, entity, name)

        sb.appendLine("}")
        return sb.toString()
    }

    private fun generateCreate(sb: StringBuilder, entity: AnalyzedEntity, name: String, columns: List<String>) {
        val insertColumns = columns.joinToString(", ")
        val placeholders = columns.joinToString(", ") { "?" }

        sb.appendLine("    fun create(entity: $name): $name {")
        sb.appendLine("        val sql = \"INSERT INTO $name ($insertColumns) VALUES ($placeholders)\"")
        sb.appendLine("        val stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)")

        entity.storedFields.forEachIndexed { i, field ->
            val propName = TypeMapper.toKotlinPropertyName(field)
            val setter = getSetterCall(field.type, i + 1, "entity.$propName")
            sb.appendLine("        $setter")
        }

        sb.appendLine("        stmt.executeUpdate()")
        sb.appendLine("        val keys = stmt.generatedKeys")
        sb.appendLine("        val id = if (keys.next()) keys.getLong(1) else entity.id")
        sb.appendLine("        return entity.copy(id = id)")
        sb.appendLine("    }")
        sb.appendLine()
    }

    private fun getSetterCall(type: PlaingType, paramIndex: Int, valueExpr: String): String {
        return when (type) {
            is PlaingType.TextType -> "stmt.setString($paramIndex, $valueExpr)"
            is PlaingType.NumberType -> "stmt.setDouble($paramIndex, $valueExpr)"
            is PlaingType.BooleanType -> "stmt.setInt($paramIndex, if ($valueExpr) 1 else 0)"
            is PlaingType.DateType -> "stmt.setLong($paramIndex, $valueExpr)"
            is PlaingType.EntityRef -> "stmt.setLong($paramIndex, $valueExpr)"
            is PlaingType.OptionalType -> "if ($valueExpr != null) { ${getSetterCall(type.innerType, paramIndex, valueExpr)} } else { stmt.setNull($paramIndex, Types.NULL) }"
            is PlaingType.ListType -> error("ListType should not be in storedFields")
        }
    }

    private fun generateMapRow(sb: StringBuilder, entity: AnalyzedEntity, name: String) {
        sb.appendLine("    private fun mapRow(rs: ResultSet): $name {")
        sb.appendLine("        return $name(")
        sb.appendLine("            id = rs.getLong(\"id\"),")

        for (field in entity.storedFields) {
            val columnName = TypeMapper.toColumnName(field)
            val propName = TypeMapper.toKotlinPropertyName(field)
            val getter = getResultSetGetter(field.type, columnName)
            sb.appendLine("            $propName = $getter,")
        }

        sb.appendLine("        )")
        sb.appendLine("    }")
    }

    private fun getResultSetGetter(type: PlaingType, columnName: String): String {
        return when (type) {
            is PlaingType.TextType -> "rs.getString(\"$columnName\")"
            is PlaingType.NumberType -> "rs.getDouble(\"$columnName\")"
            is PlaingType.BooleanType -> "rs.getInt(\"$columnName\") != 0"
            is PlaingType.DateType -> "rs.getLong(\"$columnName\")"
            is PlaingType.EntityRef -> "rs.getLong(\"$columnName\")"
            is PlaingType.OptionalType -> {
                val inner = getResultSetGetter(type.innerType, columnName)
                "($inner).let { if (rs.wasNull()) null else it }"
            }
            is PlaingType.ListType -> error("ListType should not be in storedFields")
        }
    }
}
