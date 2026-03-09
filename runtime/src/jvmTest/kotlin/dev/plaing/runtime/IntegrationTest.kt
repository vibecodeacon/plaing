package dev.plaing.runtime

import dev.plaing.runtime.client.WsClient
import dev.plaing.runtime.db.DatabaseManager
import dev.plaing.runtime.server.PlaingServer
import dev.plaing.runtime.session.SessionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IntegrationTest {

    @Test
    fun `event round trip through WebSocket`() = runBlocking {
        // Set up server with a simple echo handler
        val server = PlaingServer(port = 9100, dbUrl = "jdbc:sqlite::memory:")
        server.db.connect()

        server.eventBus.register("PING") { envelope ->
            HandlerResult(listOf(
                EventEnvelope(
                    event = "PONG",
                    payload = buildJsonObject {
                        put("echo", envelope.payload["message"]?.jsonPrimitive?.content ?: "")
                    },
                    correlationId = envelope.correlationId,
                    sourceSessionId = envelope.sourceSessionId,
                )
            ))
        }

        server.start(wait = false)
        delay(500) // let server start

        try {
            val client = WsClient(port = 9100)
            client.connect(this)
            delay(300) // let connection establish

            // Send PING event
            val payload = buildJsonObject { put("message", "hello") }
            client.send("PING", payload)

            // Wait for PONG response
            val response = withTimeout(5000) {
                client.incoming.first { it.event == "PONG" }
            }

            assertEquals("PONG", response.event)
            assertEquals("hello", response.payload["echo"]?.jsonPrimitive?.content)

            client.disconnect()
        } finally {
            server.stop()
        }
    }

    @Test
    fun `login flow with database`() = runBlocking {
        val server = PlaingServer(port = 9101, dbUrl = "jdbc:sqlite::memory:")
        server.db.connect()

        // Create users table
        server.createTables("""
            CREATE TABLE IF NOT EXISTS User (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                email TEXT NOT NULL UNIQUE,
                password TEXT NOT NULL
            );
        """.trimIndent())

        // Insert a test user
        val conn = server.db.getConnection()
        val insertStmt = conn.prepareStatement("INSERT INTO User (name, email, password) VALUES (?, ?, ?)")
        insertStmt.setString(1, "Alice")
        insertStmt.setString(2, "alice@test.com")
        insertStmt.setString(3, "secret123")
        insertStmt.executeUpdate()

        // Register login handler (simulating what HandlerGen would produce)
        server.eventBus.register("LOGIN_ATTEMPT") { envelope ->
            val email = envelope.payload["email"]?.jsonPrimitive?.content ?: ""
            val password = envelope.payload["password"]?.jsonPrimitive?.content ?: ""

            val findStmt = conn.prepareStatement("SELECT * FROM User WHERE email = ?")
            findStmt.setString(1, email)
            val rs = findStmt.executeQuery()

            if (!rs.next()) {
                return@register HandlerResult(listOf(
                    EventEnvelope(
                        event = "LOGIN_FAILURE",
                        payload = buildJsonObject { put("message", "invalid credentials") },
                        correlationId = envelope.correlationId,
                        sourceSessionId = envelope.sourceSessionId,
                    )
                ))
            }

            val dbPassword = rs.getString("password")
            if (dbPassword == password) {
                val session = server.sessions.createSession(
                    userId = rs.getLong("id"),
                    wsSessionId = envelope.sourceSessionId,
                )
                HandlerResult(listOf(
                    EventEnvelope(
                        event = "LOGIN_SUCCESS",
                        payload = buildJsonObject {
                            put("user_name", rs.getString("name"))
                            put("token", session.token)
                        },
                        correlationId = envelope.correlationId,
                        sourceSessionId = envelope.sourceSessionId,
                    )
                ))
            } else {
                HandlerResult(listOf(
                    EventEnvelope(
                        event = "LOGIN_FAILURE",
                        payload = buildJsonObject { put("message", "invalid credentials") },
                        correlationId = envelope.correlationId,
                        sourceSessionId = envelope.sourceSessionId,
                    )
                ))
            }
        }

        server.start(wait = false)
        delay(500)

        try {
            val client = WsClient(port = 9101)
            client.connect(this)
            delay(300)

            // Test successful login
            client.send("LOGIN_ATTEMPT", buildJsonObject {
                put("email", "alice@test.com")
                put("password", "secret123")
            })

            val successResponse = withTimeout(5000) {
                client.incoming.first { it.event == "LOGIN_SUCCESS" }
            }
            assertEquals("LOGIN_SUCCESS", successResponse.event)
            assertEquals("Alice", successResponse.payload["user_name"]?.jsonPrimitive?.content)
            assertNotNull(successResponse.payload["token"]?.jsonPrimitive?.content)

            client.disconnect()

            // Test failed login with new client
            val client2 = WsClient(port = 9101)
            client2.connect(this)
            delay(300)

            client2.send("LOGIN_ATTEMPT", buildJsonObject {
                put("email", "alice@test.com")
                put("password", "wrong_password")
            })

            val failResponse = withTimeout(5000) {
                client2.incoming.first { it.event == "LOGIN_FAILURE" }
            }
            assertEquals("LOGIN_FAILURE", failResponse.event)
            assertEquals("invalid credentials", failResponse.payload["message"]?.jsonPrimitive?.content)

            client2.disconnect()
        } finally {
            server.stop()
        }
    }

    @Test
    fun `session management`() {
        val sessionManager = SessionManager()

        // Create session
        val session = sessionManager.createSession(userId = 1, wsSessionId = "ws-123")
        assertNotNull(session.token)
        assertEquals(1, session.userId)
        assertEquals("ws-123", session.wsSessionId)

        // Retrieve session
        val retrieved = sessionManager.getSession(session.token)
        assertNotNull(retrieved)
        assertEquals(session.token, retrieved.token)

        // Get user sessions
        val userSessions = sessionManager.getUserSessions(1)
        assertEquals(1, userSessions.size)

        // Invalidate
        assertTrue(sessionManager.invalidate(session.token))
        assertEquals(null, sessionManager.getSession(session.token))
    }

    @Test
    fun `database manager connects and executes SQL`() {
        val db = DatabaseManager("jdbc:sqlite::memory:")
        val conn = db.connect()

        db.executeSql("CREATE TABLE test (id INTEGER PRIMARY KEY, value TEXT)")

        val insertStmt = conn.prepareStatement("INSERT INTO test (value) VALUES (?)")
        insertStmt.setString(1, "hello")
        insertStmt.executeUpdate()

        val rs = conn.createStatement().executeQuery("SELECT value FROM test WHERE id = 1")
        assertTrue(rs.next())
        assertEquals("hello", rs.getString("value"))

        db.close()
    }

    @Test
    fun `event bus routes events to handlers`() = runBlocking {
        val eventBus = EventBus()
        var handlerCalled = false

        eventBus.register("TEST_EVENT") { envelope ->
            handlerCalled = true
            assertEquals("hello", envelope.payload["msg"]?.jsonPrimitive?.content)
            HandlerResult(listOf(
                EventEnvelope(
                    event = "TEST_RESPONSE",
                    payload = buildJsonObject { put("status", "ok") },
                    correlationId = envelope.correlationId,
                )
            ))
        }

        // Collect outgoing events
        val responses = mutableListOf<EventEnvelope>()
        val collectJob = launch {
            eventBus.outgoing.collect { responses.add(it) }
        }
        delay(50) // let collector subscribe

        // Emit event
        eventBus.emit(EventEnvelope(
            event = "TEST_EVENT",
            payload = buildJsonObject { put("msg", "hello") },
            correlationId = "test-123",
        ))

        delay(200) // let flow propagate
        collectJob.cancel()

        assertTrue(handlerCalled)
        assertEquals(1, responses.size)
        assertEquals("TEST_RESPONSE", responses[0].event)
        assertEquals("test-123", responses[0].correlationId)
    }

    @Test
    fun `protocol encodes and decodes events`() {
        val original = EventEnvelope(
            event = "TEST",
            payload = buildJsonObject {
                put("name", "Alice")
                put("age", 30)
            },
            correlationId = "abc-123",
            sourceSessionId = "ws-456",
        )

        val encoded = Protocol.encode(original)
        val decoded = Protocol.decode(encoded, "ws-456")

        assertEquals(original.event, decoded.event)
        assertEquals(original.correlationId, decoded.correlationId)
        assertEquals("Alice", decoded.payload["name"]?.jsonPrimitive?.content)
        assertEquals(30, decoded.payload["age"]?.jsonPrimitive?.int)
    }
}
