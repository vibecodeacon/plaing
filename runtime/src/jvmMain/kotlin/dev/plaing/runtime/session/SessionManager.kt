package dev.plaing.runtime.session

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class Session(
    val id: String = UUID.randomUUID().toString(),
    val token: String = UUID.randomUUID().toString(),
    val userId: Long,
    val wsSessionId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

class SessionManager {
    private val sessions = ConcurrentHashMap<String, Session>()  // token -> Session
    private val userSessions = ConcurrentHashMap<Long, MutableList<Session>>()  // userId -> sessions

    fun createSession(userId: Long, wsSessionId: String? = null): Session {
        val session = Session(userId = userId, wsSessionId = wsSessionId)
        sessions[session.token] = session
        userSessions.getOrPut(userId) { mutableListOf() }.add(session)
        return session
    }

    fun getSession(token: String): Session? = sessions[token]

    fun getUserSessions(userId: Long): List<Session> = userSessions[userId] ?: emptyList()

    fun invalidate(token: String): Boolean {
        val session = sessions.remove(token) ?: return false
        userSessions[session.userId]?.removeIf { it.token == token }
        return true
    }

    fun invalidateAllForUser(userId: Long) {
        val userSessionList = userSessions.remove(userId) ?: return
        for (session in userSessionList) {
            sessions.remove(session.token)
        }
    }
}
