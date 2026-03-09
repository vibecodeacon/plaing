package dev.plaing.compiler.codegen

import dev.plaing.compiler.parser.*

class ReactionGen {
    fun generate(reactions: List<ReactionDeclaration>, packageName: String): String {
        val sb = StringBuilder()
        sb.appendLine("package $packageName")
        sb.appendLine()
        sb.appendLine("import dev.plaing.runtime.EventEnvelope")
        sb.appendLine("import dev.plaing.runtime.state.StateStore")
        sb.appendLine("import kotlinx.serialization.json.*")
        sb.appendLine()
        sb.appendLine("fun handleReaction(envelope: EventEnvelope, stateStore: StateStore) {")
        sb.appendLine("    when (envelope.event) {")

        for (reaction in reactions) {
            sb.appendLine("        \"${reaction.eventName}\" -> {")
            for (action in reaction.actions) {
                generateAction(sb, action, "            ")
            }
            sb.appendLine("        }")
        }

        sb.appendLine("    }")
        sb.appendLine("}")
        return sb.toString()
    }

    private fun generateAction(sb: StringBuilder, action: ReactionAction, indent: String) {
        when (action) {
            is StoreAction -> {
                val fromExpr = generateFromExpression(action.from)
                sb.appendLine("${indent}stateStore.storeEntity(\"${action.entityName}\", $fromExpr)")
            }
            is NavigateAction -> {
                sb.appendLine("${indent}stateStore.navigateTo(\"${action.targetPage}\")")
            }
            is ShowAlertAction -> {
                val msgExpr = generateAlertExpression(action.message)
                sb.appendLine("${indent}stateStore.showAlert($msgExpr)")
            }
        }
    }

    private fun generateFromExpression(expr: Expression): String {
        return when (expr) {
            is DotAccess -> {
                val target = expr.target
                if (target is Identifier) {
                    "envelope.payload[\"${expr.field}\"]?.jsonObject ?: buildJsonObject {}"
                } else {
                    "envelope.payload[\"${expr.field}\"]?.jsonObject ?: buildJsonObject {}"
                }
            }
            is Identifier -> "envelope.payload[\"${expr.name}\"]?.jsonObject ?: buildJsonObject {}"
            else -> "buildJsonObject {}"
        }
    }

    private fun generateAlertExpression(expr: Expression): String {
        return when (expr) {
            is DotAccess -> {
                val target = expr.target
                if (target is Identifier) {
                    "envelope.payload[\"${expr.field}\"]?.jsonPrimitive?.content ?: \"\""
                } else {
                    "envelope.payload[\"${expr.field}\"]?.jsonPrimitive?.content ?: \"\""
                }
            }
            is StringLiteral -> "\"${expr.value}\""
            is Identifier -> "envelope.payload[\"${expr.name}\"]?.jsonPrimitive?.content ?: \"\""
            else -> "\"\""
        }
    }
}
