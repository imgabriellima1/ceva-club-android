package com.example.cevaclub.data

import android.os.Build
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal object PasswordHash {
    private val random = SecureRandom()
    private const val SHA256 = "PBKDF2WithHmacSHA256"
    private const val SHA1 = "PBKDF2WithHmacSHA1"

    data class Credential(val salt: ByteArray, val hash: ByteArray, val algorithm: String, val rounds: Int)

    fun create(password: String): Credential {
        val salt = ByteArray(16).also(random::nextBytes)
        val algorithm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) SHA256 else SHA1
        val rounds = if (algorithm == SHA256) 600_000 else 1_300_000
        // Android 7 supports setting a 160-bit key for the SHA-1 fallback.
        return Credential(salt, derive(password, salt, algorithm, rounds), algorithm, rounds)
    }

    fun matches(password: String, credential: Credential): Boolean =
        MessageDigest.isEqual(
            credential.hash,
            derive(password, credential.salt, credential.algorithm, credential.rounds)
        )

    private fun derive(password: String, salt: ByteArray, algorithm: String, rounds: Int): ByteArray {
        val keyBits = if (algorithm == SHA256) 256 else 160
        val chars = password.toCharArray()
        val spec = PBEKeySpec(chars, salt, rounds, keyBits)
        return try {
            SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            chars.fill('\u0000')
        }
    }
}
