package dev.plaing.compiler.codegen

import dev.plaing.compiler.parser.*

class EventGen {
    fun generate(event: EventDeclaration, packageName: String): String {
        val className = eventClassName(event.name)
        val sb = StringBuilder()

        sb.appendLine("package $packageName")
        sb.appendLine()
        sb.appendLine("import kotlinx.serialization.Serializable")
        sb.appendLine("import kotlinx.serialization.json.*")
        sb.appendLine()
        sb.appendLine("@Serializable")

        if (event.fields.isEmpty()) {
            sb.appendLine("class $className {")
        } else {
            sb.appendLine("data class $className(")
            for ((i, field) in event.fields.withIndex()) {
                val type = TypeMapper.toKotlinType(field.type)
                val comma = if (i < event.fields.size - 1) "," else ","
                sb.appendLine("    val ${field.name}: $type$comma")
            }
            sb.appendLine(") {")
        }

        sb.appendLine("    companion object {")
        sb.appendLine("        const val EVENT_NAME = \"${event.name}\"")
        sb.appendLine()

        // fromPayload
        sb.appendLine("        fun fromPayload(payload: JsonObject): $className {")
        if (event.fields.isEmpty()) {
            sb.appendLine("            return $className()")
        } else {
            sb.appendLine("            return $className(")
            for (field in event.fields) {
                val extractor = jsonExtractor(field.name, field.type)
                sb.appendLine("                ${field.name} = $extractor,")
            }
            sb.appendLine("            )")
        }
        sb.appendLine("        }")

        sb.appendLine("    }")
        sb.appendLine()

        // toPayload
        sb.appendLine("    fun toPayload(): JsonObject = buildJsonObject {")
        for (field in event.fields) {
            val putter = jsonPutter(field.name, field.type)
            sb.appendLine("        $putter")
        }
        sb.appendLine("    }")

        sb.appendLine("}")
        return sb.toString()
    }

    private fun jsonExtractor(name: String, type: PlaingType): String = when (type) {
        is PlaingType.TextType -> "payload[\"$name\"]?.jsonPrimitive?.content ?: \"\""
        is PlaingType.NumberType -> "payload[\"$name\"]?.jsonPrimitive?.double ?: 0.0"
        is PlaingType.BooleanType -> "payload[\"$name\"]?.jsonPrimitive?.boolean ?: false"
        is PlaingType.DateType -> "payload[\"$name\"]?.jsonPrimitive?.long ?: 0L"
        is PlaingType.EntityRef -> "payload[\"$name\"]?.jsonPrimitive?.long ?: 0L"
        else -> "payload[\"$name\"]?.jsonPrimitive?.content ?: \"\""
    }

    private fun jsonPutter(name: String, type: PlaingType): String = when (type) {
        is PlaingType.TextType -> "put(\"$name\", $name)"
        is PlaingType.NumberType -> "put(\"$name\", $name)"
        is PlaingType.BooleanType -> "put(\"$name\", $name)"
        is PlaingType.DateType -> "put(\"$name\", $name)"
        is PlaingType.EntityRef -> "put(\"$name\", $name)"
        else -> "put(\"$name\", $name)"
    }

    companion object {
        fun eventClassName(eventName: String): String {
            // LOGIN_ATTEMPT -> LoginAttemptEvent
            return eventName.split("_").joinToString("") { part ->
                part.lowercase().replaceFirstChar { it.uppercase() }
            } + "Event"
        }
    }
}
