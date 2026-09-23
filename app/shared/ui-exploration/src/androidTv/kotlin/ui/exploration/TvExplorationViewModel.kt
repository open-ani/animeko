/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.exploration

import androidx.paging.compose.launchAsLazyPagingItemsIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.navigation.SubjectDetailPlaceholder
import me.him188.ani.app.ui.exploration.ExplorationPageViewModel
import me.him188.ani.tv.ui.foundation.TvNavigationEvent
import me.him188.ani.tv.ui.foundation.TvNavigationEvents
import org.koin.core.Koin

class TvExplorationViewModel(
    koin: Koin,
) : ExplorationPageViewModel(koin) {
    // Keep the presented pages across route changes, like the shared trending pager.
    // Recreating an empty presenter briefly removes the first row and shifts the saved viewport.
    val recommendations = explorationPageState.recommendationPager.launchAsLazyPagingItemsIn(backgroundScope)
    val followed = explorationPageState.followedSubjectsPager.launchAsLazyPagingItemsIn(backgroundScope)
    private val media = MutableStateFlow(TvSubjectMediaUiState())
    val mediaState = media.asStateFlow()
    private val hero = MutableStateFlow<TvHeroSubject?>(null)
    private val navigation = TvNavigationEvents()
    val navigationEvents = navigation.events

    init {
        backgroundScope.launch {
            hero.filterNotNull().collectLatest { target ->
                if (target.subjectId !in media.value.infoCache) delay(300)
                loadInfo(target.subjectId) ?: return@collectLatest
                loadBackdrop(target.subjectId)
            }
        }
    }

    fun onIntent(intent: TvExplorationIntent) {
        when (intent) {
            is TvExplorationIntent.ShowHero -> hero.value = intent.subject
            is TvExplorationIntent.CardVisible -> {
                intent.collection?.let { info ->
                    media.update { current ->
                        if (current.infoCache[info.subjectId] == info) current
                        else current.copy(infoCache = current.infoCache + (info.subjectId to info))
                    }
                }
                backgroundScope.launch { loadBackdrop(intent.subjectId) }
            }

            is TvExplorationIntent.OpenSubject -> navigation.emit(
                TvNavigationEvent.Subject(
                    intent.subject.subjectId,
                    SubjectDetailPlaceholder(
                        id = intent.subject.subjectId,
                        nameCN = intent.subject.title,
                        coverUrl = intent.subject.imageUrl,
                    ),
                ),
            )

            is TvExplorationIntent.ContinueWatching -> navigation.emit(
                TvNavigationEvent.Episode(intent.subject.subjectId, intent.episodeId),
            )
        }
    }

    private suspend fun loadInfo(id: Int): SubjectCollectionInfo? {
        media.value.infoCache[id]?.let { return it }
        return loadOrNull { getSubjectCollectionInfo(id) }?.also { info ->
            media.update { it.copy(infoCache = it.infoCache + (id to info)) }
        }
    }

    private suspend fun loadBackdrop(id: Int) {
        if (id in media.value.backdropCache) return
        val info = loadInfo(id) ?: return
        val url = info.subjectInfo.tmdbArt?.primaryBackdrop?.medium
        media.update { it.copy(backdropCache = it.backdropCache + (id to url)) }
    }

    private suspend fun <T> loadOrNull(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
