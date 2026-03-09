package dev.plaing.runtime.server

import dev.plaing.runtime.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class WsServer(
    private val eventBus: EventBus,
    private val port: Int = 8080,
) {
    private val sessions = ConcurrentHashMap<String, WebSocketSession>()
    private var server: EmbeddedServer<*, *>? = null

    fun start(wait: Boolean = true) {
        server = embeddedServer(Netty, port = port) {
            install(WebSockets)
            routing {
                webSocket("/ws") {
                    val sessionId = UUID.randomUUID().toString()
                    sessions[sessionId] = this
                    println("[plaing] Client connected: $sessionId")

                    // Listen for outgoing events and send to this client
                    val outgoingJob = launch {
                        eventBus.outgoing
                            .filter { it.sourceSessionId == sessionId || it.sourceSessionId == null }
                            .collect { envelope ->
                                try {
                                    send(Frame.Text(Protocol.encode(envelope)))
                                } catch (e: Exception) {
                                    // Session might be closed
                                }
                            }
                    }

                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                try {
                                    val envelope = Protocol.decode(text, sessionId)
                                    eventBus.emit(envelope)
                                } catch (e: Exception) {
                                    System.err.println("[plaing] Error processing message: ${e.message}")
                                }
                            }
                        }
                    } finally {
                        outgoingJob.cancel()
                        sessions.remove(sessionId)
                        println("[plaing] Client disconnected: $sessionId")
                    }
                }
            }
        }
        server!!.start(wait = wait)
    }

    fun stop() {
        server?.stop(1000, 2000)
    }
}
