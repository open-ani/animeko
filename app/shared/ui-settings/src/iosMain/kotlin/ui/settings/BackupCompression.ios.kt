/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * Use of this source code is governed by the GNU AGPLv3 license.
 */

package me.him188.ani.app.ui.settings

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cValue
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toCValues
import platform.zlib.Z_DEFAULT_COMPRESSION
import platform.zlib.Z_DEFAULT_STRATEGY
import platform.zlib.Z_DEFLATED
import platform.zlib.Z_FINISH
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.deflate
import platform.zlib.deflateEnd
import platform.zlib.deflateInit2
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2
import platform.zlib.z_stream

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual fun compressBackup(content: ByteArray): ByteArray {
    if (content.isEmpty()) return ByteArray(0)
    memScoped {
        val stream = alloc<z_stream>()
        // 15 + 16 specifies standard gzip format
        val initResult = deflateInit2(stream.ptr, Z_DEFAULT_COMPRESSION, Z_DEFLATED, 15 + 16, 8, Z_DEFAULT_STRATEGY)
        check(initResult == Z_OK) { "deflateInit2 failed with code $initResult" }

        try {
            val chunkSize = 16384
            val outChunk = ByteArray(chunkSize)
            val result = mutableListOf<ByteArray>()

            stream.avail_in = content.size.toUInt()
            stream.next_in = content.refTo(0).getPointer(this).reinterpret()

            do {
                stream.avail_out = chunkSize.toUInt()
                stream.next_out = outChunk.refTo(0).getPointer(this).reinterpret()
                val ret = deflate(stream.ptr, Z_FINISH)
                check(ret == Z_OK || ret == Z_STREAM_END) { "deflate failed with code $ret" }
                val produced = chunkSize - stream.avail_out.toInt()
                if (produced > 0) {
                    result.add(outChunk.copyOf(produced))
                }
            } while (ret != Z_STREAM_END)

            val totalSize = result.sumOf { it.size }
            val output = ByteArray(totalSize)
            var offset = 0
            for (chunk in result) {
                chunk.copyInto(output, offset)
                offset += chunk.size
            }
            return output
        } finally {
            deflateEnd(stream.ptr)
        }
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual fun decompressBackup(content: ByteArray, maxBytes: Int): ByteArray {
    require(content.isNotEmpty()) { "Backup data is empty" }
    memScoped {
        val stream = alloc<z_stream>()
        // 15 + 32 specifies automatic gzip/zlib detection
        val initResult = inflateInit2(stream.ptr, 15 + 32)
        check(initResult == Z_OK) { "inflateInit2 failed with code $initResult" }

        try {
            val chunkSize = 16384
            val outChunk = ByteArray(chunkSize)
            val result = mutableListOf<ByteArray>()

            stream.avail_in = content.size.toUInt()
            stream.next_in = content.refTo(0).getPointer(this).reinterpret()

            var totalSize = 0
            do {
                stream.avail_out = chunkSize.toUInt()
                stream.next_out = outChunk.refTo(0).getPointer(this).reinterpret()
                // Z_BUF_ERROR here means the input ended before the gzip stream did.
                val ret = inflate(stream.ptr, Z_NO_FLUSH)
                check(ret == Z_OK || ret == Z_STREAM_END) { "inflate failed with code $ret" }
                val produced = chunkSize - stream.avail_out.toInt()
                totalSize += produced
                check(totalSize <= maxBytes) { "Backup expands beyond $maxBytes bytes" }
                if (produced > 0) result.add(outChunk.copyOf(produced))
            } while (ret != Z_STREAM_END)

            val output = ByteArray(totalSize)
            var offset = 0
            for (chunk in result) {
                chunk.copyInto(output, offset)
                offset += chunk.size
            }
            return output
        } finally {
            inflateEnd(stream.ptr)
        }
    }
}
