/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("NOTHING_TO_INLINE", "KotlinRedundantDiagnosticSuppress", "unused")

package me.him188.ani.datasources.api

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import me.him188.ani.datasources.api.PackedDate.Companion.Invalid
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmField
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmStatic
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * 一个日期, 支持年月日. 支持表示无效状态 [Invalid].
 */
@JvmInline
@Serializable
value class PackedDate(
    /**
     * 高 16 位为年, 中 8 位为月, 低 8 位为日
     */
    @JvmField val packed: Int // room access it
) : Comparable<PackedDate> {
    inline val isValid: Boolean get() = packed != Int.MAX_VALUE
    inline val isInvalid: Boolean get() = packed == Int.MAX_VALUE

    /**
     * 获取年份, `0..9999`. 当无效 [Invalid] 时返回 0.
     */
    inline val year: Int get() = if (isValid) DatePacker.unpack1(packed) else 0

    /**
     * 获取原始月份信息, `1..12`. 当无效 [Invalid] 时返回 0.
     */
    inline val month: Int get() = if (isValid) DatePacker.unpack2(packed) else 0

    /**
     * 获取日期, `0..31`. 当无效 [Invalid] 时返回 0.
     */
    inline val day: Int get() = if (isValid) DatePacker.unpack3(packed) else 0

    companion object {
        /**
         * 表示一个无效时间.
         */
        @JvmStatic
        val Invalid = PackedDate(Int.MAX_VALUE)


        /**
         * @param date `2024-05-18`. 允许的日期范围为 `0000-01-01` 到 `9999-12-31`. 仅检查时间格式, 不检查时间合法性.
         * 因此 2 月 31 日也被视为是正确的.
         * @return 当 [date] 格式不正确时返回 [Invalid]
         */
        fun parseFromDate(date: String): PackedDate {
            val split = date.split("-")
            if (split.size < 3) return Invalid
            return PackedDate(
                split[0].toIntOrNull() ?: return Invalid,
                split[1].toIntOrNull() ?: return Invalid,
                split[2].toIntOrNull() ?: return Invalid,
            )
        }

        private val UTC8 = TimeZone.of("UTC+8")

        fun now(): PackedDate {
            val timeZone = UTC8 // bangumi 是固定 UTC+8
            val calendar = Clock.System.now().toLocalDateTime(timeZone)

            val year = calendar.year
            val month = calendar.monthNumber
            val day = calendar.dayOfMonth
            return PackedDate(year, month, day)
        }
    }

    override fun compareTo(other: PackedDate): Int = packed.compareTo(other.packed) // trivial!

    override fun toString(): String {
        return if (isInvalid) "Invalid" else "$year-$month-$day"
    }
}

fun PackedDate.toStringExcludingSameYear(): String = when {
    isInvalid -> toString()
    year == PackedDate.now().year -> "$month-$day"
    else -> toString()
}

fun PackedDate.toLocalDateOrNull(): LocalDate? {
    if (isInvalid) return null
    return LocalDate(year, month, day)
}

fun PackedDate?.isNullOrInvalid(): Boolean = this == null || this.isInvalid

inline fun PackedDate.ifInvalid(block: () -> PackedDate): PackedDate {
    contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
    return if (isInvalid) block() else this
}

//private val UTC8 = kotlinx.datetime.TimeZone.of("UTC+8")

//@Stable
//fun PackedDate.toEpochMillis(): Long {
//    return LocalDate(year, month, day).atStartOfDayIn(UTC8).toEpochMilliseconds()
//}

/**
 * 获取月份所在季度的第一个月, `1, 4, 7, 10`.
 */
inline val PackedDate.seasonMonth: Int
    get() = when (month) {
        12, in 1..2 -> 1
        in 3..5 -> 4
        in 6..8 -> 7
        in 9..11 -> 10
        else -> 0
    }

/**
 * 计算两个日期的间隔. 当任一日期无效时返回 [Duration.INFINITE].
 */
operator fun PackedDate.minus(other: PackedDate): Duration {
    if (this.isInvalid || other.isInvalid) return Duration.INFINITE

    val thisDate = LocalDate(this.year, this.month, this.day)
    val otherDate = LocalDate(other.year, other.month, other.day)

    val thisInstant = thisDate.atStartOfDayIn(TimeZone.UTC)
    val otherInstant = otherDate.atStartOfDayIn(TimeZone.UTC)

    return (thisInstant.toEpochMilliseconds() - otherInstant.toEpochMilliseconds()).milliseconds
}

inline fun PackedDate(
    year: Int,
    month: Int,
    day: Int,
): PackedDate = if (year in 0..9999 && month in 1..12 && day in 1..31) {
    PackedDate(DatePacker.pack(year, month, day))
} else {
    Invalid // invalid
}

@Suppress("NOTHING_TO_INLINE")
@PublishedApi
internal object DatePacker {
    inline fun pack(
        val1: Int, // short
        val2: Int, // byte
        val3: Int, // byte
    ): Int {
        return val1.shl(16) or val2.shl(8) or val3
    }

    inline fun unpack1(value: Int): Int = value.shr(16).and(0xFFFF)
    inline fun unpack2(value: Int): Int = value.shr(8).and(0xFF)
    inline fun unpack3(value: Int): Int = value.and(0xFF)
}
