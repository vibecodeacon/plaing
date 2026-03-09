package dev.plaing.runtime.server

import dev.plaing.runtime.EventBus
import dev.plaing.runtime.EventHandler
import dev.plaing.runtime.db.DatabaseManager
import dev.plaing.runtime.session.SessionManager

class PlaingServer(
    private val port: Int = 8080,
    private val dbUrl: String = "jdbc:sqlite:plaing.db",
) {
    val db = DatabaseManager(dbUrl)
    val eventBus = EventBus()
    val sessions = SessionManager()
    private var wsServer: WsServer? = null

    fun createTables(vararg schemas: String) {
        for (schema in schemas) {
            db.executeSql(schema)
        }
    }

    fun registerHandler(eventName: String, handler: EventHandler) {
        eventBus.register(eventName, handler)
    }

    fun start(wait: Boolean = true) {
        println("[plaing] Server starting on port $port...")
        wsServer = WsServer(eventBus, port)
        wsServer!!.start(wait)
    }

    fun stop() {
        wsServer?.stop()
        db.close()
    }
}
