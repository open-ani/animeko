/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player

import androidx.compose.runtime.Immutable
import me.him188.ani.app.domain.media.resolver.MediaResolver
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.datasources.api.Media

@Immutable
sealed interface VideoLoadingState {
    sealed interface Progressing : VideoLoadingState

    /**
     * 等待选择 [Media] ([MediaSelector])
     */
    data object Initial : VideoLoadingState

    /**
     * 在解析磁力链/寻找文件
     */
    data object ResolvingSource : VideoLoadingState, Progressing

    /**
     * WEB: 已经成功解析到 m3u8 链接
     * BT: 要解析磁力链, 查询元数据
     */
    data class DecodingData(
        val isBt: Boolean,
    ) : VideoLoadingState, Progressing

    /**
     * 文件成功找到
     */
    data class Succeed(
        val isBt: Boolean,
    ) : VideoLoadingState, Progressing

    sealed class Failed : VideoLoadingState
    data object ResolutionTimedOut : Failed()
    data object NetworkError : Failed()
    data object Cancelled : Failed()

    /**
     * 不支持的媒体, 或者说是未启用支持该媒体的 [MediaResolver]
     */
    data object UnsupportedMedia : Failed()
    data object NoMatchingFile : Failed()
    data class UnknownError(
        val cause: Throwable,
    ) : Failed()
}
