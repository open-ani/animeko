/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
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
     * 当用户手动选择偏好使用某个 Web 源的事件
     */
    val onPreferWebSource: Flow<PreferWebSourceEvent>

}

/**
 * 表示一次媒体选择事件的详细信息。
 *
 * 此事件在媒体成功选择（无论自动或手动）后发出，用于向外部广播选择行为。
 *
 * @property media 当前被选中的媒体项，可能为 null。
 * @property subtitleLanguageId 当前选择的字幕语言 ID，可能为 null。
 * @property previousMedia 上一次被选中的媒体项。
 */
data class SelectEvent(
    val media: Media?,
    val subtitleLanguageId: String?,
    val previousMedia: Media?,
)

/**
 * 用户选择了某个 Web 源, 表示用户希望以后都使用这个源来播放此条目.
 * 
 * @see MediaSelectorEvents
 */
data class PreferWebSourceEvent(
    val subjectId: Int,
    val mediaSourceId: String
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
    override val onPreferWebSource: MutableSharedFlow<PreferWebSourceEvent> =
        MutableSharedFlow(replay, extraBufferCapacity, onBufferOverflow)
}
