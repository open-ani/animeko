/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.datasources.api.Media

/**
 * @see MediaSelector.events
 * @see MediaSelector.eventHandling
 */
interface MediaSelectorEvents {
    val onSelect: Flow<SelectEvent>

    /**
     * 此事件会在 media 切换前 emit.
     * 注意, 只要 [MediaSelector.select] 被调用就会广播这个事件. 这也就包括了重复选择同一个 media. 
     * 所以在广播这个事件后不一定会广播 [onSelect].
     */
    val onBeforeSelect: Flow<SelectEvent>

    /**
     * 用户偏好发生变化, 这可能是 [MediaSelector.select], 也可能是 [MediaPreferenceItem.prefer].
     *
     * flow 的值为新的用户设置
     */
    val onChangePreference: Flow<MediaPreference>
    
    /**
     * 媒体被加入黑名单.
     * 当用户选择某媒体后，应该将之前选择的媒体（即 fastSelect 的结果）加入黑名单.
     * 
     * flow 的值为加入黑名单的媒体.
     */
    val onBlackListMedia: Flow<Media>
}

data class SelectEvent(
    val media: Media?,
    val subtitleLanguageId: String?,
)

class MutableMediaSelectorEvents(
    replay: Int = 0,
    extraBufferCapacity: Int = 1,
    onBufferOverflow: BufferOverflow = BufferOverflow.DROP_OLDEST,
) : MediaSelectorEvents {
    override val onSelect: MutableSharedFlow<SelectEvent> =
        MutableSharedFlow(replay, extraBufferCapacity, onBufferOverflow)
    override val onBeforeSelect: MutableSharedFlow<SelectEvent> =
        MutableSharedFlow(replay, extraBufferCapacity, onBufferOverflow)
    override val onChangePreference: MutableSharedFlow<MediaPreference> =
        MutableSharedFlow(replay, extraBufferCapacity, onBufferOverflow)
    override val onBlackListMedia: MutableSharedFlow<Media> =
        MutableSharedFlow(replay, extraBufferCapacity, onBufferOverflow)
}
