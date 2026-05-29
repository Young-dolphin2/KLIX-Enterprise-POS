package com.example.barandgrillownerpanel.utils

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * PIN security utility.
 * Hashes PINs with SHA-256 + per-PIN salt before storage.
 * NEVER stores plaintext PINs.
 */
object PinHasher {
    private const val TAG = "PIN_HASHER"
    private val random = SecureRandom()

    /**
     * Generate a random salt (16 bytes, base64 encoded).
     */
    fun generateSalt(): String {
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return Base64.getEncoder().encodeToString(salt)
    }

    /**
     * Hash a PIN with the given salt using SHA-256.
     * Format: "salt:hash"
     * 
     * Example output: "aB3x...=":ABC123..."
     */
    fun hash(pin: String, salt: String? = null): String {
        val actualSalt = salt ?: generateSalt()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(actualSalt.toByteArray())
        digest.update(pin.toByteArray())
        val hash = Base64.getEncoder().encodeToString(digest.digest())
        return "$actualSalt:$hash"
    }

    /**
     * Verify a PIN against a stored hash.
     * Stored format: "salt:hash"
     */
    fun verify(pin: String, storedHash: String): Boolean {
        // If stored as plaintext (migration), hash it now
        if (!storedHash.contains(":")) {
            // Legacy plaintext — hash it and return comparison
            return pin == storedHash
        }
        
        val parts = storedHash.split(":", limit = 2)
        if (parts.size != 2) return false
        
        val salt = parts[0]
        val expectedHash = parts[1]
        val computedHash = hash(pin, salt).split(":")[1]
        
        return MessageDigest.isEqual(
            computedHash.toByteArray(),
            expectedHash.toByteArray()
        )
    }

    /**
     * Check if a PIN is strong enough.
     * Returns null if valid, error message if too weak.
     */
    fun validateStrength(pin: String): String? {
        return when {
            pin.length < 4 -> "PIN must be at least 4 digits"
            pin.length > 10 -> "PIN must be at most 10 digits"
            pin.all { it == pin[0] } -> "PIN cannot be all the same digit"
            pin in listOf("1234", "0000", "1111", "2222", "3333", "4444", "5555", "6666", "7777", "8888", "9999") -> 
                "PIN is too common. Choose a stronger one."
            pin.any { !it.isDigit() } -> "PIN must contain only digits"
            else -> null // Valid
        }
    }
}


