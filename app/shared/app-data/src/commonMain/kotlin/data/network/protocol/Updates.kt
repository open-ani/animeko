/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network.protocol

import kotlinx.serialization.Serializable


@Serializable
data class ReleaseUpdatesResponse(
    val versions: List<String>
)

@Serializable
data class ReleaseUpdatesDetailedResponse(
    val updates: List<UpdateInfo>
)

@Serializable
data class UpdateInfo(
    val version: String,
    val downloadUrlAlternatives: List<String>,
    val publishTime: Long, // seconds
    val description: String,
)

@Serializable // do not change field name, used both in app and server
enum class ReleaseClass {
    /**
     * 每日构建
     */
    ALPHA,

    /**
     * 测试版
     */
    BETA,

    /**
     * Release Candidate
     */
    RC, // 根据投票结果, 无人选择 RC, 故去除. 只有 3.0.0 有 rc, 3.0.0 正式版起没有

    /**
     * 稳定版
     */
    STABLE;

    override fun toString(): String {
        return this.name.lowercase()
    }

    fun moreStableThan(other: ReleaseClass): Boolean {
        return this.ordinal >= other.ordinal
    }

    companion object {
        /**
         * 在客户端启用了的项目
         */
        val enabledEntries by lazy {
            entries.filter { it != RC }.sortedDescending()
        }

        fun fromStringOrNull(value: String): ReleaseClass? {
            return value.let {
                entries.firstOrNull { it.toString() == value.lowercase() }
            }
        }
    }
}