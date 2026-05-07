package me.him188.ani.utils.io

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Local AES-CTR obfuscation for credential-like strings stored on disk.
 *
 * Mirrors `rclone obscure`: encryption with a hardcoded key + random IV, output
 * as URL-safe base64 (no padding). The key is checked into source so any reader
 * with the binary can [reveal] anything we wrote — this is anti-eyedropping,
 * not real encryption. Use [reveal] / [tryReveal] paired with [obscure].
 *
 * Output format: `ob1:` || base64UrlNoPad(IV(16) || ciphertext). The
 * [MAGIC_PREFIX] disambiguates obscured payloads from legacy plaintext during
 * migration so [tryReveal] never accidentally "decrypts" a real password that
 * happens to round-trip through base64.
 */

private val OBSCURE_KEY: ByteArray = byteArrayOf(
    0x7a.toByte(), 0xc4.toByte(), 0x18.toByte(), 0x9f.toByte(),
    0xe2.toByte(), 0x3b.toByte(), 0x05.toByte(), 0xd1.toByte(),
    0x4f.toByte(), 0xa6.toByte(), 0x82.toByte(), 0x57.toByte(),
    0x6e.toByte(), 0xb1.toByte(), 0x90.toByte(), 0x2d.toByte(),
    0xc8.toByte(), 0x73.toByte(), 0xee.toByte(), 0x4a.toByte(),
    0x05.toByte(), 0x16.toByte(), 0xd9.toByte(), 0xb7.toByte(),
    0x21.toByte(), 0x68.toByte(), 0xfa.toByte(), 0x3c.toByte(),
    0x97.toByte(), 0x52.toByte(), 0xab.toByte(), 0x80.toByte(),
)

internal const val OBSCURE_IV_SIZE: Int = 16

/** Tag identifying our obscured-payload format. Bumping this rotates the on-disk shape. */
const val MAGIC_PREFIX: String = "ob1:"

@OptIn(ExperimentalEncodingApi::class)
private val Base64UrlNoPad = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

/**
 * Encrypts under [OBSCURE_KEY] using AES-CTR with the given [iv]. Encrypt and
 * decrypt are the same operation (XOR with keystream); callers reuse this for
 * both directions. [iv] must be exactly [OBSCURE_IV_SIZE] bytes.
 */
internal expect fun aesCtrXor(key: ByteArray, iv: ByteArray, input: ByteArray): ByteArray

/** Cryptographically secure random bytes for IV generation. */
internal expect fun secureRandomBytes(size: Int): ByteArray

@OptIn(ExperimentalEncodingApi::class)
fun obscure(plaintext: String): String {
    if (plaintext.isEmpty()) return ""
    val iv = secureRandomBytes(OBSCURE_IV_SIZE)
    val ciphertext = aesCtrXor(OBSCURE_KEY, iv, plaintext.encodeToByteArray())
    return MAGIC_PREFIX + Base64UrlNoPad.encode(iv + ciphertext)
}

/**
 * Reveals an [obscured] string. Returns `null` when the input is not in our
 * obscured format — typically because it is legacy plaintext from before
 * obscuring was introduced. Throws on a malformed-but-prefixed payload so
 * genuine corruption is not silently treated as plaintext.
 */
@OptIn(ExperimentalEncodingApi::class)
fun tryReveal(obscured: String): String? {
    if (obscured.isEmpty()) return ""
    if (!obscured.startsWith(MAGIC_PREFIX)) return null
    val raw = Base64UrlNoPad.decode(obscured.substring(MAGIC_PREFIX.length))
    require(raw.size >= OBSCURE_IV_SIZE) { "obscured payload too short" }
    val iv = raw.copyOfRange(0, OBSCURE_IV_SIZE)
    val ciphertext = raw.copyOfRange(OBSCURE_IV_SIZE, raw.size)
    return aesCtrXor(OBSCURE_KEY, iv, ciphertext).decodeToString()
}
