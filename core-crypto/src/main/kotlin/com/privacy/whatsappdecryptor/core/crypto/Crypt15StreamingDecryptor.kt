package com.privacy.whatsappdecryptor.core.crypto

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * High-performance, streaming decryptor for WhatsApp Crypt15 database backups.
 * Streams data in 64 KB chunks without loading full encrypted or decrypted databases into memory.
 */
object Crypt15StreamingDecryptor {

    private const val BUFFER_SIZE = 65536 // 64 KB streaming buffer
    private const val TRAILER_SIZE = 32   // 16 bytes Auth Tag + 16 bytes MD5 Checksum
    private val SQLITE_HEADER_MAGIC = "SQLite format 3\u0000".toByteArray(StandardCharsets.US_ASCII)

    /**
     * Decrypts [inputStream] containing a Crypt15 container and writes the resulting SQLite database to [outputFile].
     *
     * @param inputStream Source stream of msgstore.db.crypt15
     * @param outputFile Destination file for decrypted msgstore.db in app-private storage
     * @param hexKey The 64-digit WhatsApp backup encryption key
     * @param totalBytes Optional total size for progress reporting (-1 if unknown)
     * @param onProgress Callback receiving (bytesProcessed, totalBytes)
     */
    fun decrypt(
        inputStream: InputStream,
        outputFile: File,
        hexKey: String,
        totalBytes: Long = -1L,
        onProgress: (bytesProcessed: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ) {
        val rootKey = KeyValidator.parseKey(hexKey)
        val derivedKey = Hkdf.deriveWhatsAppBackupKey(rootKey)

        // Ensure parent directories exist
        outputFile.parentFile?.mkdirs()

        var success = false
        val bufferedInput = if (inputStream is BufferedInputStream) inputStream else BufferedInputStream(inputStream, BUFFER_SIZE)

        try {
            FileOutputStream(outputFile).use { fos ->
                BufferedOutputStream(fos, BUFFER_SIZE).use { bos ->
                    decryptInternal(
                        input = bufferedInput,
                        output = bos,
                        derivedKey = derivedKey,
                        totalBytes = totalBytes,
                        onProgress = onProgress
                    )
                }
            }
            success = true
        } catch (e: org.bouncycastle.crypto.InvalidCipherTextException) {
            throw Crypt15AuthenticationException("Decryption authentication failed: incorrect key or corrupted backup", e)
        } catch (e: DataFormatException) {
            throw Crypt15AuthenticationException("Decryption failed: incorrect key or corrupted backup", e)
        } catch (e: Crypt15Exception) {
            throw e
        } catch (e: Exception) {
            throw Crypt15Exception("Unexpected error during Crypt15 decryption: ${e.message}", e)
        } finally {
            if (!success && outputFile.exists()) {
                outputFile.delete()
            }
        }
    }

    private fun decryptInternal(
        input: InputStream,
        output: BufferedOutputStream,
        derivedKey: ByteArray,
        totalBytes: Long,
        onProgress: (Long, Long) -> Unit
    ) {
        val md5 = MessageDigest.getInstance("MD5")

        // 1. Parse container header and extract IV
        val header = ProtobufHeaderParser.parseHeader(input)

        // Include header bytes in MD5 checksum calculation
        val headerLengthByte = byteArrayOf(header.protobufRaw.size.toByte())
        md5.update(headerLengthByte)
        if (header.hasFeatureFlag) {
            md5.update(0x01.toByte())
        }
        md5.update(header.protobufRaw)

        var totalBytesRead = header.totalHeaderBytesRead.toLong()

        // 2. Initialize BouncyCastle GCMBlockCipher (constant-memory streaming, never buffers 2 GB in RAM)
        val cipher = org.bouncycastle.crypto.modes.GCMBlockCipher.newInstance(org.bouncycastle.crypto.engines.AESEngine.newInstance())
        val aeadParams = org.bouncycastle.crypto.params.AEADParameters(
            org.bouncycastle.crypto.params.KeyParameter(derivedKey),
            128,
            header.iv
        )
        cipher.init(false, aeadParams) // false = decrypt

        // 3. Sliding 32-byte window for trailing tag and checksum
        val window = ByteArray(TRAILER_SIZE)
        var windowLen = 0

        // Fill initial 32-byte window
        while (windowLen < TRAILER_SIZE) {
            val r = input.read(window, windowLen, TRAILER_SIZE - windowLen)
            if (r == -1) {
                throw Crypt15ParseException("File too small: must be at least header + 32 bytes trailer")
            }
            windowLen += r
            totalBytesRead += r
        }

        // 4. Stream ciphertext through cipher and inflater
        val readBuffer = ByteArray(BUFFER_SIZE)
        val cipherOutputBuffer = ByteArray(BUFFER_SIZE + 32)
        val decompressedBuffer = ByteArray(BUFFER_SIZE)
        val inflater = Inflater(false) // Standard ZLIB format

        var isFirstDecompressedChunk = true

        while (true) {
            val bytesRead = input.read(readBuffer)
            if (bytesRead == -1) break

            totalBytesRead += bytesRead

            if (bytesRead >= TRAILER_SIZE) {
                // Entire old window is ciphertext
                val outLen = cipher.processBytes(window, 0, TRAILER_SIZE, cipherOutputBuffer, 0)
                if (outLen > 0) {
                    inflateAndWrite(cipherOutputBuffer, 0, outLen, inflater, decompressedBuffer, output, isFirstDecompressedChunk)
                    isFirstDecompressedChunk = false
                }
                md5.update(window, 0, TRAILER_SIZE)

                // Middle portion of readBuffer is also ciphertext
                val middleLen = bytesRead - TRAILER_SIZE
                if (middleLen > 0) {
                    val mOutLen = cipher.processBytes(readBuffer, 0, middleLen, cipherOutputBuffer, 0)
                    if (mOutLen > 0) {
                        inflateAndWrite(cipherOutputBuffer, 0, mOutLen, inflater, decompressedBuffer, output, isFirstDecompressedChunk)
                        isFirstDecompressedChunk = false
                    }
                    md5.update(readBuffer, 0, middleLen)
                }

                // Copy last 32 bytes of readBuffer into window
                System.arraycopy(readBuffer, bytesRead - TRAILER_SIZE, window, 0, TRAILER_SIZE)
            } else {
                // bytesRead < TRAILER_SIZE
                val outLen = cipher.processBytes(window, 0, bytesRead, cipherOutputBuffer, 0)
                if (outLen > 0) {
                    inflateAndWrite(cipherOutputBuffer, 0, outLen, inflater, decompressedBuffer, output, isFirstDecompressedChunk)
                    isFirstDecompressedChunk = false
                }
                md5.update(window, 0, bytesRead)

                // Shift remaining window forward and append readBuffer
                System.arraycopy(window, bytesRead, window, 0, TRAILER_SIZE - bytesRead)
                System.arraycopy(readBuffer, 0, window, TRAILER_SIZE - bytesRead, bytesRead)
            }

            onProgress(totalBytesRead, totalBytes)
        }

        // 5. EOF reached: window holds candidate tag and checksum
        val candidateTag = window.copyOfRange(0, 16)
        val candidateChecksum = window.copyOfRange(16, 32)

        val md5Clone = md5.clone() as MessageDigest
        md5Clone.update(candidateTag)
        val singleFileDigest = md5Clone.digest()

        try {
            if (singleFileDigest.contentEquals(candidateChecksum)) {
                // Single-file backup verified: candidateTag is the GCM tag!
                val outLen = cipher.processBytes(candidateTag, 0, 16, cipherOutputBuffer, 0)
                if (outLen > 0) {
                    inflateAndWrite(cipherOutputBuffer, 0, outLen, inflater, decompressedBuffer, output, isFirstDecompressedChunk)
                }
                val finalLen = cipher.doFinal(cipherOutputBuffer, 0)
                if (finalLen > 0) {
                    inflateAndWrite(cipherOutputBuffer, 0, finalLen, inflater, decompressedBuffer, output, isFirstDecompressedChunk)
                }
            } else {
                // Multi-file backup: candidateTag is ciphertext, candidateChecksum is GCM tag
                val outLen1 = cipher.processBytes(candidateTag, 0, 16, cipherOutputBuffer, 0)
                if (outLen1 > 0) {
                    inflateAndWrite(cipherOutputBuffer, 0, outLen1, inflater, decompressedBuffer, output, isFirstDecompressedChunk)
                    isFirstDecompressedChunk = false
                }
                val outLen2 = cipher.processBytes(candidateChecksum, 0, 16, cipherOutputBuffer, 0)
                if (outLen2 > 0) {
                    inflateAndWrite(cipherOutputBuffer, 0, outLen2, inflater, decompressedBuffer, output, isFirstDecompressedChunk)
                }
                val finalLen = cipher.doFinal(cipherOutputBuffer, 0)
                if (finalLen > 0) {
                    inflateAndWrite(cipherOutputBuffer, 0, finalLen, inflater, decompressedBuffer, output, isFirstDecompressedChunk)
                }
            }
        } catch (e: org.bouncycastle.crypto.InvalidCipherTextException) {
            throw Crypt15AuthenticationException("Decryption authentication failed: incorrect key or corrupted backup", e)
        }

        // Flush remaining inflater output
        while (!inflater.finished()) {
            val count = inflater.inflate(decompressedBuffer)
            if (count == 0) break
            output.write(decompressedBuffer, 0, count)
        }

        inflater.end()
        output.flush()

        onProgress(totalBytesRead, totalBytes)
    }

    private fun inflateAndWrite(
        data: ByteArray,
        offset: Int,
        len: Int,
        inflater: Inflater,
        decompressedBuffer: ByteArray,
        output: BufferedOutputStream,
        isFirstChunk: Boolean
    ) {
        inflater.setInput(data, offset, len)
        var first = isFirstChunk
        while (!inflater.needsInput() && !inflater.finished()) {
            val count = inflater.inflate(decompressedBuffer)
            if (count > 0) {
                if (first) {
                    verifySqliteHeader(decompressedBuffer, count)
                    first = false
                }
                output.write(decompressedBuffer, 0, count)
            }
        }
    }

    private fun verifySqliteHeader(buffer: ByteArray, count: Int) {
        if (count < SQLITE_HEADER_MAGIC.size) {
            throw Crypt15InvalidDatabaseException("Decompressed database header is too short")
        }
        for (i in SQLITE_HEADER_MAGIC.indices) {
            if (buffer[i] != SQLITE_HEADER_MAGIC[i]) {
                throw Crypt15InvalidDatabaseException("Output is not a valid SQLite database (header signature mismatch)")
            }
        }
    }
}
