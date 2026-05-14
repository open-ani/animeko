/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.cache.storage

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.domain.media.cache.ExternalLocalFileMediaFactory
import me.him188.ani.app.domain.media.cache.engine.MediaCacheEngineKey
import me.him188.ani.app.domain.media.resolver.toEpisodeMetadata
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.createTempFile
import me.him188.ani.utils.io.delete
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.writeText
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExternalLocalFileMediaCacheStorageTest {
    @Test
    fun `delete should not remove original file`() = runTest {
        val storage = createStorage(backgroundScope.coroutineContext)
        val file = SystemPaths.createTempFile(prefix = "external-local-file", suffix = ".mkv").apply {
            writeText("test-data")
        }
        val subjectInfo = SubjectInfo.Empty.copy(subjectId = 1, name = "Test Subject")
        val episodeInfo = EpisodeInfo(1, EpisodeType.MainStory, sort = EpisodeSort(1), name = "Episode 1")

        val cache = storage.cache(
            media = ExternalLocalFileMediaFactory.createMedia(file, subjectInfo, episodeInfo),
            metadata = ExternalLocalFileMediaFactory.createMetadata(subjectInfo, episodeInfo),
            episodeMetadata = episodeInfo.toEpisodeMetadata(),
            resume = false,
        )

        storage.delete(cache)

        assertTrue(file.exists())
        assertEquals(0, storage.listFlow.first().size)
    }

    @Test
    fun `cache should replace previous binding for same episode`() = runTest {
        val storage = createStorage(backgroundScope.coroutineContext)
        val subjectInfo = SubjectInfo.Empty.copy(subjectId = 1, name = "Test Subject")
        val episodeInfo = EpisodeInfo(1, EpisodeType.MainStory, sort = EpisodeSort(1), name = "Episode 1")
        val file1 = SystemPaths.createTempFile(prefix = "external-local-file-1", suffix = ".mkv").apply {
            writeText("test-data-1")
        }
        val file2 = SystemPaths.createTempFile(prefix = "external-local-file-2", suffix = ".mkv").apply {
            writeText("test-data-2")
        }

        storage.cache(
            media = ExternalLocalFileMediaFactory.createMedia(file1, subjectInfo, episodeInfo),
            metadata = ExternalLocalFileMediaFactory.createMetadata(subjectInfo, episodeInfo),
            episodeMetadata = episodeInfo.toEpisodeMetadata(),
            resume = false,
        )
        storage.cache(
            media = ExternalLocalFileMediaFactory.createMedia(file2, subjectInfo, episodeInfo),
            metadata = ExternalLocalFileMediaFactory.createMetadata(subjectInfo, episodeInfo),
            episodeMetadata = episodeInfo.toEpisodeMetadata(),
            resume = false,
        )

        val bindings = storage.listFlow.first()
        val cachedMedia = bindings.single().getCachedMedia()

        assertEquals(1, bindings.size)
        assertEquals(file2.absolutePath, cachedMedia.download.let { it as ResourceLocation.LocalFile }.filePath)
        assertTrue(file1.exists())
        assertTrue(file2.exists())
    }

    @Test
    fun `restorePersistedCaches should drop missing file metadata`() = runTest {
        val store = MemoryDataStore<List<MediaCacheSave>>(emptyList())
        val storage = ExternalLocalFileMediaCacheStorage(
            mediaSourceId = "local-file-system",
            store = store,
            parentCoroutineContext = backgroundScope.coroutineContext,
        )
        val subjectInfo = SubjectInfo.Empty.copy(subjectId = 1, name = "Test Subject")
        val episodeInfo = EpisodeInfo(1, EpisodeType.MainStory, sort = EpisodeSort(1), name = "Episode 1")
        val missingFile = SystemPaths.createTempFile(prefix = "external-local-missing", suffix = ".mkv").apply {
            writeText("missing")
            delete()
        }
        val media = ExternalLocalFileMediaFactory.createMedia(missingFile, subjectInfo, episodeInfo)

        store.data.value = listOf(
            MediaCacheSave(
                origin = media,
                metadata = ExternalLocalFileMediaFactory.createMetadata(subjectInfo, episodeInfo),
                engine = MediaCacheEngineKey.ExternalLocalFile,
            ),
        )

        storage.restorePersistedCaches()

        assertEquals(0, storage.listFlow.first().size)
        assertEquals(0, store.data.first().size)
    }

    private fun createStorage(parentCoroutineContext: CoroutineContext): ExternalLocalFileMediaCacheStorage {
        return ExternalLocalFileMediaCacheStorage(
            mediaSourceId = "local-file-system",
            store = MemoryDataStore(emptyList()),
            parentCoroutineContext = parentCoroutineContext,
        )
    }
}
