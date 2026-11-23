/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.flow.first
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.mediasource.GetMediaSelectorSourceTiersUseCase
import me.him188.ani.app.domain.mediasource.GetPreferredWebMediaSourceUseCase
import me.him188.ani.app.domain.settings.GetMediaSelectorSettingsFlowUseCase
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.domain.usecase.UseCase
import me.him188.ani.datasources.api.source.MediaSourceKind
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

interface MediaSelectorAutoSelectUseCase : UseCase {
    /**
     * @return 这次自动选择的结果.
     */
    suspend operator fun invoke(session: MediaFetchSession, mediaSelector: MediaSelector): MediaAutoSelectOutcome
}

class MediaSelectorAutoSelectUseCaseImpl(
    private val koin: Koin = GlobalKoin,
) : MediaSelectorAutoSelectUseCase, KoinComponent {
    private val getMediaSelectorSettingsFlow: GetMediaSelectorSettingsFlowUseCase by inject()
    private val getMediaSelectorSourceTiers: GetMediaSelectorSourceTiersUseCase by inject()
    private val getPreferredWebMediaSource: GetPreferredWebMediaSourceUseCase by inject()

    override suspend fun invoke(session: MediaFetchSession, mediaSelector: MediaSelector): MediaAutoSelectOutcome {
        val settings = getMediaSelectorSettingsFlow().first()
        // #355 Restore a temporarily enabled source using the merged media preference.
        // Enable it before selecting: a disabled source counts as finished, and once every Web source has finished
        // the selector stops waiting for deadlines. The selector needs the same preference before deciding anyway.
        if (settings.autoEnableLastSelected) {
            val sourceId = mediaSelector.mediaSourceId.finalSelected.first()
            session.mediaSourceResults.firstOrNull { it.mediaSourceId == sourceId }?.enable()
        }

        val subjectId = session.request.first().subjectId.toIntOrNull()
        return MediaAutoSelector(mediaSelector).select(
            session,
            MediaAutoSelector.Config(
                preferredSourceId = subjectId?.let { getPreferredWebMediaSource(it).first() },
                web = if (settings.preferKind == MediaSourceKind.WEB) MediaAutoSelector.Web(
                    sourceTiers = getMediaSelectorSourceTiers().first(),
                    fastSelect = settings.fastSelectWebKind,
                    exactMatchAfter = settings.fastSelectWebLowTierToleranceDuration,
                ) else null,
                fallbackToOtherKinds = true,
            ),
        )
    }

    override fun getKoin(): Koin = koin
}
