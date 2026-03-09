package dev.plaing.compiler.codegen

import dev.plaing.compiler.parser.*

class HandlerGen {
    // Track which entity variables have been created in scope
    // Maps entity name -> whether it was a "find all" (true = list, false = single)
    private val entityVars = mutableMapOf<String, Boolean>()

    // Counter for unique statement variable names
    private var stmtCounter = 0

    fun generate(handler: HandlerDeclaration, eventDeclarations: Map<String, EventDeclaration>, packageName: String): String {
        entityVars.clear()
        stmtCounter = 0
        val eventClassName = EventGen.eventClassName(handler.eventName)
        val handlerClassName = eventClassName.removeSuffix("Event") + "Handler"

        val sb = StringBuilder()
        sb.appendLine("package $packageName")
        sb.appendLine()
        sb.appendLine("import dev.plaing.generated.event.*")
        sb.appendLine("import dev.plaing.runtime.*")
        sb.appendLine("import kotlinx.serialization.json.*")
        sb.appendLine()
        sb.appendLine("class $handlerClassName(")
        sb.appendLine("    private val db: java.sql.Connection,")
        sb.appendLine(") : EventHandler {")
        sb.appendLine()
        sb.appendLine("    override suspend fun handle(envelope: EventEnvelope): HandlerResult {")
        sb.appendLine("        val event = $eventClassName.fromPayload(envelope.payload)")
        sb.appendLine("        val emitted = mutableListOf<EventEnvelope>()")
        sb.appendLine()

        for (stmt in handler.body) {
            generateStatement(sb, stmt, "        ")
        }

        sb.appendLine()
        sb.appendLine("        return HandlerResult(emitted)")
        sb.appendLine("    }")
        sb.appendLine()

        // Helper methods
        sb.appendLine("    private fun resultSetToMap(rs: java.sql.ResultSet): Map<String, Any?> {")
        sb.appendLine("        val meta = rs.metaData")
        sb.appendLine("        val map = mutableMapOf<String, Any?>()")
        sb.appendLine("        for (i in 1..meta.columnCount) {")
        sb.appendLine("            map[meta.getColumnName(i)] = rs.getObject(i)")
        sb.appendLine("        }")
        sb.appendLine("        return map")
        sb.appendLine("    }")
        sb.appendLine()
        sb.appendLine("    private fun mapToJsonObject(map: Map<String, Any?>): JsonObject = buildJsonObject {")
        sb.appendLine("        for ((key, value) in map) {")
        sb.appendLine("            when (value) {")
        sb.appendLine("                is String -> put(key, value)")
        sb.appendLine("                is Number -> put(key, value.toLong())")
        sb.appendLine("                is Boolean -> put(key, value)")
        sb.appendLine("                null -> put(key, JsonNull)")
        sb.appendLine("                else -> put(key, value.toString())")
        sb.appendLine("            }")
        sb.appendLine("        }")
        sb.appendLine("    }")

        sb.appendLine("}")
        return sb.toString()
    }

    private fun nextStmtId(): Int = ++stmtCounter

    private fun generateStatement(sb: StringBuilder, stmt: Statement, indent: String) {
        when (stmt) {
            is FindStatement -> generateFind(sb, stmt, indent)
            is CreateStatement -> generateCreate(sb, stmt, indent)
            is UpdateStatement -> generateUpdate(sb, stmt, indent)
            is DeleteStatement -> generateDelete(sb, stmt, indent)
            is EmitStatement -> generateEmit(sb, stmt, indent)
            is IfStatement -> generateIf(sb, stmt, indent)
            is StopStatement -> sb.appendLine("${indent}return HandlerResult(emitted)")
        }
    }

    private fun generateFind(sb: StringBuilder, stmt: FindStatement, indent: String) {
        val varName = stmt.entityName.lowercase()
        val stmtId = nextStmtId()
        val stmtVar = "stmt$stmtId"
        entityVars[stmt.entityName] = stmt.all

        if (stmt.conditions.isEmpty()) {
            sb.appendLine("${indent}val $stmtVar = db.prepareStatement(\"SELECT * FROM ${stmt.entityName}\")")
        } else {
            val whereParts = stmt.conditions.map { cond ->
                "${cond.field} ${ComparisonMapper.toSql(cond.operator)} ?"
            }
            val whereClause = whereParts.joinToString(" AND ")
            sb.appendLine("${indent}val $stmtVar = db.prepareStatement(\"SELECT * FROM ${stmt.entityName} WHERE $whereClause\")")
            stmt.conditions.forEachIndexed { i, cond ->
                val value = generateExpressionValue(cond.value)
                sb.appendLine("${indent}$stmtVar.setObject(${i + 1}, $value)")
            }
        }
        sb.appendLine("${indent}val ${varName}Rs = $stmtVar.executeQuery()")

        if (stmt.all) {
            sb.appendLine("${indent}val ${varName}List = mutableListOf<Map<String, Any?>>()")
            sb.appendLine("${indent}while (${varName}Rs.next()) ${varName}List.add(resultSetToMap(${varName}Rs))")
        } else {
            sb.appendLine("${indent}val $varName: Map<String, Any?>? = if (${varName}Rs.next()) resultSetToMap(${varName}Rs) else null")
        }
        sb.appendLine()
    }

