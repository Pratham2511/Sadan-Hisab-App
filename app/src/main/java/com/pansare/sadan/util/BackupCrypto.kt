package com.pansare.sadan.util

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Versioned, portable AES-GCM encryption envelope for backups.
 * Passwords are never stored. Key derivation uses PBKDF2WithHmacSHA256.
 *
 * Envelope format: (MAGIC 4B)(ITERATIONS 4B)(SALT 16B)(IV 12B)(CIPHERTEXT...)
 */
object BackupCrypto {

    private const val MAGIC = "PSB1"
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val ITERATIONS = 210_000

    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        require(password.isNotEmpty()) { "A backup password is required." }

        val random = SecureRandom()
        val salt = ByteArray(SALT_SIZE).also(random::nextBytes)
        val iv = ByteArray(IV_SIZE).also(random::nextBytes)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, derive(password, salt), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plain)

        return ByteArrayOutputStream().use { out ->
            out.write(MAGIC.toByteArray())
            out.write(ByteBuffer.allocate(4).putInt(ITERATIONS).array())
            out.write(salt)
            out.write(iv)
            out.write(encrypted)
            out.toByteArray()
        }
    }

    fun decrypt(envelope: ByteArray, password: CharArray): ByteArray {
        require(envelope.size > 4 + 4 + SALT_SIZE + IV_SIZE + 16) {
            "Backup file is corrupted."
        }

        val input = ByteBuffer.wrap(envelope)
        val magic = ByteArray(4).also(input::get).decodeToString()
        require(magic == MAGIC) { "Unsupported backup file." }

        val iterations = input.int
        require(iterations >= 100_000) {
            "Backup file uses an unsafe or unsupported key configuration."
        }

        val salt = ByteArray(SALT_SIZE).also(input::get)
        val iv = ByteArray(IV_SIZE).also(input::get)
        val cipherText = ByteArray(input.remaining()).also(input::get)

        return try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, derive(password, salt, iterations), GCMParameterSpec(128, iv))
            }.doFinal(cipherText)
        } catch (_: Exception) {
            throw IllegalArgumentException("Incorrect password or corrupted backup.")
        }
    }

    private fun derive(
        password: CharArray,
        salt: ByteArray,
        iterations: Int = ITERATIONS
    ): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }
}
