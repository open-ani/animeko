/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.flow.debounce
import me.him188.ani.app.data.models.preference.MediaPreference

inline val MediaSelector.eventHandling get() = MediaSelectorEventHandlers(this)

class MediaSelectorEventHandlers(
    private val mediaSelector: MediaSelector,
) {
    /**
     * 保存本次会话用户更新的资源选择偏好设置
     */
    suspend fun savePreferenceOnSelect(
        save: suspend (MediaPreference) -> Unit,
    ) {
        mediaSelector.events.onChangePreference.debounce(1000).collect {
            save(it)
        }
    }
}
