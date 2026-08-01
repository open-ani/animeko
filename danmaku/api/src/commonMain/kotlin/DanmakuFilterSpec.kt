/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.api

/**
 * 播放过程中随时可能变化的弹幕过滤配置.
 */
data class DanmakuFilterSpec(
    /**
     * 正则过滤. 匹配到任意一个的弹幕会被丢弃.
     */
    val regexPatterns: List<String> = emptyList(),
    /**
     * 简繁转换方式. 弹幕文本在加载时已经被转换过 ([DanmakuPreprocessConfig.zhConversion]),
     * 所以这里也要把 [regexPatterns] 转换成同一种字形, 否则用简体写的屏蔽词就拦不住繁体弹幕.
     */
    val zhConversion: ZhConversion = ZhConversion.NONE,
) {
    val isEmpty: Boolean get() = regexPatterns.isEmpty()

    companion object {
        val Empty = DanmakuFilterSpec()
    }
}
