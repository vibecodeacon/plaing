package dev.plaing.compiler.codegen

import dev.plaing.compiler.parser.*

object TypeMapper {
    fun toKotlinType(type: PlaingType, nullable: Boolean = false): String {
        val base = when (type) {
            is PlaingType.TextType -> "String"
            is PlaingType.NumberType -> "Double"
            is PlaingType.BooleanType -> "Boolean"
            is PlaingType.DateType -> "Long"
            is PlaingType.EntityRef -> "Long" // foreign key
            is PlaingType.ListType -> "List<${toKotlinType(type.elementType)}>"
            is PlaingType.OptionalType -> return "${toKotlinType(type.innerType)}?"
        }
        return if (nullable) "$base?" else base
    }

    fun toSqlType(type: PlaingType): String = when (type) {
        is PlaingType.TextType -> "TEXT"
        is PlaingType.NumberType -> "REAL"
        is PlaingType.BooleanType -> "INTEGER"
        is PlaingType.DateType -> "INTEGER"
        is PlaingType.EntityRef -> "INTEGER"
        is PlaingType.OptionalType -> toSqlType(type.innerType)
        is PlaingType.ListType -> error("ListType fields are not stored as columns")
    }

    fun isStoredInTable(field: FieldDefinition): Boolean = field.type !is PlaingType.ListType

    fun toColumnName(field: FieldDefinition): String = when (field.type) {
        is PlaingType.EntityRef -> "${field.name}_id"
        else -> field.name
    }

    fun toKotlinPropertyName(field: FieldDefinition): String {
        val camelName = snakeToCamel(field.name)
        return when (field.type) {
            is PlaingType.EntityRef -> "${camelName}Id"
            else -> camelName
        }
    }

    fun snakeToCamel(name: String): String {
        return name.split('_').mapIndexed { index, part ->
            if (index == 0) part.lowercase()
            else part.replaceFirstChar { it.uppercase() }
        }.joinToString("")
    }

    fun defaultValueToKotlin(expr: Expression): String = when (expr) {
        is StringLiteral -> "\"${expr.value}\""
        is NumberLiteral -> if (expr.value == expr.value.toLong().toDouble()) "${expr.value.toLong()}" else "${expr.value}"
        is BooleanLiteral -> "${expr.value}"
        is NowLiteral -> "System.currentTimeMillis()"
        is Identifier -> expr.name
        else -> error("Unsupported default value: $expr")
    }

    fun defaultValueToSql(expr: Expression): String = when (expr) {
        is StringLiteral -> "'${expr.value}'"
        is NumberLiteral -> if (expr.value == expr.value.toLong().toDouble()) "${expr.value.toLong()}" else "${expr.value}"
        is BooleanLiteral -> if (expr.value) "1" else "0"
        is NowLiteral -> "(strftime('%s','now') * 1000)"
        else -> error("Unsupported SQL default value: $expr")
    }

    fun isNullable(field: FieldDefinition): Boolean = field.type is PlaingType.OptionalType

    fun isRequired(field: FieldDefinition): Boolean = field.modifiers.any { it is FieldModifier.Required }

    fun isUnique(field: FieldDefinition): Boolean = field.modifiers.any { it is FieldModifier.Unique }

    fun isHidden(field: FieldDefinition): Boolean = field.modifiers.any { it is FieldModifier.Hidden }

    fun getDefault(field: FieldDefinition): Expression? =
        field.modifiers.filterIsInstance<FieldModifier.Default>().firstOrNull()?.value
}
