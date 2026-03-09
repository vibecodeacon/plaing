package dev.plaing.compiler.codegen

class KotlinDataClassGenerator {
    fun generate(entity: AnalyzedEntity, packageName: String): String {
        val sb = StringBuilder()
        sb.appendLine("package $packageName")
        sb.appendLine()
        sb.appendLine("data class ${entity.declaration.name}(")

        // id always first
        sb.appendLine("    val id: Long = 0,")

        // Separate fields into those without defaults (first) and those with defaults (after)
        val fieldsNoDefault = entity.storedFields.filter { TypeMapper.getDefault(it) == null && !TypeMapper.isNullable(it) }
        val fieldsWithDefault = entity.storedFields.filter { TypeMapper.getDefault(it) != null || TypeMapper.isNullable(it) }

        for (field in fieldsNoDefault) {
            val propName = TypeMapper.toKotlinPropertyName(field)
            val kotlinType = TypeMapper.toKotlinType(field.type)
            sb.appendLine("    val $propName: $kotlinType,")
        }

        for (field in fieldsWithDefault) {
            val propName = TypeMapper.toKotlinPropertyName(field)
            val nullable = TypeMapper.isNullable(field)
            val kotlinType = TypeMapper.toKotlinType(field.type, nullable)
            val default = TypeMapper.getDefault(field)
            val defaultStr = when {
                default != null -> " = ${TypeMapper.defaultValueToKotlin(default)}"
                nullable -> " = null"
                else -> ""
            }
            sb.appendLine("    val $propName: $kotlinType$defaultStr,")
        }

        sb.appendLine(")")
        return sb.toString()
    }
}
