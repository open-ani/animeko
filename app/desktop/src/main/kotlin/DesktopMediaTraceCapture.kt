/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.data.repository.media.SelectorMediaSourceEpisodeCacheRepository
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.domain.episode.CreateMediaFetchSelectBundleFlowUseCase
import me.him188.ani.app.domain.media.selector.trace.MediaSelectionTraceRecorder
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.koin.core.Koin
import org.koin.dsl.module
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Developer-only capture is opt-in via environment variables; normal launches install nothing. */
internal class DesktopMediaTraceCapture private constructor(
    private val scope: CoroutineScope,
    private val duration: Duration,
    private val episodes: List<Pair<Int, Int>>,
    private val cacheToClear: SelectorMediaSourceEpisodeCacheRepository?,
    private val subjects: SubjectCollectionRepository,
) {
    fun start(navigator: AniNavigator) {
        if (episodes.isEmpty()) return
        scope.launch(Dispatchers.Main) {
            navigator.awaitBackStack()
            delay(2.seconds)
            for ((subjectId, episodeId) in episodes) {
                // Match entering playback from a loaded subject page, including its episode list.
                val metadata = withTimeoutOrNull(30.seconds) {
                    subjects.subjectCollectionFlow(subjectId).first { subject ->
                        subject.episodes.any { it.episodeId == episodeId }
                    }
                }
                if (metadata == null) {
                    logger.warn { "Skipping capture: episode metadata unavailable for $subjectId:$episodeId" }
                    continue
                }
                cacheToClear?.clearByRequestedSubject(subjectId)
                logger.info { "Opening real playback for capture: subject=$subjectId, episode=$episodeId" }
                navigator.navigateEpisodeDetails(subjectId, episodeId)
                delay(duration + 15.seconds)
            }
            logger.info { "Playback capture batch finished" }
        }
    }

    companion object {
        private val logger = logger<DesktopMediaTraceCapture>()

        fun install(koin: Koin, scope: CoroutineScope): DesktopMediaTraceCapture? {
            val directory = System.getenv("ANIMEKO_MEDIA_TRACE_DIR")?.takeIf { it.isNotBlank() } ?: return null
            val duration = (System.getenv("ANIMEKO_MEDIA_TRACE_SECONDS")?.toLongOrNull() ?: 45).seconds
            require(duration.isPositive() && duration.isFinite())
            val episodes = System.getenv("ANIMEKO_MEDIA_TRACE_EPISODES").orEmpty()
                .split(',').filter { it.isNotBlank() }.map { value ->
                    val ids = value.split(':')
                    require(ids.size == 2) { "Expected subjectId:episodeId" }
                    ids[0].toInt() to ids[1].toInt()
                }
            val clearCache = System.getenv("ANIMEKO_MEDIA_TRACE_CLEAR_SEARCH_CACHE").toBoolean()
            require(!clearCache || episodes.isNotEmpty()) { "Clearing search cache requires a capture episode list" }
            val recorder = MediaSelectionTraceRecorder(File(directory), koin, duration, clearCache)
            val delegate = koin.get<CreateMediaFetchSelectBundleFlowUseCase>()
            koin.loadModules(listOf(module {
                single<CreateMediaFetchSelectBundleFlowUseCase> { recorder.decorate(delegate) }
            }))
            return DesktopMediaTraceCapture(
                scope, duration, episodes,
                if (clearCache) koin.get<SelectorMediaSourceEpisodeCacheRepository>() else null,
                koin.get<SubjectCollectionRepository>(),
            )
        }
    }
}
