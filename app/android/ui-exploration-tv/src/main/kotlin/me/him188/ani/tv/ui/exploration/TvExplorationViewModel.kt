/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.exploration

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.network.BangumiSummaryService
import me.him188.ani.app.data.network.TmdbImageService
import me.him188.ani.app.data.network.newestAiredDateStringOrNull
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.navigation.SubjectDetailPlaceholder
import me.him188.ani.app.ui.main.ExplorationPageViewModel
import me.him188.ani.tv.ui.foundation.TvNavigationEvent
import me.him188.ani.tv.ui.foundation.TvNavigationEvents
import org.koin.core.Koin

class TvExplorationViewModel(
    koin: Koin,
    private val collectionRepository: SubjectCollectionRepository,
    private val tmdb: TmdbImageService,
    private val summaryService: BangumiSummaryService,
) : ExplorationPageViewModel(koin) {
    private val media = MutableStateFlow(TvSubjectMediaUiState())
    val mediaState = media.asStateFlow()
    private val hero = MutableStateFlow<TvHeroSubject?>(null)
    private val navigation = TvNavigationEvents()
    val navigationEvents = navigation.events
    private val requestsMutex = Mutex()
    private val backdropGate = Semaphore(3)
    private val infoRequests = mutableMapOf<Int, Deferred<SubjectCollectionInfo?>>()
    private val backdropRequests = mutableMapOf<Int, Deferred<String?>>()

    init {
        backgroundScope.launch {
            hero.filterNotNull().collectLatest { target ->
                if (target.subjectId !in media.value.infoCache) delay(300)
                val info = loadInfo(target.subjectId) ?: return@collectLatest
                loadBackdrop(target.subjectId)
                if (info.subjectInfo.summary.isBlank() && target.subjectId !in media.value.summaryFallbackCache) {
                    val summary = loadOrNull { summaryService.getSummary(target.subjectId) }.orEmpty()
                    media.update { it.copy(summaryFallbackCache = it.summaryFallbackCache + (target.subjectId to summary)) }
                }
            }
        }
    }

    fun onIntent(intent: TvExplorationIntent) {
        when (intent) {
            is TvExplorationIntent.ShowHero -> hero.value = intent.subject
            is TvExplorationIntent.CardVisible -> {
                intent.collection?.let { info ->
                    media.update { current ->
                        if (info.subjectId in current.infoCache) current
                        else current.copy(infoCache = current.infoCache + (info.subjectId to info))
                    }
                }
                backgroundScope.launch { loadBackdrop(intent.subjectId) }
            }
            is TvExplorationIntent.OpenSubject -> navigation.emit(TvNavigationEvent.Subject(
                intent.subject.subjectId,
                SubjectDetailPlaceholder(
                    id = intent.subject.subjectId,
                    nameCN = intent.subject.title,
                    coverUrl = intent.subject.imageUrl,
                ),
            ))
        }
    }

    private suspend fun loadInfo(id: Int): SubjectCollectionInfo? {
        media.value.infoCache[id]?.let { return it }
        return requestsMutex.withLock {
            infoRequests.getOrPut(id) {
                backgroundScope.async {
                    loadOrNull { collectionRepository.subjectCollectionFlow(id).first() }?.also { info ->
                        media.update { it.copy(infoCache = it.infoCache + (id to info)) }
                    }
                }
            }
        }.await()
    }

    private suspend fun loadBackdrop(id: Int): String? {
        if (id in media.value.backdropCache) return media.value.backdropCache[id]
        return requestsMutex.withLock {
            backdropRequests.getOrPut(id) {
                backgroundScope.async {
                    backdropGate.withPermit {
                        val info = loadInfo(id)
                        val url = info?.let {
                            loadOrNull {
                                tmdb.getBackdropUrl(id, it.subjectInfo.name, activeAsOfDate = it.episodes.newestAiredDateStringOrNull())
                            }
                        }
                        media.update { it.copy(backdropCache = it.backdropCache + (id to url)) }
                        url
                    }
                }
            }
        }.await()
    }

    private suspend fun <T> loadOrNull(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
