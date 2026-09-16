package com.privacy.whatsappdecryptor.core.crypto

/**
 * Validates, normalizes, and decodes 64-character hexadecimal WhatsApp backup keys.
 */
object KeyValidator {

    private val HEX_CHARS_REGEX = Regex("^[0-9a-fA-F]{64}$")

    /**
     * Normalizes a key input by trimming, removing spaces and hyphens, and converting to lowercase.
     */
    fun normalize(input: String): String {
        return input.trim()
            .replace(" ", "")
            .replace("-", "")
            .lowercase()
    }

    /**
     * Returns true if the normalized input is exactly 64 hexadecimal characters.
     */
    fun isValidKeyFormat(input: String): Boolean {
        val normalized = normalize(input)
        return HEX_CHARS_REGEX.matches(normalized)
    }

    /**
     * Decodes the 64-character hex key into 32 raw bytes.
     * Throws [IllegalArgumentException] if format is invalid.
     */
    fun parseKey(input: String): ByteArray {
        val normalized = normalize(input)
        require(isValidKeyFormat(normalized)) {
            "Invalid key format: expected 64 hexadecimal characters, got ${normalized.length}"
        }

        val result = ByteArray(32)
        for (i in 0 until 32) {
            val startIndex = i * 2
            val byteStr = normalized.substring(startIndex, startIndex + 2)
            result[i] = byteStr.toInt(16).toByte()
        }
        return result
    }
}
