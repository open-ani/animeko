/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.fetch

import androidx.compose.runtime.Stable

/**
 * @see MediaSourceFetchResult.state
 */
@Stable
sealed class MediaSourceFetchState {
    data object Idle : MediaSourceFetchState()

    /**
     * 被禁用, 因此不会主动发起请求. 仍然可以通过 [MediaSourceFetchResult.restart] 发起请求.
     */
    data object Disabled : MediaSourceFetchState()

    data object Working : MediaSourceFetchState()

    sealed class Completed : MediaSourceFetchState() {
        internal abstract val id: Int // restartCount
    }

    data class Succeed(
        override val id: Int, // restartCount
    ) : Completed()

    /**
     * The data source upstream has failed. E.g. a network request failed.
     */
    data class Failed(
        val cause: Throwable, override val id: Int,
    ) : Completed()

    /**
     * Failed because the flow collector has thrown an exception (and stopped collection)
     */
    data class Abandoned(
        val cause: Throwable, override val id: Int,
    ) : Completed()
}

val MediaSourceFetchState.isWorking get() = this is MediaSourceFetchState.Working
val MediaSourceFetchState.isDisabled get() = this is MediaSourceFetchState.Disabled
val MediaSourceFetchState.isFinal get() = this is MediaSourceFetchState.Completed || this is MediaSourceFetchState.Disabled
val MediaSourceFetchState.isFailedOrAbandoned get() = this is MediaSourceFetchState.Failed || this is MediaSourceFetchState.Abandoned
