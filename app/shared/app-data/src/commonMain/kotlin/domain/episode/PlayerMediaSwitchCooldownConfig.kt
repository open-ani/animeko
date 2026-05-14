/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

/**
 * Compatibility workaround for player kernels that may freeze if a new media is loaded
 * immediately after [PlayerSession.stopPlayer].
 */
class PlayerMediaSwitchCooldownConfig(
    val delayMillis: Long = 0L,
) {
    init {
        require(delayMillis >= 0) { "delayMillis must be non-negative" }
    }
}
