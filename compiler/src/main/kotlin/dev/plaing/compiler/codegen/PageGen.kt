package dev.plaing.compiler.codegen

import dev.plaing.compiler.parser.*

class PageGen {
    fun generate(page: PageDeclaration, packageName: String, styleNames: Set<String> = emptySet()): String {
        val boundFields = collectBoundFields(page.body)
        val hasStyles = styleNames.isNotEmpty()

        val sb = StringBuilder()
        sb.appendLine("package $packageName")
        sb.appendLine()
        sb.appendLine("import androidx.compose.foundation.layout.*")
        sb.appendLine("import androidx.compose.foundation.lazy.*")
        sb.appendLine("import androidx.compose.material3.*")
        sb.appendLine("import androidx.compose.runtime.*")
        sb.appendLine("import androidx.compose.ui.Alignment")
        sb.appendLine("import androidx.compose.ui.Modifier")
        sb.appendLine("import androidx.compose.ui.unit.dp")
        sb.appendLine("import dev.plaing.runtime.ui.*")
        sb.appendLine("import dev.plaing.runtime.state.StateStore")
        sb.appendLine("import kotlinx.serialization.json.buildJsonObject")
        sb.appendLine("import kotlinx.serialization.json.put")
        sb.appendLine("import kotlinx.serialization.json.jsonPrimitive")
        if (hasStyles) {
            val stylePackage = packageName.substringBeforeLast('.') + ".style"
            sb.appendLine("import ${stylePackage}.*")
        }
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
            generateElement(sb, element, "    ", boundFields, styleNames)
        }

        // Alert at the end
        sb.appendLine()
        sb.appendLine("    PlaingAlert(stateStore.alertMessage)")

        sb.appendLine("}")
        return sb.toString()
    }

    private fun generateElement(sb: StringBuilder, element: UiElement, indent: String, boundFields: Set<String>, styleNames: Set<String>) {
        when (element) {
            is LayoutElement -> {
                val styleFn = styleFunction(element.name, styleNames)
                val modifierChain = if (styleFn != null) {
                    "modifier.fillMaxSize().padding(16.dp).$styleFn()"
                } else {
                    "modifier.fillMaxSize().padding(16.dp)"
                }
                sb.appendLine("${indent}Column(")
                sb.appendLine("${indent}    modifier = $modifierChain,")
                sb.appendLine("${indent}    horizontalAlignment = Alignment.CenterHorizontally,")
                sb.appendLine("${indent}) {")
                for (child in element.children) {
                    generateElement(sb, child, "$indent    ", boundFields, styleNames)
                }
                sb.appendLine("${indent}}")
            }
            is HeadingElement -> {
                sb.appendLine("${indent}PlaingHeading(\"${element.text}\")")
            }
            is FormElement -> {
                val styleFn = styleFunction(element.name, styleNames)
                val modifierChain = if (styleFn != null) {
                    "Modifier.widthIn(max = 400.dp).$styleFn()"
                } else {
                    "Modifier.widthIn(max = 400.dp)"
                }
                sb.appendLine("${indent}Column(")
                sb.appendLine("${indent}    modifier = $modifierChain,")
                sb.appendLine("${indent}) {")
                for (child in element.children) {
                    generateElement(sb, child, "$indent    ", boundFields, styleNames)
                }
                sb.appendLine("${indent}}")
            }
            is InputElement -> {
                val placeholder = element.properties.filterIsInstance<InputProperty.Placeholder>().firstOrNull()?.text ?: ""
                val isSecret = element.properties.filterIsInstance<InputProperty.Type>().any { it.typeName == "secret" }
                val bindField = element.properties.filterIsInstance<InputProperty.BindsTo>().firstOrNull()?.field
                val fillsFrom = element.properties.filterIsInstance<InputProperty.FillsFrom>().firstOrNull()
                val stateVar = bindField ?: element.name

                if (fillsFrom != null) {
                    // Initialize the bound field from the selected entity when it changes
                    sb.appendLine("${indent}LaunchedEffect(stateStore.getSelectedEntity(\"${fillsFrom.entityName}\")) {")
                    sb.appendLine("${indent}    val selected = stateStore.getSelectedEntity(\"${fillsFrom.entityName}\")")
                    sb.appendLine("${indent}    if (selected != null) {")
                    sb.appendLine("${indent}        $stateVar = selected[\"${fillsFrom.fieldName}\"]?.jsonPrimitive?.content ?: \"\"")
                    sb.appendLine("${indent}    }")
                    sb.appendLine("${indent}}")
                }

                sb.appendLine("${indent}PlaingInput(")
                sb.appendLine("${indent}    value = $stateVar,")
                sb.appendLine("${indent}    onValueChange = { $stateVar = it },")
                sb.appendLine("${indent}    placeholder = \"$placeholder\",")
                if (isSecret) {
                    sb.appendLine("${indent}    isSecret = true,")
                }
                sb.appendLine("${indent})")
            }
            is TextElement -> {
                val textValue = generateTextValue(element.value)
                sb.appendLine("${indent}PlaingText($textValue)")
            }
            is ListElement -> {
                sb.appendLine("${indent}val ${element.name} = stateStore.getEntityList(\"${element.entityName}\")")
                sb.appendLine("${indent}LazyColumn {")
                sb.appendLine("${indent}    items(${element.name}) { item ->")
                sb.appendLine("${indent}        PlaingListItem(")
                sb.appendLine("${indent}            fields = mapOf(")
                for (field in element.fields) {
                    sb.appendLine("${indent}                \"$field\" to (item[\"$field\"]?.jsonPrimitive?.content ?: \"\"),")
                }
                sb.appendLine("${indent}            ),")
                if (element.onClickSelect != null) {
                    sb.appendLine("${indent}            onClick = { stateStore.selectEntity(\"${element.onClickSelect}\", item) },")
                }
                sb.appendLine("${indent}        )")
                sb.appendLine("${indent}    }")
                sb.appendLine("${indent}}")
            }
            is ButtonElement -> {
                val styleFn = styleFunction(element.text.lowercase().replace(" ", "-"), styleNames)
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
                if (styleFn != null) {
                    sb.appendLine("${indent}    modifier = Modifier.$styleFn(),")
                }
                sb.appendLine("${indent})")
            }
        }
    }

    /**
     * Convert a hyphenated element name (e.g. "login-form") to the corresponding
     * style function name (e.g. "loginFormStyle") if a matching style exists.
     */
    private fun styleFunction(elementName: String, styleNames: Set<String>): String? {
        if (elementName !in styleNames) return null
        return elementName.split("-").mapIndexed { i, part ->
            if (i == 0) part.lowercase() else part.replaceFirstChar { it.uppercase() }
        }.joinToString("") + "Style"
    }

    private fun generateTextValue(expr: Expression): String = when (expr) {
        is StringLiteral -> "\"${expr.value}\""
        is DotAccess -> {
            val target = expr.target
            if (target is Identifier) {
                "stateStore.getEntity(\"${target.name}\")?.get(\"${expr.field}\")?.jsonPrimitive?.content ?: \"\""
            } else {
                "\"\""
            }
        }
        is Identifier -> "stateStore.getEntity(\"${expr.name}\")?.toString() ?: \"\""
        else -> "\"\""
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
