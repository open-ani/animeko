@file:OptIn(ExperimentalForeignApi::class)

package me.him188.ani.utils.io

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.refTo
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreCrypto.CCCryptorCreateWithMode
import platform.CoreCrypto.CCCryptorFinal
import platform.CoreCrypto.CCCryptorRefVar
import platform.CoreCrypto.CCCryptorRelease
import platform.CoreCrypto.CCCryptorUpdate
import platform.CoreCrypto.kCCAlgorithmAES
import platform.CoreCrypto.kCCEncrypt
import platform.CoreCrypto.kCCModeCTR
import platform.CoreCrypto.kCCModeOptionCTR_BE
import platform.CoreCrypto.kCCSuccess
import platform.CoreCrypto.ccNoPadding
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.posix.size_tVar

internal actual fun aesCtrXor(key: ByteArray, iv: ByteArray, input: ByteArray): ByteArray = memScoped {
    val cryptorRef = alloc<CCCryptorRefVar>()
    val createStatus = CCCryptorCreateWithMode(
        op = kCCEncrypt,
        mode = kCCModeCTR,
        alg = kCCAlgorithmAES,
        padding = ccNoPadding,
        iv = iv.refTo(0),
        key = key.refTo(0),
        keyLength = key.size.convert(),
        tweak = null,
        tweakLength = 0u,
        numRounds = 0,
        options = kCCModeOptionCTR_BE,
        cryptorRef = cryptorRef.ptr,
    )
    require(createStatus == kCCSuccess) { "CCCryptorCreateWithMode failed: $createStatus" }
    try {
        // CTR is a stream cipher: ciphertext length == plaintext length, no
        // padding to flush. We still call CCCryptorFinal for symmetry / future
        // mode changes; with CTR it returns 0 bytes written.
        val output = ByteArray(input.size)
        val written = alloc<size_tVar>()
        val updateStatus = output.usePinned { outPin ->
            CCCryptorUpdate(
                cryptorRef = cryptorRef.value,
                dataIn = input.refTo(0),
                dataInLength = input.size.convert(),
                dataOut = outPin.addressOf(0),
                dataOutAvailable = output.size.convert(),
                dataOutMoved = written.ptr,
            )
        }
        require(updateStatus == kCCSuccess) { "CCCryptorUpdate failed: $updateStatus" }
        require(written.value.toInt() == input.size) {
            "CCCryptorUpdate consumed ${written.value} of ${input.size} bytes"
        }

        val finalWritten = alloc<size_tVar>()
        val finalStatus = CCCryptorFinal(
            cryptorRef = cryptorRef.value,
            dataOut = null,
            dataOutAvailable = 0u,
            dataOutMoved = finalWritten.ptr,
        )
        require(finalStatus == kCCSuccess) { "CCCryptorFinal failed: $finalStatus" }
        output
    } finally {
        CCCryptorRelease(cryptorRef.value)
    }
}

internal actual fun secureRandomBytes(size: Int): ByteArray = memScoped {
    val buffer = allocArray<platform.posix.uint8_tVar>(size)
    val status = SecRandomCopyBytes(kSecRandomDefault, size.convert(), buffer)
    require(status == 0) { "SecRandomCopyBytes failed: $status" }
    buffer.readBytes(size)
}
