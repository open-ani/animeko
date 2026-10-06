/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.danmaku

import me.him188.ani.danmaku.api.DanmakuServiceId

/**
 * 弹幕来源的主要文字体系.
 *
 * 转换链 (简繁/地区词汇) 的方向取决于来源文字与目标文字两者,
 * 例如同样转换到简体, 简体来源无需转换, 而台湾来源应使用 `tw2sp` (含词汇转换),
 * 参见 [resolveSokkuriConfig].
 */
enum class DanmakuSourceScript {
    SIMPLIFIED,
    TRADITIONAL,
    TAIWAN,
    HONG_KONG,
    ;
}

/**
 * 各弹幕来源的主要文字体系. 未收录的来源默认为 [DanmakuSourceScript.SIMPLIFIED].
 *
 * 巴哈弹幕以台湾正体及台湾惯用词汇为主, 其余当前来源以简体为主.
 */
fun DanmakuServiceId.defaultSourceScript(): DanmakuSourceScript = when (this) {
    DanmakuServiceId.Baha -> DanmakuSourceScript.TAIWAN
    else -> DanmakuSourceScript.SIMPLIFIED
}
