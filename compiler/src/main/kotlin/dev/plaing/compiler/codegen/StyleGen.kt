package dev.plaing.compiler.codegen

import dev.plaing.compiler.parser.*

class StyleGen {
    fun generate(style: StyleDeclaration, packageName: String): String {
        val functionName = style.targetName.split("-").mapIndexed { i, part ->
            if (i == 0) part.lowercase() else part.replaceFirstChar { it.uppercase() }
        }.joinToString("") + "Style"

        val sb = StringBuilder()
        sb.appendLine("package $packageName")
        sb.appendLine()
        sb.appendLine("import androidx.compose.foundation.background")
        sb.appendLine("import androidx.compose.foundation.layout.*")
        sb.appendLine("import androidx.compose.foundation.shape.RoundedCornerShape")
        sb.appendLine("import androidx.compose.ui.Modifier")
        sb.appendLine("import androidx.compose.ui.draw.clip")
        sb.appendLine("import androidx.compose.ui.graphics.Color")
        sb.appendLine("import androidx.compose.ui.unit.dp")
        sb.appendLine("import androidx.compose.ui.unit.sp")
        sb.appendLine()

        sb.appendLine("fun Modifier.$functionName(): Modifier = this")

        for (prop in style.properties) {
            val modifier = mapPropertyToModifier(prop)
            if (modifier != null) {
                sb.appendLine("    $modifier")
            }
        }
        sb.appendLine()

        // Generate text style data class if there are text properties
        val textProps = style.properties.filter { it.name in textPropertyNames }
        if (textProps.isNotEmpty()) {
            sb.appendLine("data class ${functionName.replaceFirstChar { it.uppercase() }}TextStyle(")
            for (prop in textProps) {
                when (prop.name) {
                    "font size" -> sb.appendLine("    val fontSize: androidx.compose.ui.unit.TextUnit = ${parseSp(prop.value)},")
                    "font weight" -> sb.appendLine("    val fontWeight: androidx.compose.ui.text.font.FontWeight = ${parseFontWeight(prop.value)},")
                    "text color" -> sb.appendLine("    val color: Color = ${parseColor(prop.value)},")
                }
            }
            sb.appendLine(")")
        }

        return sb.toString()
    }

    private val textPropertyNames = setOf("font size", "font weight", "text color")

    private fun mapPropertyToModifier(prop: StyleProperty): String? {
        return when (prop.name) {
            "background color" -> ".background(${parseColor(prop.value)})"
            "background" -> ".background(${parseColor(prop.value)})"
            "border radius" -> ".clip(RoundedCornerShape(${parseDp(prop.value)}))"
            "padding" -> {
                val parts = prop.value.split(" ").filter { it.isNotBlank() }
                when (parts.size) {
                    1 -> ".padding(${parseDp(parts[0])})"
                    2 -> ".padding(horizontal = ${parseDp(parts[1])}, vertical = ${parseDp(parts[0])})"
                    4 -> ".padding(start = ${parseDp(parts[3])}, top = ${parseDp(parts[0])}, end = ${parseDp(parts[1])}, bottom = ${parseDp(parts[2])})"
                    else -> ".padding(${parseDp(parts[0])})"
                }
            }
            "max width" -> ".widthIn(max = ${parseDp(prop.value)})"
            "min width" -> ".widthIn(min = ${parseDp(prop.value)})"
            "max height" -> ".heightIn(max = ${parseDp(prop.value)})"
            "min height" -> ".heightIn(min = ${parseDp(prop.value)})"
            "width" -> ".width(${parseDp(prop.value)})"
            "height" -> ".height(${parseDp(prop.value)})"
            "margin bottom" -> ".padding(bottom = ${parseDp(prop.value)})"
            "margin top" -> ".padding(top = ${parseDp(prop.value)})"
            "margin" -> ".padding(${parseDp(prop.value)})"
            "border" -> if (prop.value == "none") null else null // skip complex borders for now
            "cursor" -> null // not applicable in Compose
            "box shadow" -> null // complex, skip for now
            // Text properties handled separately
            "font size", "font weight", "text color" -> null
            else -> "/* TODO: ${prop.name} is ${prop.value} */"
        }
    }

    private fun parseDp(value: String): String {
        val numStr = value.replace("px", "").replace("dp", "").trim()
        return try {
            val num = numStr.toInt()
            "${num}.dp"
        } catch (e: NumberFormatException) {
            try {
                val num = numStr.toDouble()
                "${num}.dp"
            } catch (e: NumberFormatException) {
                "0.dp /* $value */"
            }
        }
    }

    private fun parseSp(value: String): String {
        val numStr = value.replace("px", "").replace("sp", "").trim()
        return try {
            "${numStr.toInt()}.sp"
        } catch (e: NumberFormatException) {
            "14.sp /* $value */"
        }
    }

    private fun parseColor(value: String): String {
        return when (value.lowercase()) {
            "white" -> "Color.White"
            "black" -> "Color.Black"
            "red" -> "Color.Red"
            "green" -> "Color.Green"
            "blue" -> "Color.Blue"
            "gray", "grey" -> "Color.Gray"
            "transparent" -> "Color.Transparent"
            "none" -> "Color.Transparent"
            // Theme colors
            "primary" -> "Color(0xFF6200EE)"
            "primary-dark" -> "Color(0xFF3700B3)"
            "secondary" -> "Color(0xFF03DAC6)"
            "error" -> "Color(0xFFB00020)"
            else -> {
                if (value.startsWith("#")) {
                    "Color(0xFF${value.removePrefix("#")})"
                } else if (value.startsWith("rgba")) {
                    "Color.Gray /* $value */"
                } else {
                    "Color.Unspecified /* $value */"
                }
            }
        }
    }

    private fun parseFontWeight(value: String): String {
        return when (value.lowercase()) {
            "bold" -> "FontWeight.Bold"
            "normal" -> "FontWeight.Normal"
            "light" -> "FontWeight.Light"
            "thin" -> "FontWeight.Thin"
            "medium" -> "FontWeight.Medium"
            "semibold", "semi-bold" -> "FontWeight.SemiBold"
            "extrabold", "extra-bold" -> "FontWeight.ExtraBold"
            else -> {
                try {
                    "FontWeight(${value.toInt()})"
                } catch (e: NumberFormatException) {
                    "FontWeight.Normal /* $value */"
                }
            }
        }
    }
}
