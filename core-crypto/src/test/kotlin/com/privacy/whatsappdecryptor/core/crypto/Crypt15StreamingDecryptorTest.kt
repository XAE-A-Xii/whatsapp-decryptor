package com.privacy.whatsappdecryptor.core.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class Crypt15StreamingDecryptorTest {

    private val testKey = "4f6b2a8c1d3e5f7a9b0c2d4e6f8a1b3c5d7e9f0a2b4c6d8e0f1a3b5c7d9e1f3a"
    private val wrongKey = "0000000000000000000000000000000000000000000000000000000000000000"

    @Test
    fun `successful end-to-end streaming decryption and decompression`(@TempDir tempDir: Path) {
        val fixtureStream = javaClass.getResourceAsStream("/sample_backup.crypt15")
            ?: error("sample_backup.crypt15 fixture not found")
        val expectedBytes = javaClass.getResourceAsStream("/sample_expected.db")?.readAllBytes()
            ?: error("sample_expected.db fixture not found")

        val outputFile = tempDir.resolve("output_msgstore.db").toFile()
        var lastProgressBytes = 0L

        Crypt15StreamingDecryptor.decrypt(
            inputStream = fixtureStream,
            outputFile = outputFile,
            hexKey = testKey,
            onProgress = { bytesRead, _ ->
                assertTrue(bytesRead >= lastProgressBytes)
                lastProgressBytes = bytesRead
            }
        )

        assertTrue(outputFile.exists())
        assertEquals(expectedBytes.size.toLong(), outputFile.length())
        assertArrayEquals(expectedBytes, outputFile.readBytes())
    }

    @Test
    fun `incorrect key throws Crypt15AuthenticationException and deletes partial output`(@TempDir tempDir: Path) {
        val fixtureStream = javaClass.getResourceAsStream("/sample_backup.crypt15")
            ?: error("sample_backup.crypt15 fixture not found")

        val outputFile = tempDir.resolve("output_wrong_key.db").toFile()

        assertThrows<Crypt15AuthenticationException> {
            Crypt15StreamingDecryptor.decrypt(
                inputStream = fixtureStream,
                outputFile = outputFile,
                hexKey = wrongKey
            )
        }

        assertFalse(outputFile.exists(), "Output file must be cleaned up on failure")
    }

    @Test
    fun `corrupted ciphertext throws Crypt15AuthenticationException and cleans up`(@TempDir tempDir: Path) {
        val originalBytes = javaClass.getResourceAsStream("/sample_backup.crypt15")?.readAllBytes()
            ?: error("sample_backup.crypt15 not found")

        // Tamper with a ciphertext byte
        val corruptedBytes = originalBytes.clone()
        corruptedBytes[200] = (corruptedBytes[200].toInt() xor 0xFF).toByte()

        val outputFile = tempDir.resolve("output_corrupted.db").toFile()

        assertThrows<Crypt15AuthenticationException> {
            Crypt15StreamingDecryptor.decrypt(
                inputStream = corruptedBytes.inputStream(),
                outputFile = outputFile,
                hexKey = testKey
            )
        }

        assertFalse(outputFile.exists(), "Output file must be deleted on corrupted tag")
    }
}
