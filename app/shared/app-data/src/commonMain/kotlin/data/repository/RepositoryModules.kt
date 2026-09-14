/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.network.AniApiProvider
import me.him188.ani.app.data.network.AutoSkipRepository
import me.him188.ani.app.data.network.RecommendationRepository
import me.him188.ani.app.data.network.TrendsRepository
import me.him188.ani.app.data.persistent.dataStores
import me.him188.ani.app.data.persistent.database.AniDatabase
import me.him188.ani.app.data.repository.episode.AnimeScheduleRepository
import me.him188.ani.app.data.repository.episode.BangumiCommentRepository
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.episode.EpisodeCommentRepository
import me.him188.ani.app.data.repository.episode.EpisodeProgressRepository
import me.him188.ani.app.data.repository.media.EpisodePreferencesRepository
import me.him188.ani.app.data.repository.media.EpisodePreferencesRepositoryImpl
import me.him188.ani.app.data.repository.media.MediaSourceInstanceRepository
import me.him188.ani.app.data.repository.media.MediaSourceInstanceRepositoryImpl
import me.him188.ani.app.data.repository.media.MediaSourceSubscriptionRepository
import me.him188.ani.app.data.repository.media.MikanIndexCacheRepository
import me.him188.ani.app.data.repository.media.MikanIndexCacheRepositoryImpl
import me.him188.ani.app.data.repository.media.SelectorMediaSourceEpisodeCacheRepository
import me.him188.ani.app.data.repository.person.PersonCommentRepository
import me.him188.ani.app.data.repository.person.PersonDetailsRepository
import me.him188.ani.app.data.repository.player.DanmakuRegexFilterRepository
import me.him188.ani.app.data.repository.player.DanmakuRegexFilterRepositoryImpl
import me.him188.ani.app.data.repository.player.EpisodePlayHistoryRepository
import me.him188.ani.app.data.repository.player.EpisodePlayHistoryRepositoryImpl
import me.him188.ani.app.data.repository.player.EpisodeScreenshotRepository
import me.him188.ani.app.data.repository.player.PlaybackHistorySyncer
import me.him188.ani.app.data.repository.player.WhatslinkEpisodeScreenshotRepository
import me.him188.ani.app.data.repository.subject.BangumiMergeRepository
import me.him188.ani.app.data.repository.subject.BangumiSyncCommandRepository
import me.him188.ani.app.data.repository.subject.DefaultBangumiMergeRepository
import me.him188.ani.app.data.repository.subject.DefaultSubjectRelationsRepository
import me.him188.ani.app.data.repository.subject.FollowedSubjectsRepository
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepositoryImpl
import me.him188.ani.app.data.repository.subject.SubjectRelationsRepository
import me.him188.ani.app.data.repository.subject.SubjectSearchCompletionRepository
import me.him188.ani.app.data.repository.subject.SubjectSearchHistoryRepository
import me.him188.ani.app.data.repository.subject.SubjectSearchRepository
import me.him188.ani.app.data.repository.torrent.peer.PeerFilterSubscriptionRepository
import me.him188.ani.app.data.repository.user.PreferencesRepositoryImpl
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.data.repository.user.TokenRepository
import me.him188.ani.app.data.repository.user.UserRepository
import me.him188.ani.app.domain.bangumi.BangumiConflictChecker
import me.him188.ani.app.domain.danmaku.DanmakuRepository
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.ScopedHttpClientUserAgent
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.platform.Context
import me.him188.ani.app.platform.files
import me.him188.ani.utils.io.resolve
import org.koin.core.KoinApplication
import org.koin.core.scope.Scope
import org.koin.dsl.module

val Scope.aniApiProvider get() = get<AniApiProvider>()

private val Scope.database get() = get<AniDatabase>()
private val Scope.settingsRepository get() = get<SettingsRepository>()

