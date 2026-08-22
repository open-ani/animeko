/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.domain.media.createTestDefaultMedia
import me.him188.ani.app.domain.media.createTestMediaProperties
import me.him188.ani.app.domain.media.fetch.CompletedConditions
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchResult
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchState
import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.platform.collections.ImmutableEnumMap
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaSelectorAutoSelectPropagationTest {
    @Test
    fun `same mediaId does not satisfy propagation barrier for a different Media`() = runTest {
        val currentMedia = jellyfinMedia(
            mediaId = "same-jellyfin-item-id",
            server = "preferred-server",
        )
        val staleMedia = jellyfinMedia(
            mediaId = currentMedia.mediaId,
            server = "stale-server",
        )
        val selector = ControlledCandidatesSelector(
            listOf(staleMedia),
            coroutineContext[ContinuationInterceptor]!!,
        )
        val session = ControlledMediaFetchSession(listOf(currentMedia))
        var selected: Media? = null
        val job = backgroundScope.launch {
            selected = selector.autoSelect.awaitCompletedAndSelectDefault(session)
        }
        runCurrent()

        assertFalse(
            job.isCompleted,
            "A stale Media with the same mediaId must not satisfy the propagation barrier",
        )
        assertNull(selector.selected.value)

        selector.candidates.value = listOf(currentMedia)
        runCurrent()

        assertTrue(job.isCompleted)
        assertEquals(currentMedia, selected)
        assertEquals(currentMedia, selector.selected.value)
    }

    @Test
    fun `restart invalidates pending propagation snapshot`() = runTest {
        val oldMedia = jellyfinMedia(
            mediaId = "old-jellyfin-item-id",
            server = "old-server",
        )
        val selector = ControlledCandidatesSelector(
            emptyList(),
            coroutineContext[ContinuationInterceptor]!!,
        )
        val session = ControlledMediaFetchSession(listOf(oldMedia))
        val job = backgroundScope.launch {
            selector.autoSelect.awaitCompletedAndSelectDefault(session)
        }
        runCurrent()
        assertFalse(job.isCompleted, "The test must reach the propagation wait")

        session.restartAndCompleteWithEmptyResults()
        runCurrent()

        try {
            assertTrue(
                job.isCompleted,
                "Auto selection must stop waiting for the previous query snapshot after restart",
            )
            assertNull(selector.selected.value)
        } finally {
            job.cancelAndJoin()
        }
    }

    private fun jellyfinMedia(
        mediaId: String,
        server: String,
    ): DefaultMedia {
        return createTestDefaultMedia(
            mediaId = mediaId,
            mediaSourceId = "jellyfin",
            originalUrl = "http://$server/Items/$mediaId",
            download = ResourceLocation.HttpStreamingFile(
                uri = "http://$server/Videos/$mediaId/stream",
            ),
            originalTitle = "Episode 1",
            publishedTime = 0,
            properties = createTestMediaProperties(subjectName = "Test Anime"),
            episodeRange = EpisodeRange.single(EpisodeSort(1)),
            location = MediaSourceLocation.Lan,
            kind = MediaSourceKind.WEB,
        )
    }

    private class ControlledCandidatesSelector private constructor(
        val candidates: MutableStateFlow<List<Media>>,
        private val delegate: MediaSelector,
    ) : MediaSelector by delegate {
        constructor(
            initialCandidates: List<Media>,
            flowCoroutineContext: CoroutineContext,
        ) : this(MutableStateFlow(initialCandidates), flowCoroutineContext)

        private constructor(
            candidates: MutableStateFlow<List<Media>>,
            flowCoroutineContext: CoroutineContext,
        ) : this(
            candidates,
            createDelegate(candidates, flowCoroutineContext),
        )

        companion object {
            private fun createDelegate(
                candidates: Flow<List<Media>>,
                flowCoroutineContext: CoroutineContext,
            ): MediaSelector {
                return DefaultMediaSelector(
                    mediaSelectorContextNotCached = flowOf(MediaSelectorContext.EmptyForPreview),
                    mediaListNotCached = candidates,
                    savedUserPreference = flowOf(MediaPreference.Any),
                    savedDefaultPreference = flowOf(MediaPreference.Any),
                    mediaSelectorSettings = flowOf(
                        MediaSelectorSettings.AllVisible.copy(
                            fastSelectWebKind = false,
                            hideSingleEpisodeForCompleted = false,
                            preferSeasons = false,
                        ),
                    ),
                    flowCoroutineContext = flowCoroutineContext,
                    enableCaching = false,
                )
            }
        }
    }

    private class ControlledMediaFetchSession(
        initialResults: List<Media>,
    ) : MediaFetchSession {
        private val results = MutableStateFlow(initialResults)
        private val completion = MutableStateFlow(CompletedConditions.AllCompleted)
        private val sourceState = MutableStateFlow<MediaSourceFetchState>(
            MediaSourceFetchState.Succeed(0),
        )

        override val request: Flow<MediaFetchRequest> = flowOf(
            MediaFetchRequest(
                subjectId = "1",
                episodeId = "1",
                subjectNameCN = "Test Anime",
                subjectNames = listOf("Test Anime"),
                episodeSort = EpisodeSort(1),
                episodeName = "Episode 1",
            ),
        )
        override val mediaSourceResults: List<MediaSourceFetchResult> = listOf(
            object : MediaSourceFetchResult {
                override val instanceId: String = "jellyfin-instance"
                override val mediaSourceId: String = "jellyfin"
                override val sourceInfo: MediaSourceInfo = MediaSourceInfo("Jellyfin")
                override val kind: MediaSourceKind = MediaSourceKind.WEB
                override val state = sourceState
                override val results: Flow<List<Media>> = this@ControlledMediaFetchSession.results

                override fun restart() {
                }

                override fun enable() {
                }
            },
        )
        override val cumulativeResults: Flow<List<Media>> = results
        override val hasCompleted: Flow<CompletedConditions> = completion

        override fun setFetchRequest(request: MediaFetchRequest) {
        }

        fun restartAndCompleteWithEmptyResults() {
            completion.value = CompletedConditions(
                ImmutableEnumMap { false },
            )
            results.value = emptyList()
            sourceState.value = MediaSourceFetchState.Succeed(1)
            completion.value = CompletedConditions.AllCompleted
        }
    }
}
