package com.privacy.whatsappdecryptor.core.crypto

import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.PushbackInputStream

data class Crypt15Header(
    val iv: ByteArray,
    val protobufRaw: ByteArray,
    val hasFeatureFlag: Boolean,
    val totalHeaderBytesRead: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Crypt15Header
        return iv.contentEquals(other.iv) && totalHeaderBytesRead == other.totalHeaderBytesRead
    }

    override fun hashCode(): Int = iv.contentHashCode()
}

/**
 * Lightweight, zero-dependency parser for the WhatsApp Crypt15 BackupPrefix container header.
 * Extracts the 16-byte initialization vector (IV) directly from the wire format.
 */
object ProtobufHeaderParser {

    private const val C15_IV_FIELD_TAG = (3 shl 3) or 2 // 0x1A (Field 3, WireType 2)
    private const val IV_SUBFIELD_TAG = (1 shl 3) or 2   // 0x0A (Field 1, WireType 2)

    /**
     * Reads and parses the Crypt15 container header from [inputStream].
     * Leaves the [inputStream] positioned directly at the start of the AES-GCM ciphertext.
     */
    fun parseHeader(inputStream: InputStream): Crypt15Header {
        val pushbackStream = if (inputStream is PushbackInputStream) inputStream else PushbackInputStream(inputStream, 1)

        // 1. Read first byte: length of the upcoming Protobuf payload
        val sizeByte = pushbackStream.read()
        if (sizeByte == -1) {
            throw Crypt15ParseException("Empty stream: cannot read Crypt15 header size")
        }
        val protoSize = sizeByte and 0xFF
        var headerBytesRead = 1

        // 2. Check for optional feature flag byte (0x01)
        val nextByte = pushbackStream.read()
        if (nextByte == -1) {
            throw Crypt15ParseException("Truncated stream after header size")
        }

        val hasFeatureFlag: Boolean
        if (nextByte == 0x01) {
            hasFeatureFlag = true
            headerBytesRead += 1
        } else {
            hasFeatureFlag = false
            pushbackStream.unread(nextByte)
        }

        // 3. Read protoSize bytes of Protobuf message
        val protoBytes = ByteArray(protoSize)
        var totalRead = 0
        while (totalRead < protoSize) {
            val count = pushbackStream.read(protoBytes, totalRead, protoSize - totalRead)
            if (count == -1) {
                throw Crypt15ParseException("Truncated Protobuf payload: expected $protoSize bytes, got $totalRead")
            }
            totalRead += count
        }
        headerBytesRead += protoSize

        // 4. Parse fields to extract the 16-byte IV
        val iv = extractIvFromProto(protoBytes)
            ?: throw Crypt15ParseException("Could not locate C15_IV in BackupPrefix Protobuf")

        return Crypt15Header(
            iv = iv,
            protobufRaw = protoBytes,
            hasFeatureFlag = hasFeatureFlag,
            totalHeaderBytesRead = headerBytesRead
        )
    }

    private fun extractIvFromProto(protoBytes: ByteArray): ByteArray? {
        val stream = ByteArrayInputStream(protoBytes)
        while (stream.available() > 0) {
            val tag = readVarint(stream)?.toInt() ?: break
            val fieldNumber = tag ushr 3
            val wireType = tag and 0x07

            when (wireType) {
                0 -> readVarint(stream) // Varint
                1 -> skipBytes(stream, 8) // 64-bit
                2 -> {
                    // Length-delimited
                    val length = readVarint(stream)?.toInt() ?: break
                    val value = ByteArray(length)
                    val read = stream.read(value)
                    if (read != length) throw EOFException("Truncated length-delimited field")

                    if (tag == C15_IV_FIELD_TAG) {
                        // Embedded C15_IV message! Parse subfield 1 (IV)
                        val iv = extractIvFromC15IvMessage(value)
                        if (iv != null) return iv
                    }
                }
                5 -> skipBytes(stream, 4) // 32-bit
                else -> throw Crypt15ParseException("Unsupported wire type: $wireType")
            }
        }
        return null
    }

    private fun extractIvFromC15IvMessage(submessageBytes: ByteArray): ByteArray? {
        val stream = ByteArrayInputStream(submessageBytes)
        while (stream.available() > 0) {
            val tag = readVarint(stream)?.toInt() ?: break
            val wireType = tag and 0x07

            if (wireType == 2 && tag == IV_SUBFIELD_TAG) {
                val length = readVarint(stream)?.toInt() ?: break
                if (length == 16) {
                    val iv = ByteArray(16)
                    val read = stream.read(iv)
                    if (read == 16) return iv
                }
            } else {
                skipField(stream, wireType)
            }
        }
        return null
    }

    private fun skipField(stream: InputStream, wireType: Int) {
        when (wireType) {
            0 -> readVarint(stream)
            1 -> skipBytes(stream, 8)
            2 -> {
                val len = readVarint(stream)?.toInt() ?: 0
                skipBytes(stream, len)
            }
            5 -> skipBytes(stream, 4)
            else -> throw Crypt15ParseException("Unknown wire type: $wireType")
        }
    }

    private fun skipBytes(stream: InputStream, count: Int) {
        var remaining = count
        while (remaining > 0) {
            val skipped = stream.skip(remaining.toLong()).toInt()
            if (skipped <= 0) {
                if (stream.read() == -1) throw EOFException("Unexpected EOF while skipping")
                remaining -= 1
            } else {
                remaining -= skipped
            }
        }
    }

    private fun readVarint(stream: InputStream): Long? {
        var value = 0L
        var shift = 0
        while (shift < 64) {
            val b = stream.read()
            if (b == -1) {
                if (shift == 0) return null
                throw EOFException("Truncated varint")
            }
            value = value or ((b and 0x7F).toLong() shl shift)
            if ((b and 0x80) == 0) return value
            shift += 7
        }
        throw Crypt15ParseException("Varint too long")
    }
}
