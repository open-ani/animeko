/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player

/** 每次加载独立发布结果；同一资源重试或相同错误也属于不同尝试，按实例身份区分。 */
sealed interface MediaLoadResult {
    val mediaId: String

    class Ready(override val mediaId: String) : MediaLoadResult
    class Failed(override val mediaId: String, val state: VideoLoadingState.Failed) : MediaLoadResult
}
