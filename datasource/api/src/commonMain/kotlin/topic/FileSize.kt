/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("NOTHING_TO_INLINE", "KotlinRedundantDiagnosticSuppress")

package me.him188.ani.datasources.api.topic

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.reduce
import kotlinx.serialization.Serializable
import me.him188.ani.datasources.api.topic.FileSize.Companion.Unspecified
import me.him188.ani.datasources.api.topic.FileSize.Companion.Zero
import me.him188.ani.utils.platform.format1f
import kotlin.jvm.JvmField
import kotlin.jvm.JvmInline

/**
 * 表示一个数据大小.
 *
 * ```
 * val size = 233.megaBytes
 * assertEquals("233 MB", size.toString())
 * ```
 *
 * 在进行操作时, 如 [plus], [Unspecified] 会被看作为 0.
 *
 * @see Unspecified
 * @see Zero
 */
@JvmInline
@Serializable
value class FileSize private constructor(
    @JvmField @PublishedApi internal val rawValue: Long,
) {
    inline val inBytes: Long get() = if (isUnspecified) 0L else rawValue

    inline val isUnspecified: Boolean get() = this == Unspecified

    inline val inKiloBytes: Float get() = inBytes / 1024f
    inline val inMegaBytes: Float get() = inKiloBytes / 1024f
    inline val inGigaBytes: Float get() = inMegaBytes / 1024f

    inline operator fun plus(another: FileSize): FileSize = (this.inBytes + another.inBytes).bytes
    inline operator fun minus(another: FileSize): FileSize = (this.inBytes - another.inBytes).bytes

    inline operator fun times(another: Int): FileSize = (this.inBytes * another).bytes
    inline operator fun times(another: Long): FileSize = (this.inBytes * another).bytes
    inline operator fun times(another: Float): FileSize = (this.inBytes * another).bytes

    inline operator fun unaryPlus(): FileSize = this
    inline operator fun unaryMinus(): FileSize = (-inBytes).bytes

    companion object {
        val Long.bytes: FileSize
            get() {
                check(this != Long.MIN_VALUE) { "Long.MIN_VALUE is not allowed to be a FileSize" }
                return FileSize(this)
            }

        inline val Long.kiloBytes: FileSize get() = (this * 1024).bytes
        inline val Long.megaBytes: FileSize get() = (this * 1024).kiloBytes
        inline val Long.gigaBytes: FileSize get() = (this * 1024).megaBytes

        inline val Int.bytes: FileSize get() = toLong().bytes
        inline val Int.kiloBytes: FileSize get() = (this * 1024).bytes
        inline val Int.megaBytes: FileSize get() = (this * 1024).kiloBytes
        inline val Int.gigaBytes: FileSize get() = (this * 1024).megaBytes

        inline val Double.bytes: FileSize get() = this.toLong().bytes
        inline val Double.kiloBytes: FileSize get() = (this * 1024).bytes
        inline val Double.megaBytes: FileSize get() = (this * 1024).kiloBytes
        inline val Double.gigaBytes: FileSize get() = (this * 1024).megaBytes

        inline val Float.bytes: FileSize get() = this.toLong().bytes
        inline val Float.kiloBytes: FileSize get() = (this * 1024).bytes
        inline val Float.megaBytes: FileSize get() = (this * 1024).kiloBytes
        inline val Float.gigaBytes: FileSize get() = (this * 1024).megaBytes

        val Zero = 0.bytes

        /**
         * 特殊值
         */
        val Unspecified = FileSize(Long.MIN_VALUE)
    }

    @Suppress("DefaultLocale")
    override fun toString(): String {
        val gigaBytes = this.inGigaBytes
        if (gigaBytes >= 1) {
            return "${String.format1f(gigaBytes)} GB"
        }
        val megaBytes = this.inMegaBytes
        if (megaBytes >= 1) {
            return "${String.format1f(megaBytes)} MB"
        }
        val kiloBytes = this.inKiloBytes
        if (kiloBytes >= 1) {
            return "${String.format1f(kiloBytes)} KB"
        }
        return "${this.inBytes} B"
    }
}

suspend inline fun Flow<FileSize>.sum() = reduce { acc, value -> acc + value }
inline fun Iterable<FileSize>.sum() = reduce { acc, value -> acc + value }
inline fun Array<FileSize>.sum() = fold(Zero) { acc, value -> acc + value }

val flowOfFileSizeZero = flowOf(Zero)
