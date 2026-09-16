package com.privacy.whatsappdecryptor.core.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HkdfTest {

    private fun String.hexToByteArray(): ByteArray {
        check(length % 2 == 0) { "Must have even length" }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }

    @Test
    fun `test WhatsApp backup encryption key derivation against wa-crypt-tools reference`() {
        val rootKeyHex = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val rootKey = rootKeyHex.hexToByteArray()

        val expectedDerivedKeyHex = "d44f47a5e7d390c365ea62ece489a5418f2ef9907e0383cd49766d1b819769dc"

        val derivedKey = Hkdf.deriveWhatsAppBackupKey(rootKey)

        assertEquals(expectedDerivedKeyHex, derivedKey.toHexString())
    }

    @Test
    fun `test RFC 5869 Test Case 1 with SHA-256`() {
        // RFC 5869 Case 1
        val ikm = "0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b".hexToByteArray()
        val salt = "000102030405060708090a0b0c".hexToByteArray()
        val info = "f0f1f2f3f4f5f6f7f8f9".hexToByteArray()
        val expectedOkm = "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865".hexToByteArray()

        val okm = Hkdf.hkdf(ikm = ikm, salt = salt, info = info, length = 42)

        assertArrayEquals(expectedOkm, okm)
    }
}
