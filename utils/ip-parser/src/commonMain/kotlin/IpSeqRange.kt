/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.ipparser

interface IpSeqRange {
    fun contains(address: String): Boolean

    companion object {
        /**
         * Parse the [ipSeqPattern]. If the pattern is invalid, return null.
         * @throws IllegalArgumentException if the pattern is invalid.
         */
        fun parse(ipSeqPattern: String): IpSeqRange = IpSeqRange(ipSeqPattern)
    }
}

/**
 * Creates a new IpSeqRange instance using the [ipSeqPattern].
 * @throws IllegalArgumentException if the pattern is invalid.
 */
internal expect fun IpSeqRange(ipSeqPattern: String): IpSeqRange
