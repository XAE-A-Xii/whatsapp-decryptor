package com.privacy.whatsappdecryptor.core.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class KeyValidatorTest {

    @Test
    fun `valid 64-character lowercase hex key parses correctly`() {
        val hex = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        assertTrue(KeyValidator.isValidKeyFormat(hex))

        val bytes = KeyValidator.parseKey(hex)
        assertEquals(32, bytes.size)
        assertEquals(0x01.toByte(), bytes[0])
        assertEquals(0xef.toByte(), bytes[31])
    }

    @Test
    fun `valid key with spaces, hyphens, and uppercase parses correctly`() {
        val raw = " 0123-4567-89AB-CDEF 0123-4567-89AB-CDEF 0123-4567-89AB-CDEF 0123-4567-89AB-CDEF "
        val normalized = KeyValidator.normalize(raw)
        assertEquals("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", normalized)
        assertTrue(KeyValidator.isValidKeyFormat(raw))

        val bytes = KeyValidator.parseKey(raw)
        assertEquals(32, bytes.size)
    }

    @Test
    fun `invalid lengths or non-hex characters reject`() {
        assertFalse(KeyValidator.isValidKeyFormat("1234"))
        assertFalse(KeyValidator.isValidKeyFormat("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdeg")) // 'g' is invalid hex
        assertFalse(KeyValidator.isValidKeyFormat(""))

        assertThrows<IllegalArgumentException> {
            KeyValidator.parseKey("short")
        }
    }
}