@Suppress("UnusedReceiverParameter")
fun KoinApplication.repositoryModules(
    getContext: () -> Context,
    coroutineScope: CoroutineScope,
) = module {
    single<UserRepository> {
        UserRepository(
            getContext().dataStores.selfInfoStore,
            get(),
            aniApiProvider.userApi,
            aniApiProvider.userAuthApi,
            aniApiProvider.userProfileApi,
            aniApiProvider.bangumiApi,
            get(),
        )
    }
    single<BangumiSyncCommandRepository> {
        BangumiSyncCommandRepository(
            aniApiProvider.bangumiApi,
        )
    }
    single<BangumiMergeRepository> {
        DefaultBangumiMergeRepository(
            aniApiProvider.bangumiApi,
            get(),
        )
    }
    single<BangumiConflictChecker> {
        BangumiConflictChecker(
            mergeRepository = get(),
            subjectCollectionRepository = get(),
        )
    }

    single<TokenRepository> { TokenRepository(getContext().dataStores.tokenStore) }

    single<EpisodePreferencesRepository> {
        EpisodePreferencesRepositoryImpl(
            getContext().dataStores.preferredAllianceStore,
            database.preferredWebMediaSourceDao(),
        )
    }

    single<SubjectCollectionRepository> {
        SubjectCollectionRepositoryImpl(
            subjectService = get(),
            subjectCollectionDao = database.subjectCollection(),
//            characterDao = database.character(),
//            characterActorDao = database.characterActor(),
//            personDao = database.person(),
//            subjectCharacterRelationDao = database.subjectCharacterRelation(),
//            subjectPersonRelationDao = database.subjectPersonRelation(),
            subjectRelationsDao = database.subjectRelations(),
            episodeCollectionRepository = get(),
            animeScheduleRepository = get(),
            episodeService = get(),
            episodeCollectionDao = database.episodeCollection(),
            sessionManager = get(),
            nsfwModeSettingsFlow = settingsRepository.uiSettings.flow.map { it.searchSettings.nsfwMode },
            getEpisodeTypeFiltersUseCase = get(),
        )
    }

    single<FollowedSubjectsRepository> {
        FollowedSubjectsRepository(
            subjectCollectionRepository = get(),
            animeScheduleRepository = get(),
            episodeCollectionRepository = get(),
            settingsRepository = get(),
            sessionManager = get(),
        )
    }

    single<SubjectSearchRepository> {
        SubjectSearchRepository(
            aniSubjectSearchService = get(),
            subjectCollectionRepository = get(),
        )
    }

    single<SubjectSearchCompletionRepository> {
        SubjectSearchCompletionRepository(
            aniSubjectSearchService = get(),
            subjectCollectionRepository = get(),
            settingsRepository = get(),
        )
    }

    single<SubjectSearchHistoryRepository> {
        SubjectSearchHistoryRepository(database.searchHistory(), database.searchTag())
    }

    single<SubjectRelationsRepository> {
        DefaultSubjectRelationsRepository(
            database.subjectCollection(),
            database.subjectRelations(),
            subjectService = get(),
            subjectCollectionRepository = get(),
            aniSubjectRelationIndexService = get(),
        )
    }

    single<PersonDetailsRepository> {
        PersonDetailsRepository(
            personsApi = aniApiProvider.personsApi,
            charactersApi = aniApiProvider.charactersApi,
        )
    }

    single<AnimeScheduleRepository> { AnimeScheduleRepository(get()) }

    single<BangumiCommentRepository> {
        BangumiCommentRepository(
            get(),
            database.subjectReviews(),
        )
    }

    single<EpisodeCollectionRepository> {
        EpisodeCollectionRepository(
            subjectDao = database.subjectCollection(),
            episodeCollectionDao = database.episodeCollection(),
            episodeService = get(),
            animeScheduleRepository = get(),
            subjectCollectionRepository = inject(),
            getEpisodeTypeFiltersUseCase = get(),
        )
    }

    single<EpisodeProgressRepository> {
        EpisodeProgressRepository(
            episodeCollectionRepository = get(),
            cacheManager = get(),
        )
    }

    single<EpisodeScreenshotRepository> { WhatslinkEpisodeScreenshotRepository() }

    single<EpisodeCommentRepository> { EpisodeCommentRepository(aniCommentService = get()) }

    single<PersonCommentRepository> { PersonCommentRepository(aniCommentService = get()) }

    single<MediaSourceInstanceRepository> {
        MediaSourceInstanceRepositoryImpl(getContext().dataStores.mediaSourceSaveStore)
    }

    single<MediaSourceSubscriptionRepository> {
        MediaSourceSubscriptionRepository(getContext().dataStores.mediaSourceSubscriptionStore)
    }

    single<EpisodePlayHistoryRepository> {
        EpisodePlayHistoryRepositoryImpl(
            dataStore = getContext().dataStores.episodeHistoryStore,
            playbackHistoryDao = database.playbackHistoryDao(),
            onDirtyChanged = { get<PlaybackHistorySyncer>().requestSync() },
        )
    }

    single<PeerFilterSubscriptionRepository> {
        PeerFilterSubscriptionRepository(
            dataStore = getContext().dataStores.peerFilterSubscriptionStore,
            ruleSaveDir = getContext().files.dataDir.resolve("peerfilter-subs"),
            httpClient = get<HttpClientProvider>().get(ScopedHttpClientUserAgent.ANI),
            builtinPeerFilterRuleApi = get<AniApiProvider>().pfRuleApi,
        )
    }

    single<TrendsRepository> { TrendsRepository(get<AniApiProvider>().trendsApi) }

    single<RecommendationRepository> { RecommendationRepository(get<AniApiProvider>().homeApi) }

    single<AutoSkipRepository> { AutoSkipRepository(get<AniApiProvider>().episodesApi) }

    single<DanmakuRepository> {
        DanmakuRepository(
            parentCoroutineContext = coroutineScope.coroutineContext,
            danmakuApi = aniApiProvider.danmakuApi,
            danmakuDao = database.danmakuDao(),
            httpClientProvider = get(),
            getMediaCacheUseCase = get(),
            getSubjectEpisodeInfoBundleFlowUseCase = get(),
            settingsRepository = get(),
        )
    }

    single<SettingsRepository> { PreferencesRepositoryImpl(getContext().dataStores.preferencesStore) }

    single<DanmakuRegexFilterRepository> { DanmakuRegexFilterRepositoryImpl(getContext().dataStores.danmakuFilterStore) }

    single<MikanIndexCacheRepository> { MikanIndexCacheRepositoryImpl(getContext().dataStores.mikanIndexStore) }

    single<SelectorMediaSourceEpisodeCacheRepository> {
        SelectorMediaSourceEpisodeCacheRepository(
            dao = database.webSearchSessionCacheDao(),
            userTtlFlow = get<SettingsRepository>().mediaSelectorSettings.flow.map { it.webSearchCacheTtl },
        )
    }
}
