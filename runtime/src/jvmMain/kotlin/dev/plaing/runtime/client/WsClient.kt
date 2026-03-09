package dev.plaing.runtime.client

import dev.plaing.runtime.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.UUID

class WsClient(
    private val host: String = "localhost",
    private val port: Int = 8080,
    private val path: String = "/ws",
) {
    private val client = HttpClient(CIO) {
        install(WebSockets)
    }
    private var session: DefaultClientWebSocketSession? = null
    private var job: Job? = null
    private val _incoming = MutableSharedFlow<EventEnvelope>(extraBufferCapacity = 64)
    val incoming: SharedFlow<EventEnvelope> = _incoming.asSharedFlow()
    private val _connected = Channel<Unit>(1)

    suspend fun connect(scope: CoroutineScope) {
        job = scope.launch {
            client.webSocket(host = host, port = port, path = path) {
                session = this
                _connected.send(Unit)

                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        try {
                            val envelope = Protocol.decode(text)
                            _incoming.emit(envelope)
                        } catch (e: Exception) {
                            System.err.println("[plaing-client] Error: ${e.message}")
                        }
                    }
                }
            }
        }
        _connected.receive()
    }

    suspend fun send(eventName: String, payload: kotlinx.serialization.json.JsonObject = kotlinx.serialization.json.buildJsonObject {}) {
        val envelope = EventEnvelope(
            event = eventName,
            payload = payload,
            correlationId = UUID.randomUUID().toString(),
        )
        session?.send(Frame.Text(Protocol.encode(envelope)))
    }

    fun disconnect() {
        job?.cancel()
        client.close()
    }
}
