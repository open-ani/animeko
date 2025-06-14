/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.danmaku

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import me.him188.ani.app.data.network.danmaku.AniDanmakuProvider
import me.him188.ani.app.data.network.danmaku.AniDanmakuSender
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.ui.foundation.BackgroundScope
import me.him188.ani.app.ui.foundation.HasBackgroundScope
import me.him188.ani.client.apis.DanmakuAniApi
import me.him188.ani.danmaku.api.DanmakuContent
import me.him188.ani.danmaku.api.DanmakuInfo
import me.him188.ani.danmaku.api.provider.DanmakuFetchRequest
import me.him188.ani.danmaku.api.provider.DanmakuFetchResult
import me.him188.ani.danmaku.api.provider.DanmakuMatchInfo
import me.him188.ani.danmaku.api.provider.DanmakuMatchMethod
import me.him188.ani.danmaku.api.provider.DanmakuProvider
import me.him188.ani.danmaku.api.provider.MatchingDanmakuProvider
import me.him188.ani.danmaku.api.provider.SimpleDanmakuProvider
import me.him188.ani.danmaku.dandanplay.DandanplayDanmakuProvider
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

/**
 * 管理多个弹幕源 [DanmakuProvider]
 */
class DanmakuManager(
    parentCoroutineContext: CoroutineContext = EmptyCoroutineContext,
    val danmakuApi: ApiInvoker<DanmakuAniApi>,
) : KoinComponent, HasBackgroundScope by BackgroundScope(parentCoroutineContext) {
    private val httpClientProvider: HttpClientProvider by inject()

    private val providers by lazy {
        listOf(
            AniDanmakuProvider(danmakuApi),
            DandanplayDanmakuProvider(
                dandanplayAppId = currentAniBuildConfig.dandanplayAppId,
                dandanplayAppSecret = currentAniBuildConfig.dandanplayAppSecret,
                httpClientProvider.get(),
            ),
        )
    }

    private val sender by lazy { AniDanmakuSender(danmakuApi) }

    val selfId: Flow<String?> get() = sender.selfId

    fun createFetchers(): List<DanmakuFetcher> {
        return providers.map {
            DanmakuFetcher(it)
        }
    }

    suspend fun post(
        episodeId: Int,
        danmaku: DanmakuContent
    ): DanmakuInfo {
        return try {
            sender.send(episodeId, danmaku)
        } catch (e: Throwable) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    fun fetchFromAll(request: DanmakuFetchRequest): Flow<List<DanmakuFetchResult>> {
        return combine(
            createFetchers()
                .map {
                    flow { emit(it.fetch(request)) }
                        .catch { emit(emptyList()) }
                },
        ) {
            it.toList().flatten()
        }
    }
}


class DanmakuFetcher(
    private val provider: DanmakuProvider
) {
    suspend fun fetch(
        request: DanmakuFetchRequest
    ): List<DanmakuFetchResult> {
        return flow {
            emit(
                withTimeout(60.seconds) {
                    when (provider) {
                        is MatchingDanmakuProvider -> {
                            provider.fetchAutomatic(request)
                        }

                        is SimpleDanmakuProvider -> {
                            provider.fetchAutomatic(request = request)
                        }
                    }
                },
            )
        }.retry(1) {
            if (it is CancellationException && !currentCoroutineContext().isActive) {
                // collector was cancelled
                return@retry false
            }
            logger.error(it) { "Failed to fetch danmaku from service '${provider.mainServiceId}'" }
            true
        }.catch {
            emit(
                listOf(
                    DanmakuFetchResult(
                        provider.providerId,
                        DanmakuMatchInfo(
                            provider.mainServiceId,
                            0,
                            DanmakuMatchMethod.NoMatch,
                        ),
                        list = emptyList(),
                    ),
                ),
            )// 忽略错误, 否则一个源炸了会导致所有弹幕都不发射了
            // 必须要 emit 一个, 否则下面 .first 会出错
        }.first()
    }

    val providerId get() = provider.providerId
    val supportsInteractiveMatching get() = provider is MatchingDanmakuProvider

    fun startInteractiveMatch(): MatchingDanmakuProvider {
        check(provider is MatchingDanmakuProvider) {
            "Provider $provider does not support interactive matching"
        }
        return provider
    }

    private companion object {
        private val logger = logger<DanmakuFetcher>()
    }
}

///**
// * 手动选择弹幕条目的会话.
// *
// * This is not thread-safe.
// */
//class InteractiveDanmakuMatchSession(
//    private val provider: MatchingDanmakuProvider,
//    private val defaultDispatcher: CoroutineContext = Dispatchers.Default,
//) {
//    private val _subjectState = MutableStateFlow<SubjectState>(SubjectState.Waiting)
//    val subjectState = _subjectState.asStateFlow()
//    private val _episodeState = MutableStateFlow<EpisodeState>(EpisodeState.Waiting)
//    val episodeState = _episodeState.asStateFlow()
//
//    suspend fun setSubjectName(name: String) {
//        withContext(defaultDispatcher) {
//            _subjectState.value = SubjectState.Fetching(name)
//            try {
//                val subjects = provider.fetchSubjectList(name)
//                _subjectState.value = SubjectState.Success(subjects)
//            } catch (e: CancellationException) {
//                _subjectState.value = SubjectState.Waiting
//                throw e
//            } catch (e: Throwable) {
//                _subjectState.value = SubjectState.Failed(e)
//            }
//        }
//    }
//
//    fun dismissSubject() {
//        _subjectState.value = SubjectState.Waiting
//    }
//
//    suspend fun setEpisodeName(subject: DanmakuSubject) {
//        withContext(defaultDispatcher) {
//            _episodeState.value = EpisodeState.Fetching
//            try {
//                val episodes = provider.fetchEpisodeList(subject)
//                _episodeState.value = EpisodeState.Success(episodes)
//            } catch (e: CancellationException) {
//                _episodeState.value = EpisodeState.Waiting
//                throw e
//            } catch (e: Throwable) {
//                _episodeState.value = EpisodeState.Failed(e)
//            }
//        }
//    }
//
//    fun dismissEpisode() {
//        _episodeState.value = EpisodeState.Waiting
//    }
//
//    sealed class SubjectState {
//        data object Waiting : SubjectState()
//
//        data class Fetching(
//            val query: String,
//        ) : SubjectState()
//
//        data class Success(
//            val subjects: List<DanmakuSubject>,
//        ) : SubjectState()
//
//        data class Failed(
//            val error: Throwable,
//        ) : SubjectState()
//    }
//
//    sealed class EpisodeState {
//        data object Waiting : EpisodeState()
//
//        data object Fetching : EpisodeState()
//
//        data class Success(
//            val episodes: List<DanmakuEpisode>,
//        ) : EpisodeState()
//
//        data class Failed(
//            val error: Throwable,
//        ) : EpisodeState()
//    }
//}
//
//@Immutable
//data class InteractiveDanmakuMatchSessionPresentation(
//    val showInputQuery: Boolean,
//    val showSelectSubjects: Boolean,
//    val subjects: List<DanmakuSubject>,
//    val showSelectEpisodes: Boolean,
//    val episodes: List<DanmakuEpisode>,
//    val showSearching: Boolean,
//
//    val error: Throwable?,
//)
//
//class InteractiveDanmakuMatchSessionPresenter(
//    private val session: InteractiveDanmakuMatchSession,
//) {
//    suspend fun setQuery(name: String) {
//        session.setSubjectName(name)
//    }
//
//    fun dismissError() {
//        session.dismissSubject()
//        session.dismissEpisode()
//    }
//
//    val presentationFlow = combine(
//        session.subjectState,
//        session.episodeState,
//    ) { subjectState, episodeState ->
//        val showInputQuery = subjectState is InteractiveDanmakuMatchSession.SubjectState.Waiting
//        val showSelectSubjects = subjectState is InteractiveDanmakuMatchSession.SubjectState.Success
//        val subjects = (subjectState as? InteractiveDanmakuMatchSession.SubjectState.Success)?.subjects ?: emptyList()
//        val showSelectEpisodes = episodeState is InteractiveDanmakuMatchSession.EpisodeState.Success
//        val episodes = (episodeState as? InteractiveDanmakuMatchSession.EpisodeState.Success)?.episodes ?: emptyList()
//        val showSearching = subjectState is InteractiveDanmakuMatchSession.SubjectState.Fetching ||
//                episodeState is InteractiveDanmakuMatchSession.EpisodeState.Fetching
//
//        val error = when {
//            subjectState is InteractiveDanmakuMatchSession.SubjectState.Failed -> subjectState.error
//            episodeState is InteractiveDanmakuMatchSession.EpisodeState.Failed -> episodeState.error
//            else -> null
//        }
//
//        InteractiveDanmakuMatchSessionPresentation(
//            showInputQuery,
//            showSelectSubjects,
//            subjects,
//            showSelectEpisodes,
//            episodes,
//            showSearching,
//            error,
//        )
//    }.shareIn(CoroutineScope(Dispatchers.Default), started = SharingStarted.WhileSubscribed(), replay = 1)
//}
