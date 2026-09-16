package com.privacy.whatsappdecryptor.core.crypto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream

class ProtobufHeaderParserTest {

    private fun String.hexToByteArray(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }

    @Test
    fun `parse valid Crypt15 header with feature flag and extract 16-byte IV`() {
        val headerHex = "9d0108011a120a10a0a1a2a3a4a5a6a7a8a9aaabacadaeaf2284010a0a322e32332e32302e37361a193132333435363738393040732e77686174736170702e6e6574200028013001380140014801500158016001680170017801800101880101900101980101a00101a80101b00101b80101c00101c80101d00101d80101e00101e80101f00101f80101800201880201900200980201a00201a80201b80201"
        val expectedIvHex = "a0a1a2a3a4a5a6a7a8a9aaabacadaeaf"

        val inputStream = ByteArrayInputStream(headerHex.hexToByteArray())
        val parsedHeader = ProtobufHeaderParser.parseHeader(inputStream)

        assertNotNull(parsedHeader)
        assertEquals(expectedIvHex, parsedHeader.iv.toHexString())
        assertEquals(16, parsedHeader.iv.size)
        assertEquals(159, parsedHeader.totalHeaderBytesRead) // 1 byte size + 1 byte flag + 157 bytes protobuf
    }

    @Test
    fun `parse header without optional feature flag`() {
        // Same header but without the 0x01 flag byte: size byte followed immediately by protobuf (0x08 starts protobuf)
        val protoHex = "1a120a10a0a1a2a3a4a5a6a7a8a9aaabacadaeaf"
        val headerBytes = byteArrayOf((protoHex.length / 2).toByte()) + protoHex.hexToByteArray()

        val inputStream = ByteArrayInputStream(headerBytes)
        val parsedHeader = ProtobufHeaderParser.parseHeader(inputStream)

        assertEquals("a0a1a2a3a4a5a6a7a8a9aaabacadaeaf", parsedHeader.iv.toHexString())
    }

    @Test
    fun `reject truncated or invalid header streams`() {
        val truncatedStream = ByteArrayInputStream(byteArrayOf(0x50, 0x01, 0x08))
        assertThrows<Crypt15ParseException> {
            ProtobufHeaderParser.parseHeader(truncatedStream)
        }
    }
}
