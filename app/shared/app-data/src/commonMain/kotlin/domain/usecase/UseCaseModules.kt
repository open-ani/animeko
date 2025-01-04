/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.usecase

import me.him188.ani.app.domain.danmaku.SearchDanmakuUseCase
import me.him188.ani.app.domain.danmaku.SearchDanmakuUseCaseImpl
import me.him188.ani.app.domain.danmaku.SetDanmakuEnabledUseCase
import me.him188.ani.app.domain.danmaku.SetDanmakuEnabledUseCaseImpl
import me.him188.ani.app.domain.episode.CreateMediaFetchSelectBundleFlowUseCase
import me.him188.ani.app.domain.episode.CreateMediaFetchSelectBundleFlowUseCaseImpl
import me.him188.ani.app.domain.episode.GetEpisodeCollectionInfoFlowUseCase
import me.him188.ani.app.domain.episode.GetEpisodeCollectionInfoFlowUseCaseImpl
import me.him188.ani.app.domain.episode.GetEpisodeCollectionTypeUseCase
import me.him188.ani.app.domain.episode.GetEpisodeCollectionTypeUseCaseImpl
import me.him188.ani.app.domain.episode.GetSubjectEpisodeInfoBundleFlowUseCase
import me.him188.ani.app.domain.episode.GetSubjectEpisodeInfoBundleFlowUseCaseImpl
import me.him188.ani.app.domain.episode.SetEpisodeCollectionTypeUseCase
import me.him188.ani.app.domain.episode.SetEpisodeCollectionTypeUseCaseImpl
import me.him188.ani.app.domain.media.selector.MediaSelectorAutoSelectUseCase
import me.him188.ani.app.domain.media.selector.MediaSelectorAutoSelectUseCaseImpl
import me.him188.ani.app.domain.media.selector.MediaSelectorEventSavePreferenceUseCase
import me.him188.ani.app.domain.media.selector.MediaSelectorEventSavePreferenceUseCaseImpl
import me.him188.ani.app.domain.mediasource.GetWebMediaSourceInstanceFlowUseCase
import me.him188.ani.app.domain.mediasource.GetWebMediaSourceInstanceFlowUseCaseImpl
import me.him188.ani.app.domain.settings.GetDanmakuRegexFilterListFlowUseCase
import me.him188.ani.app.domain.settings.GetDanmakuRegexFilterListFlowUseCaseImpl
import me.him188.ani.app.domain.settings.GetMediaSelectorSettingsFlowUseCase
import me.him188.ani.app.domain.settings.GetMediaSelectorSettingsFlowUseCaseImpl
import me.him188.ani.app.domain.settings.GetVideoScaffoldConfigUseCase
import me.him188.ani.app.domain.settings.GetVideoScaffoldConfigUseCaseImpl
import org.koin.core.KoinApplication
import org.koin.dsl.module
import org.koin.mp.KoinPlatform

fun KoinApplication.useCaseModules() = module {
    single<GetEpisodeCollectionInfoFlowUseCase> { GetEpisodeCollectionInfoFlowUseCaseImpl() }
    single<SearchDanmakuUseCase> { SearchDanmakuUseCaseImpl() }
    single<GetDanmakuRegexFilterListFlowUseCase> { GetDanmakuRegexFilterListFlowUseCaseImpl() }
    single<MediaSelectorAutoSelectUseCase> { MediaSelectorAutoSelectUseCaseImpl() }
    single<MediaSelectorEventSavePreferenceUseCase> { MediaSelectorEventSavePreferenceUseCaseImpl }
    single<GetWebMediaSourceInstanceFlowUseCase> { GetWebMediaSourceInstanceFlowUseCaseImpl() }
    single<GetSubjectEpisodeInfoBundleFlowUseCase> { GetSubjectEpisodeInfoBundleFlowUseCaseImpl() }
    single<CreateMediaFetchSelectBundleFlowUseCase> { CreateMediaFetchSelectBundleFlowUseCaseImpl() }
    single<GetMediaSelectorSettingsFlowUseCase> { GetMediaSelectorSettingsFlowUseCaseImpl }
    single<GetVideoScaffoldConfigUseCase> { GetVideoScaffoldConfigUseCaseImpl }
    single<SetDanmakuEnabledUseCase> { SetDanmakuEnabledUseCaseImpl(koin) }
    single<SetEpisodeCollectionTypeUseCase> { SetEpisodeCollectionTypeUseCaseImpl(koin) }
    single<GetEpisodeCollectionTypeUseCase> { GetEpisodeCollectionTypeUseCaseImpl(koin) }
}

val GlobalKoin get() = KoinPlatform.getKoin()
