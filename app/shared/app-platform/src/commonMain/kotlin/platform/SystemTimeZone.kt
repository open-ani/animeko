/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import kotlinx.datetime.TimeZone

/**
 * 设备当前的系统时区.
 *
 * 全 App 读取系统时区的唯一入口: 日期时间显示使用用户选择的时区 (见 `TimeZoneProvider`),
 * 只有确实需要设备真实时区的逻辑才直接调用本函数.
 */
fun currentSystemTimeZone(): TimeZone = TimeZone.currentSystemDefault()
