/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import me.him188.ani.app.domain.episode.EpisodeSession
import me.him188.ani.app.domain.media.selector.MediaSelectorEventSavePreferenceUseCase
import org.koin.core.Koin

/**
 * 在数据源选择器中更新选项后, 保存用户的偏好设置
 */
class SaveMediaPreferenceExtension(
    private val context: PlayerExtensionContext,
    koin: Koin
) : PlayerExtension("SaveMediaPreference") {
    private val mediaSelectorEventSavePreferenceUseCase: MediaSelectorEventSavePreferenceUseCase by koin.inject()
    override fun onStart(
        episodeSession: EpisodeSession,
        backgroundTaskScope: ExtensionBackgroundTaskScope
    ) {
        backgroundTaskScope.launch("SaveMediaPreference") {
            context.sessionFlow.flatMapLatest { it.fetchSelectFlow }.collectLatest { bundle ->
                if (bundle == null) return@collectLatest
                mediaSelectorEventSavePreferenceUseCase(bundle.mediaSelector, context.subjectId)
            }
        }
    }

    companion object : EpisodePlayerExtensionFactory<SaveMediaPreferenceExtension> {
        override fun create(context: PlayerExtensionContext, koin: Koin): SaveMediaPreferenceExtension {
            return SaveMediaPreferenceExtension(context, koin)
        }
    }
}