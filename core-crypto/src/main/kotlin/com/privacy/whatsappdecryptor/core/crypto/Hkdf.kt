package com.privacy.whatsappdecryptor.core.crypto

import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.ceil

/**
 * Clean-room implementation of RFC 5869 HMAC-based Extract-and-Expand Key Derivation Function (HKDF)
 * using standard Java Cryptography Extension (JCE) primitives.
 */
object Hkdf {

    private const val HMAC_SHA256 = "HmacSHA256"
    private const val HASH_LEN = 32 // SHA-256 output length in bytes

    private val WHATSAPP_BACKUP_INFO = "backup encryption".toByteArray(StandardCharsets.UTF_8)
    private val WHATSAPP_SALT = ByteArray(HASH_LEN) // 32 zero bytes

    /**
     * Derives the 32-byte AES-256-GCM encryption key for WhatsApp Crypt15 backups from the 32-byte root key.
     */
    fun deriveWhatsAppBackupKey(rootKey: ByteArray): ByteArray {
        require(rootKey.size == 32) { "Root key must be exactly 32 bytes (256 bits)" }
        return hkdf(
            ikm = rootKey,
            salt = WHATSAPP_SALT,
            info = WHATSAPP_BACKUP_INFO,
            length = 32
        )
    }

    /**
     * Standard RFC 5869 HKDF Extract-and-Expand with HMAC-SHA256.
     */
    fun hkdf(
        ikm: ByteArray,
        salt: ByteArray? = null,
        info: ByteArray? = null,
        length: Int = 32
    ): ByteArray {
        val effectiveSalt = if (salt == null || salt.isEmpty()) ByteArray(HASH_LEN) else salt
        val prk = extract(effectiveSalt, ikm)
        return expand(prk, info ?: ByteArray(0), length)
    }

    /**
     * RFC 5869 Step 1: Extract.
     * PRK = HMAC-Hash(salt, IKM)
     */
    fun extract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_SHA256)
        mac.init(SecretKeySpec(salt, HMAC_SHA256))
        return mac.doFinal(ikm)
    }

    /**
     * RFC 5869 Step 2: Expand.
     * T(0) = ""
     * T(1) = HMAC-Hash(PRK, T(0) | info | 0x01)
     * ...
     * OKM = first L octets of T(1) | T(2) | ... | T(N)
     */
    fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..(255 * HASH_LEN)) { "Output length out of bounds: $length" }

        val n = ceil(length.toDouble() / HASH_LEN.toDouble()).toInt()
        val mac = Mac.getInstance(HMAC_SHA256)
        mac.init(SecretKeySpec(prk, HMAC_SHA256))

        val result = ByteArray(length)
        var t = ByteArray(0)
        var bytesWritten = 0

        for (i in 1..n) {
            mac.reset()
            mac.update(t)
            mac.update(info)
            mac.update(i.toByte())
            t = mac.doFinal()

            val bytesToCopy = minOf(HASH_LEN, length - bytesWritten)
            System.arraycopy(t, 0, result, bytesWritten, bytesToCopy)
            bytesWritten += bytesToCopy
        }

        return result
    }
}
