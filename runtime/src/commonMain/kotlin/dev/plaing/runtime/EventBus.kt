package dev.plaing.runtime

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.JsonObject

/**
 * Event envelope that wraps all events in the system.
 */
data class EventEnvelope(
    val event: String,
    val payload: JsonObject,
    val correlationId: String,
    val sourceSessionId: String? = null,
)

/**
 * Result of handling an event - zero or more events to emit back.
 */
data class HandlerResult(
    val events: List<EventEnvelope> = emptyList(),
)

/**
 * A handler function that processes an event and returns results.
 */
fun interface EventHandler {
    suspend fun handle(envelope: EventEnvelope): HandlerResult
}

/**
 * The event bus routes events to registered handlers and broadcasts responses.
 */
class EventBus {
    private val handlers = mutableMapOf<String, MutableList<EventHandler>>()
    private val _outgoing = MutableSharedFlow<EventEnvelope>(extraBufferCapacity = 64)
    val outgoing: SharedFlow<EventEnvelope> = _outgoing.asSharedFlow()

    fun register(eventName: String, handler: EventHandler) {
        handlers.getOrPut(eventName) { mutableListOf() }.add(handler)
    }

    suspend fun emit(envelope: EventEnvelope) {
        val eventHandlers = handlers[envelope.event]
        if (eventHandlers != null) {
            for (handler in eventHandlers) {
                val result = handler.handle(envelope)
                for (responseEvent in result.events) {
                    // Preserve correlation ID from the original event
                    val response = responseEvent.copy(
                        correlationId = envelope.correlationId,
                        sourceSessionId = envelope.sourceSessionId,
                    )
                    _outgoing.emit(response)
                }
            }
        }
    }
}
