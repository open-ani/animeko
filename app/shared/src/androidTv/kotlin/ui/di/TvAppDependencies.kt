/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.di

import me.him188.ani.app.data.network.AutoSkipRepository
import me.him188.ani.app.data.network.BangumiRelatedPeopleService
import me.him188.ani.app.data.network.BangumiSummaryService
import me.him188.ani.app.data.network.TmdbImageService
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.episode.EpisodeCommentRepository
import me.him188.ani.app.data.repository.media.SelectorMediaSourceEpisodeCacheRepository
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.data.repository.subject.SubjectSearchRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.data.repository.user.UserRepository
import me.him188.ani.app.domain.danmaku.DanmakuRepository
import me.him188.ani.app.domain.mediasource.web.captcha.WebSessionManager
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.domain.settings.GetDanmakuRegexFilterListFlowUseCase
import me.him188.ani.app.domain.watchtogether.PlaybackAutomationGate
import me.him188.ani.app.domain.watchtogether.WatchTogetherManager
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsStateFactory
import org.koin.core.Koin
import org.openani.mediamp.MediampPlayerFactory

/** Application services resolved before composition, passed only to ViewModel constructors. */
class TvAppDependencies(
    // Shared state holders and playback sessions still take the application Koin instance.
    val koin: Koin,
    val userRepository: UserRepository,
    val subjectCollectionRepository: SubjectCollectionRepository,
    val tmdbImageService: TmdbImageService,
    val bangumiSummaryService: BangumiSummaryService,
    val subjectSearchRepository: SubjectSearchRepository,
    val settingsRepository: SettingsRepository,
    val subjectDetailsStateFactory: SubjectDetailsStateFactory,
    val playerStateFactory: MediampPlayerFactory<*>,
    val episodeCollectionRepository: EpisodeCollectionRepository,
    val danmakuRepository: DanmakuRepository,
    val getDanmakuRegexFilterListFlowUseCase: GetDanmakuRegexFilterListFlowUseCase,
    val episodeCommentRepository: EpisodeCommentRepository,
    val bangumiRelatedPeopleService: BangumiRelatedPeopleService,
    val autoSkipRepository: AutoSkipRepository,
    val selectorEpisodeCacheRepository: SelectorMediaSourceEpisodeCacheRepository,
    val webSessionManager: WebSessionManager,
    val playbackAutomationGate: PlaybackAutomationGate,
    val watchTogetherManager: WatchTogetherManager,
    val sessionStateProvider: SessionStateProvider,
) {
    companion object {
        fun fromKoin(koin: Koin): TvAppDependencies = TvAppDependencies(
            koin = koin,
            userRepository = koin.get(),
            subjectCollectionRepository = koin.get(),
            tmdbImageService = koin.get(),
            bangumiSummaryService = koin.get(),
            subjectSearchRepository = koin.get(),
            settingsRepository = koin.get(),
            subjectDetailsStateFactory = koin.get(),
            playerStateFactory = koin.get(),
            episodeCollectionRepository = koin.get(),
            danmakuRepository = koin.get(),
            getDanmakuRegexFilterListFlowUseCase = koin.get(),
            episodeCommentRepository = koin.get(),
            bangumiRelatedPeopleService = koin.get(),
            autoSkipRepository = koin.get(),
            selectorEpisodeCacheRepository = koin.get(),
            webSessionManager = koin.get(),
            playbackAutomationGate = koin.get(),
            watchTogetherManager = koin.get(),
            sessionStateProvider = koin.get(),
        )
    }
}
