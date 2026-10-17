/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.danmaku.ui

import kotlinx.serialization.Serializable

/**
 * 弹幕文本的转换目标文字.
 *
 * 只表达"用户想用什么文字看", 不绑定具体的转换实现: 也不假设某个来源的文字体系
 * (一个来源里往往混着多种文字), 由 domain 层只按本目标选择转换链, 保证结果就是这里选定的文字.
 *
 * @since 4.9.0
 */
@Serializable
enum class DanmakuTextConversion {
    /**
     * 不转换, 按来源原始文字显示.
     */
    ORIGINAL,

    /**
     * 简体.
     */
    SIMPLIFIED,

    /**
     * 繁体 (通用).
     */
    TRADITIONAL,

    /**
     * 台湾正体 (含台湾惯用词汇).
     */
    TAIWAN,

    /**
     * 香港繁体 (含香港惯用词汇).
     */
    HONG_KONG,
    ;
}
