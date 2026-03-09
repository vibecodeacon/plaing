package dev.plaing.compiler.codegen

import dev.plaing.compiler.parser.*

class ClientAppGen {
    fun generate(
        pages: List<PageDeclaration>,
        reactions: List<ReactionDeclaration>,
        packageName: String,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("package $packageName")
        sb.appendLine()
        sb.appendLine("import androidx.compose.runtime.*")
        sb.appendLine("import androidx.compose.ui.window.Window")
        sb.appendLine("import androidx.compose.ui.window.application")
        sb.appendLine("import dev.plaing.runtime.client.WsClient")
        sb.appendLine("import dev.plaing.runtime.state.StateStore")
        sb.appendLine("import ${packageName}.ui.*")
        sb.appendLine("import ${packageName}.reaction.handleReaction")
        sb.appendLine("import kotlinx.coroutines.*")
        sb.appendLine("import kotlinx.serialization.json.JsonObject")
        sb.appendLine()
        sb.appendLine("fun main() = application {")
        sb.appendLine("    val stateStore = remember { StateStore() }")
        sb.appendLine("    val wsClient = remember { WsClient() }")
        sb.appendLine("    val scope = rememberCoroutineScope()")
        sb.appendLine()

        // Set initial page
        val firstPage = pages.firstOrNull()?.name ?: "MainPage"
        sb.appendLine("    LaunchedEffect(Unit) {")
        sb.appendLine("        stateStore.currentPage = \"$firstPage\"")
        sb.appendLine()
        sb.appendLine("        // Connect to server")
        sb.appendLine("        try {")
        sb.appendLine("            wsClient.connect(scope)")
        sb.appendLine("        } catch (e: Exception) {")
        sb.appendLine("            println(\"[plaing] Could not connect to server: \${e.message}\")")
        sb.appendLine("        }")
        sb.appendLine()
        sb.appendLine("        // Listen for incoming events")
        sb.appendLine("        wsClient.incoming.collect { envelope ->")
        sb.appendLine("            handleReaction(envelope, stateStore)")
        sb.appendLine("        }")
        sb.appendLine("    }")
        sb.appendLine()
        sb.appendLine("    val sendEvent: (String, JsonObject) -> Unit = { eventName, payload ->")
        sb.appendLine("        scope.launch {")
        sb.appendLine("            wsClient.send(eventName, payload)")
        sb.appendLine("        }")
        sb.appendLine("    }")
        sb.appendLine()
        sb.appendLine("    Window(")
        sb.appendLine("        onCloseRequest = {")
        sb.appendLine("            wsClient.disconnect()")
        sb.appendLine("            exitApplication()")
        sb.appendLine("        },")
        sb.appendLine("        title = \"Plaing App\",")
        sb.appendLine("    ) {")
        sb.appendLine("        androidx.compose.material3.MaterialTheme {")

        // Page routing
        sb.appendLine("            when (stateStore.currentPage) {")
        for (page in pages) {
            sb.appendLine("                \"${page.name}\" -> ${page.name}(stateStore, sendEvent)")
        }
        sb.appendLine("                else -> {}")
        sb.appendLine("            }")

        sb.appendLine("        }")
        sb.appendLine("    }")
        sb.appendLine("}")
        return sb.toString()
    }
}
