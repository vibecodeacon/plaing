package dev.plaing.runtime

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * Wire format for WebSocket messages.
 */
@Serializable
data class WsMessage(
    val event: String,
    val payload: JsonObject = buildJsonObject {},
    val correlationId: String = "",
)

object Protocol {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(envelope: EventEnvelope): String {
        val msg = WsMessage(
            event = envelope.event,
            payload = envelope.payload,
            correlationId = envelope.correlationId,
        )
        return json.encodeToString(WsMessage.serializer(), msg)
    }

    fun decode(text: String, sessionId: String? = null): EventEnvelope {
        val msg = json.decodeFromString(WsMessage.serializer(), text)
        return EventEnvelope(
            event = msg.event,
            payload = msg.payload,
            correlationId = msg.correlationId,
            sourceSessionId = sessionId,
        )
    }
}