    private fun generateCreate(sb: StringBuilder, stmt: CreateStatement, indent: String) {
        val varName = stmt.entityName.lowercase()
        val stmtId = nextStmtId()
        val stmtVar = "stmt$stmtId"
        entityVars[stmt.entityName] = false

        if (stmt.forEntity != null) {
            val fkColumn = "${stmt.forEntity.lowercase()}_id"
            val fkValue = "${stmt.forEntity.lowercase()}?.get(\"id\")"
            sb.appendLine("${indent}val $stmtVar = db.prepareStatement(\"INSERT INTO ${stmt.entityName} ($fkColumn) VALUES (?)\", java.sql.Statement.RETURN_GENERATED_KEYS)")
            sb.appendLine("${indent}$stmtVar.setObject(1, $fkValue)")
        } else if (stmt.assignments.isNotEmpty()) {
            val columns = stmt.assignments.joinToString(", ") { it.name }
            val placeholders = stmt.assignments.joinToString(", ") { "?" }
            sb.appendLine("${indent}val $stmtVar = db.prepareStatement(\"INSERT INTO ${stmt.entityName} ($columns) VALUES ($placeholders)\", java.sql.Statement.RETURN_GENERATED_KEYS)")
            stmt.assignments.forEachIndexed { i, assign ->
                val value = generateExpressionValue(assign.value)
                sb.appendLine("${indent}$stmtVar.setObject(${i + 1}, $value)")
            }
        } else {
            sb.appendLine("${indent}val $stmtVar = db.prepareStatement(\"INSERT INTO ${stmt.entityName} DEFAULT VALUES\", java.sql.Statement.RETURN_GENERATED_KEYS)")
        }

        sb.appendLine("${indent}$stmtVar.executeUpdate()")
        sb.appendLine("${indent}val ${varName}Keys = $stmtVar.generatedKeys")
        sb.appendLine("${indent}val $varName = mutableMapOf<String, Any?>()")
        sb.appendLine("${indent}if (${varName}Keys.next()) $varName[\"id\"] = ${varName}Keys.getLong(1)")

        for (assign in stmt.assignments) {
            val value = generateExpressionValue(assign.value)
            sb.appendLine("${indent}$varName[\"${assign.name}\"] = $value")
        }
        if (stmt.forEntity != null) {
            val fkValue = "${stmt.forEntity.lowercase()}?.get(\"id\")"
            sb.appendLine("${indent}$varName[\"${stmt.forEntity.lowercase()}_id\"] = $fkValue")
        }
        sb.appendLine()
    }

    private fun generateUpdate(sb: StringBuilder, stmt: UpdateStatement, indent: String) {
        val stmtId = nextStmtId()
        val stmtVar = "stmt$stmtId"
        val setClauses = stmt.assignments.joinToString(", ") { "${it.name} = ?" }
        val sql = if (stmt.conditions.isEmpty()) {
            "UPDATE ${stmt.entityName} SET $setClauses"
        } else {
            val whereParts = stmt.conditions.map { "${it.field} ${ComparisonMapper.toSql(it.operator)} ?" }
            "UPDATE ${stmt.entityName} SET $setClauses WHERE ${whereParts.joinToString(" AND ")}"
        }
        sb.appendLine("${indent}val $stmtVar = db.prepareStatement(\"$sql\")")
        var paramIdx = 1
        for (assign in stmt.assignments) {
            val value = generateExpressionValue(assign.value)
            sb.appendLine("${indent}$stmtVar.setObject($paramIdx, $value)")
            paramIdx++
        }
        for (cond in stmt.conditions) {
            val value = generateExpressionValue(cond.value)
            sb.appendLine("${indent}$stmtVar.setObject($paramIdx, $value)")
            paramIdx++
        }
        sb.appendLine("${indent}$stmtVar.executeUpdate()")
        sb.appendLine()
    }

    private fun generateDelete(sb: StringBuilder, stmt: DeleteStatement, indent: String) {
        val stmtId = nextStmtId()
        val stmtVar = "stmt$stmtId"
        val sql = if (stmt.conditions.isEmpty()) {
            "DELETE FROM ${stmt.entityName}"
        } else {
            val whereParts = stmt.conditions.map { "${it.field} ${ComparisonMapper.toSql(it.operator)} ?" }
            "DELETE FROM ${stmt.entityName} WHERE ${whereParts.joinToString(" AND ")}"
        }
        sb.appendLine("${indent}val $stmtVar = db.prepareStatement(\"$sql\")")
        stmt.conditions.forEachIndexed { i, cond ->
            val value = generateExpressionValue(cond.value)
            sb.appendLine("${indent}$stmtVar.setObject(${i + 1}, $value)")
        }
        sb.appendLine("${indent}$stmtVar.executeUpdate()")
        sb.appendLine()
    }

