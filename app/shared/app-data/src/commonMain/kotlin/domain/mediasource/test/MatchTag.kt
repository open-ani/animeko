/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.test

import androidx.compose.runtime.Immutable


@Immutable
class MatchTag(
    val value: String,
    /**
     * 该标签表示一个缺失的项目. 例如缺失 EP.
     */
    val isMissing: Boolean = false,
    /**
     * 该标签是否匹配了用户的搜索条件.
     * - `true`: 满足了一个条件. UI 显示为紫色的 check
     * - `false`: 不满足条件. UI 显示为红色的 close
     * - `null`: 这不是一个搜索条件. UI 不会特别高亮此标签.
     */
    val isMatch: Boolean? = null,
)

class MatchTagsBuilder
@PublishedApi
internal constructor() {
    private val list = mutableListOf<MatchTag>()

    fun emit(value: String, isMissing: Boolean = false, isMatch: Boolean? = null) {
        list.add(MatchTag(value, isMissing, isMatch))
    }

    @PublishedApi
    internal fun build(): List<MatchTag> = list
}

inline fun buildMatchTags(builder: MatchTagsBuilder.() -> Unit): List<MatchTag> =
    MatchTagsBuilder().apply(builder).build()
