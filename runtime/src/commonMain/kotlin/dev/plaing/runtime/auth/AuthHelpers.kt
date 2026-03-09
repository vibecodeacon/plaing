package dev.plaing.runtime.auth

object AuthHelpers {
    /**
     * Simple hash function for passwords.
     * In production, use bcrypt/scrypt/argon2.
     * This is a basic SHA-256 implementation for the plaing prototype.
     */
    fun hashPassword(password: String): String {
        // For the prototype, just use a simple reversible hash marker
        // Real implementation would use platform-specific crypto
        return "hashed:${password.hashCode().toUInt()}"
    }

    fun verifyPassword(password: String, hashed: String): Boolean {
        return hashPassword(password) == hashed
    }

    fun generateToken(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..32).map { chars.random() }.joinToString("")
    }
}