    private fun generateEmit(sb: StringBuilder, stmt: EmitStatement, indent: String) {
        sb.appendLine("${indent}emitted.add(EventEnvelope(")
        sb.appendLine("${indent}    event = \"${stmt.eventName}\",")
        sb.appendLine("${indent}    payload = buildJsonObject {")
        for (arg in stmt.arguments) {
            val value = generateJsonPutExpression(arg.name, arg.value)
            sb.appendLine("${indent}        $value")
        }
        sb.appendLine("${indent}    },")
        sb.appendLine("${indent}    correlationId = envelope.correlationId,")
        sb.appendLine("${indent}))")
        sb.appendLine()
    }

    private fun generateIf(sb: StringBuilder, stmt: IfStatement, indent: String) {
        val conditionCode = generateCondition(stmt.condition)
        sb.appendLine("${indent}if ($conditionCode) {")
        for (bodyStmt in stmt.body) {
            generateStatement(sb, bodyStmt, "$indent    ")
        }
        if (stmt.otherwise != null) {
            sb.appendLine("${indent}} else {")
            for (elseStmt in stmt.otherwise) {
                generateStatement(sb, elseStmt, "$indent    ")
            }
            sb.appendLine("${indent}}")
        } else {
            sb.appendLine("${indent}}")
        }
        sb.appendLine()
    }

    private fun generateCondition(condition: Condition): String = when (condition) {
        is NoResultCondition -> {
            val isAll = entityVars[condition.entityName] == true
            if (isAll) {
                "${condition.entityName.lowercase()}List.isEmpty()"
            } else {
                "${condition.entityName.lowercase()} == null"
            }
        }
        is ComparisonCondition -> {
            val left = generateExpressionValue(condition.left)
            val right = generateExpressionValue(condition.right)
            when (condition.operator) {
                ComparisonOperator.CONTAINS -> "$left.toString().contains($right.toString())"
                ComparisonOperator.STARTS_WITH -> "$left.toString().startsWith($right.toString())"
                else -> "$left == $right"
            }
        }
    }

    private fun generateExpressionValue(expr: Expression): String = when (expr) {
        is StringLiteral -> "\"${expr.value}\""
        is NumberLiteral -> if (expr.value == expr.value.toLong().toDouble()) "${expr.value.toLong()}" else "${expr.value}"
        is BooleanLiteral -> "${expr.value}"
        is NowLiteral -> "System.currentTimeMillis()"
        is Identifier -> {
            if (expr.name in entityVars) {
                val isAll = entityVars[expr.name] == true
                if (isAll) "${expr.name.lowercase()}List" else expr.name.lowercase()
            } else {
                expr.name
            }
        }
        is DotAccess -> {
            val target = expr.target
            if (target is Identifier) {
                if (target.name in entityVars) {
                    val isAll = entityVars[target.name] == true
                    if (isAll) {
                        // List access doesn't make sense with dot — probably want a single item field
                        "${target.name.lowercase()}List.firstOrNull()?.get(\"${expr.field}\")"
                    } else {
                        "${target.name.lowercase()}?.get(\"${expr.field}\")"
                    }
                } else {
                    "event.${expr.field}"
                }
            } else {
                generateExpressionValue(target) + "?.get(\"${expr.field}\")"
            }
        }
    }

    private fun generateJsonPutExpression(name: String, expr: Expression): String {
        val value = generateExpressionValue(expr)
        return when (expr) {
            is StringLiteral -> "put(\"$name\", $value)"
            is NumberLiteral -> "put(\"$name\", $value)"
            is BooleanLiteral -> "put(\"$name\", $value)"
            is NowLiteral -> "put(\"$name\", $value)"
            is Identifier -> {
                if (expr.name in entityVars) {
                    val isAll = entityVars[expr.name] == true
                    if (isAll) {
                        // Emit list of entities as a JsonArray of JsonObjects
                        "put(\"$name\", JsonArray($value.map { mapToJsonObject(it) }))"
                    } else {
                        // Emit single entity as a JsonObject
                        "put(\"$name\", $value?.let { mapToJsonObject(it) } ?: JsonObject(emptyMap()))"
                    }
                } else {
                    "put(\"$name\", JsonPrimitive($value?.toString() ?: \"\"))"
                }
            }
            is DotAccess -> {
                val target = expr.target
                if (target is Identifier && target.name in entityVars) {
                    // Entity field access: put as primitive
                    "put(\"$name\", JsonPrimitive($value?.toString() ?: \"\"))"
                } else {
                    "put(\"$name\", JsonPrimitive($value?.toString() ?: \"\"))"
                }
            }
            else -> "put(\"$name\", JsonPrimitive($value?.toString() ?: \"\"))"
        }
    }
}
