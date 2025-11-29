/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.usecase

import me.him188.ani.app.data.repository.subject.GetEpisodeTypeFiltersUseCase
import me.him188.ani.app.data.repository.subject.GetEpisodeTypeFiltersUseCaseImpl
import me.him188.ani.app.data.repository.subject.SetSubjectCollectionTypeOrDeleteUseCase
import me.him188.ani.app.data.repository.subject.SetSubjectCollectionTypeOrDeleteUseCaseImpl
import me.him188.ani.app.domain.comment.PostCommentUseCase
import me.him188.ani.app.domain.comment.PostCommentUseCaseImpl
import me.him188.ani.app.domain.danmaku.SetDanmakuEnabledUseCase
import me.him188.ani.app.domain.danmaku.SetDanmakuEnabledUseCaseImpl
import me.him188.ani.app.domain.episode.CreateMediaFetchSelectBundleFlowUseCase
import me.him188.ani.app.domain.episode.CreateMediaFetchSelectBundleFlowUseCaseImpl
import me.him188.ani.app.domain.episode.GetAnimeScheduleFlowUseCase
import me.him188.ani.app.domain.episode.GetAnimeScheduleFlowUseCaseImpl
import me.him188.ani.app.domain.episode.GetEpisodeCollectionInfoFlowUseCase
import me.him188.ani.app.domain.episode.GetEpisodeCollectionInfoFlowUseCaseImpl
import me.him188.ani.app.domain.episode.GetEpisodeCollectionTypeUseCase
import me.him188.ani.app.domain.episode.GetEpisodeCollectionTypeUseCaseImpl
import me.him188.ani.app.domain.episode.GetSubjectEpisodeInfoBundleFlowUseCase
import me.him188.ani.app.domain.episode.GetSubjectEpisodeInfoBundleFlowUseCaseImpl
import me.him188.ani.app.domain.episode.GetSubjectRecommendationUseCase
import me.him188.ani.app.domain.episode.GetSubjectRecommendationUseCaseImpl
import me.him188.ani.app.domain.episode.SetEpisodeCollectionTypeUseCase
import me.him188.ani.app.domain.episode.SetEpisodeCollectionTypeUseCaseImpl
import me.him188.ani.app.domain.media.cache.DeleteCacheByCacheIdUseCase
import me.him188.ani.app.domain.media.cache.DeleteCacheByCacheIdUseCaseImpl
import me.him188.ani.app.domain.media.cache.DeleteCacheByEpisodeIdUseCase
import me.him188.ani.app.domain.media.cache.DeleteCacheByEpisodeIdUseCaseImpl
import me.him188.ani.app.domain.media.cache.DeleteCacheUseCase
import me.him188.ani.app.domain.media.cache.DeleteCacheUseCaseImpl
import me.him188.ani.app.domain.media.cache.GetMediaCacheUseCase
import me.him188.ani.app.domain.media.cache.GetMediaCacheUseCaseImpl
import me.him188.ani.app.domain.media.selector.GetPreferredMediaSourceSortingUseCase
import me.him188.ani.app.domain.media.selector.GetPreferredMediaSourceSortingUseCaseImpl
import me.him188.ani.app.domain.media.selector.MediaSelectorAutoSelectUseCase
import me.him188.ani.app.domain.media.selector.MediaSelectorAutoSelectUseCaseImpl
import me.him188.ani.app.domain.media.selector.MediaSelectorEventSavePreferenceUseCase
import me.him188.ani.app.domain.media.selector.MediaSelectorEventSavePreferenceUseCaseImpl
import me.him188.ani.app.domain.mediasource.GetMediaSelectorSourceTiersUseCase
import me.him188.ani.app.domain.mediasource.GetMediaSelectorSourceTiersUseCaseImpl
import me.him188.ani.app.domain.mediasource.GetWebMediaSourceInstanceFlowUseCase
import me.him188.ani.app.domain.mediasource.GetWebMediaSourceInstanceFlowUseCaseImpl
import me.him188.ani.app.domain.mediasource.instance.GetMediaSourceInstancesUseCase
import me.him188.ani.app.domain.mediasource.instance.GetMediaSourceInstancesUseCaseImpl
import me.him188.ani.app.domain.settings.GetDanmakuRegexFilterListFlowUseCase
import me.him188.ani.app.domain.settings.GetDanmakuRegexFilterListFlowUseCaseImpl
import me.him188.ani.app.domain.settings.GetMediaSelectorSettingsFlowUseCase
import me.him188.ani.app.domain.settings.GetMediaSelectorSettingsFlowUseCaseImpl
import me.him188.ani.app.domain.settings.GetMediaSelectorSettingsUseCase
import me.him188.ani.app.domain.settings.GetMediaSelectorSettingsUseCaseImpl
import me.him188.ani.app.domain.settings.GetVideoScaffoldConfigUseCase
import me.him188.ani.app.domain.settings.GetVideoScaffoldConfigUseCaseImpl
import org.koin.core.KoinApplication
import org.koin.dsl.module
import org.koin.mp.KoinPlatform

