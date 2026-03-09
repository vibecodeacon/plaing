package dev.plaing.compiler.codegen

import dev.plaing.compiler.parser.ComparisonOperator

object ComparisonMapper {
    fun toSql(op: ComparisonOperator): String = when (op) {
        ComparisonOperator.IS -> "="
        ComparisonOperator.IS_NOT -> "!="
        ComparisonOperator.MATCHES -> "="
        ComparisonOperator.IS_AFTER -> ">"
        ComparisonOperator.IS_BEFORE -> "<"
        ComparisonOperator.IS_GREATER_THAN -> ">"
        ComparisonOperator.IS_LESS_THAN -> "<"
        ComparisonOperator.CONTAINS -> "LIKE"
        ComparisonOperator.STARTS_WITH -> "LIKE"
    }

    fun transformValue(op: ComparisonOperator, value: Any?): Any? = when (op) {
        ComparisonOperator.CONTAINS -> "%$value%"
        ComparisonOperator.STARTS_WITH -> "$value%"
        else -> value
    }

    fun toKotlinOperator(op: ComparisonOperator): String = when (op) {
        ComparisonOperator.IS -> "=="
        ComparisonOperator.IS_NOT -> "!="
        ComparisonOperator.MATCHES -> "=="
        ComparisonOperator.IS_AFTER -> ">"
        ComparisonOperator.IS_BEFORE -> "<"
        ComparisonOperator.IS_GREATER_THAN -> ">"
        ComparisonOperator.IS_LESS_THAN -> "<"
        ComparisonOperator.CONTAINS -> "contains"
        ComparisonOperator.STARTS_WITH -> "startsWith"
    }
}
