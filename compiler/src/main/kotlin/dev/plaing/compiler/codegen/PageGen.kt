package dev.plaing.compiler.codegen

import dev.plaing.compiler.parser.*

class PageGen {
    fun generate(page: PageDeclaration, packageName: String): String {
        val boundFields = collectBoundFields(page.body)

        val sb = StringBuilder()
        sb.appendLine("package $packageName")
        sb.appendLine()
        sb.appendLine("import androidx.compose.foundation.layout.*")
        sb.appendLine("import androidx.compose.material3.*")
        sb.appendLine("import androidx.compose.runtime.*")
        sb.appendLine("import androidx.compose.ui.Alignment")
        sb.appendLine("import androidx.compose.ui.Modifier")
        sb.appendLine("import androidx.compose.ui.unit.dp")
        sb.appendLine("import dev.plaing.runtime.ui.*")
        sb.appendLine("import dev.plaing.runtime.state.StateStore")
        sb.appendLine("import kotlinx.serialization.json.buildJsonObject")
        sb.appendLine("import kotlinx.serialization.json.put")
        sb.appendLine()
        sb.appendLine("@Composable")
        sb.appendLine("fun ${page.name}(")
        sb.appendLine("    stateStore: StateStore,")
        sb.appendLine("    onEvent: (String, kotlinx.serialization.json.JsonObject) -> Unit,")
        sb.appendLine("    modifier: Modifier = Modifier,")
        sb.appendLine(") {")

        // State variables for bound fields
        for (field in boundFields) {
            sb.appendLine("    var $field by remember { mutableStateOf(\"\") }")
        }
        if (boundFields.isNotEmpty()) sb.appendLine()

        // Generate UI tree
        for (element in page.body) {
            generateElement(sb, element, "    ", boundFields)
        }

        // Alert at the end
        sb.appendLine()
        sb.appendLine("    PlaingAlert(stateStore.alertMessage)")

        sb.appendLine("}")
        return sb.toString()
    }

    private fun generateElement(sb: StringBuilder, element: UiElement, indent: String, boundFields: Set<String>) {
        when (element) {
            is LayoutElement -> {
                sb.appendLine("${indent}Column(")
                sb.appendLine("${indent}    modifier = modifier.fillMaxSize().padding(16.dp),")
                sb.appendLine("${indent}    horizontalAlignment = Alignment.CenterHorizontally,")
                sb.appendLine("${indent}) {")
                for (child in element.children) {
                    generateElement(sb, child, "$indent    ", boundFields)
                }
                sb.appendLine("${indent}}")
            }
            is HeadingElement -> {
                sb.appendLine("${indent}PlaingHeading(\"${element.text}\")")
            }
            is FormElement -> {
                sb.appendLine("${indent}Column(")
                sb.appendLine("${indent}    modifier = Modifier.widthIn(max = 400.dp),")
                sb.appendLine("${indent}) {")
                for (child in element.children) {
                    generateElement(sb, child, "$indent    ", boundFields)
                }
                sb.appendLine("${indent}}")
            }
            is InputElement -> {
                val placeholder = element.properties.filterIsInstance<InputProperty.Placeholder>().firstOrNull()?.text ?: ""
                val isSecret = element.properties.filterIsInstance<InputProperty.Type>().any { it.typeName == "secret" }
                val bindField = element.properties.filterIsInstance<InputProperty.BindsTo>().firstOrNull()?.field
                val stateVar = bindField ?: element.name

                sb.appendLine("${indent}PlaingInput(")
                sb.appendLine("${indent}    value = $stateVar,")
                sb.appendLine("${indent}    onValueChange = { $stateVar = it },")
                sb.appendLine("${indent}    placeholder = \"$placeholder\",")
                if (isSecret) {
                    sb.appendLine("${indent}    isSecret = true,")
                }
                sb.appendLine("${indent})")
            }
            is ButtonElement -> {
                sb.appendLine("${indent}PlaingButton(")
                sb.appendLine("${indent}    text = \"${element.text}\",")
                if (element.action != null) {
                    val action = element.action
                    sb.appendLine("${indent}    onClick = {")
                    sb.appendLine("${indent}        onEvent(\"${action.eventName}\", buildJsonObject {")
                    for (arg in action.arguments) {
                        sb.appendLine("${indent}            put(\"$arg\", $arg)")
                    }
                    sb.appendLine("${indent}        })")
                    sb.appendLine("${indent}    },")
                } else {
                    sb.appendLine("${indent}    onClick = {},")
                }
                sb.appendLine("${indent})")
            }
        }
    }

    private fun collectBoundFields(elements: List<UiElement>): Set<String> {
        val fields = mutableSetOf<String>()
        for (element in elements) {
            when (element) {
                is InputElement -> {
                    val bindField = element.properties.filterIsInstance<InputProperty.BindsTo>().firstOrNull()?.field
                    if (bindField != null) fields.add(bindField)
                }
                is LayoutElement -> fields.addAll(collectBoundFields(element.children))
                is FormElement -> fields.addAll(collectBoundFields(element.children))
                else -> {}
            }
        }
        return fields
    }
}
