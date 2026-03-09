package dev.plaing.runtime.db

import java.sql.Connection
import java.sql.DriverManager

class DatabaseManager(
    private val url: String = "jdbc:sqlite:plaing.db",
) {
    private var connection: Connection? = null

    fun connect(): Connection {
        val conn = DriverManager.getConnection(url)
        // Enable WAL mode for better concurrent access (file-based DBs only)
        if (!url.contains(":memory:")) {
            val stmt = conn.createStatement()
            val rs = stmt.executeQuery("PRAGMA journal_mode=WAL")
            rs.close()
            stmt.close()
        }
        connection = conn
        return conn
    }

    fun getConnection(): Connection = connection ?: error("Database not connected. Call connect() first.")

    fun executeSql(sql: String) {
        getConnection().createStatement().execute(sql)
    }

    fun close() {
        connection?.close()
        connection = null
    }
}