fun KoinApplication.useCaseModules() = module {
    single<GetEpisodeCollectionInfoFlowUseCase> { GetEpisodeCollectionInfoFlowUseCaseImpl() }
    single<GetDanmakuRegexFilterListFlowUseCase> { GetDanmakuRegexFilterListFlowUseCaseImpl() }
    single<MediaSelectorAutoSelectUseCase> { MediaSelectorAutoSelectUseCaseImpl() }
    single<MediaSelectorEventSavePreferenceUseCase> { MediaSelectorEventSavePreferenceUseCaseImpl }
    single<GetWebMediaSourceInstanceFlowUseCase> { GetWebMediaSourceInstanceFlowUseCaseImpl() }
    single<GetSubjectEpisodeInfoBundleFlowUseCase> { GetSubjectEpisodeInfoBundleFlowUseCaseImpl() }
    single<CreateMediaFetchSelectBundleFlowUseCase> { CreateMediaFetchSelectBundleFlowUseCaseImpl() }
    single<GetMediaSelectorSettingsFlowUseCase> { GetMediaSelectorSettingsFlowUseCaseImpl }
    single<GetVideoScaffoldConfigUseCase> { GetVideoScaffoldConfigUseCaseImpl }
    single<SetDanmakuEnabledUseCase> { SetDanmakuEnabledUseCaseImpl(koin) }
    single<SetSubjectCollectionTypeOrDeleteUseCase> { SetSubjectCollectionTypeOrDeleteUseCaseImpl(get(), get(), get()) }
    single<SetEpisodeCollectionTypeUseCase> { SetEpisodeCollectionTypeUseCaseImpl(koin) }
    single<GetEpisodeCollectionTypeUseCase> { GetEpisodeCollectionTypeUseCaseImpl(koin) }
    single<GetAnimeScheduleFlowUseCase> { GetAnimeScheduleFlowUseCaseImpl(get(), get()) }
    single<PostCommentUseCase> { PostCommentUseCaseImpl(get(), get()) }
    single<GetPreferredMediaSourceSortingUseCase> { GetPreferredMediaSourceSortingUseCaseImpl(get()) }
    single<GetMediaSelectorSourceTiersUseCase> { GetMediaSelectorSourceTiersUseCaseImpl(get()) }
    single<GetEpisodeTypeFiltersUseCase> { GetEpisodeTypeFiltersUseCaseImpl(get()) }
    single<GetMediaSelectorSettingsUseCase> { GetMediaSelectorSettingsUseCaseImpl(get()) }
    single<GetMediaSourceInstancesUseCase> { GetMediaSourceInstancesUseCaseImpl(get()) }
    single<GetSubjectRecommendationUseCase> { GetSubjectRecommendationUseCaseImpl(get()) }
    single<GetMediaCacheUseCase> { GetMediaCacheUseCaseImpl(get()) }
    single<DeleteCacheUseCase> { DeleteCacheUseCaseImpl(get(), get()) }
    single<DeleteCacheByCacheIdUseCase> { DeleteCacheByCacheIdUseCaseImpl(get(), get()) }
    single<DeleteCacheByEpisodeIdUseCase> { DeleteCacheByEpisodeIdUseCaseImpl(get(), get()) }
}

val GlobalKoin get() = KoinPlatform.getKoin()
