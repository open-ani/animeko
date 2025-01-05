/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.domain.media.SOURCE_DMHY
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MediaSelectorFastSelectSourcesTest {
    private fun TestScope.createTestBuilder(): MediaSelectorTestBuilder = MediaSelectorTestBuilder(this).apply {
        savedUserPreference.value = MediaPreference.Any
        savedDefaultPreference.value = MediaPreference.Any
    }

    @Test
    fun `selects none if mediaList is empty`() = runTest {
        val test = createTestBuilder()
        val (_, session, selector) = test.create()

        val selected = selector.autoSelect.fastSelectSources(
            session,
            listOf(SOURCE_DMHY),
            flowOf(MediaSourceKind.WEB),
        )
        assertNull(selected)
    }

    @Test
    fun `selects none if order is empty`() = runTest {
        val test = createTestBuilder()
        val (_, session, selector) = test.create()

        val selected = selector.autoSelect.fastSelectSources(
            session,
            listOf(),
            flowOf(MediaSourceKind.WEB),
        )
        assertNull(selected)
    }

    @Test
    fun `selects none if order is preferred is null`() = runTest {
        val test = createTestBuilder()
        val (_, session, selector) = test.create()

        val selected = selector.autoSelect.fastSelectSources(
            session,
            listOf(),
            flowOf(null),
        )
        assertNull(selected)
    }

    @Test
    fun `selects none if order is preferred is BT`() = runTest {
        val test = createTestBuilder()
        val (_, session, selector) = test.create()

        val selected = selector.autoSelect.fastSelectSources(
            session,
            listOf(),
            flowOf(MediaSourceKind.BitTorrent),
        )
        assertNull(selected)
    }

    @Test
    fun `selects none when finished source id does not match`() = runTest {
        val test = createTestBuilder()
        val source1 = test.delayedMediaSource("1")
        val (_, session, selector) = test.create()

        lateinit var target: Media
        source1.complete(
            listOf(
                test.createMedia("1", MediaSourceKind.WEB).also { target = it },
            ),
        )

        val selected = selector.autoSelect.fastSelectSources(
            session,
            listOf("2"),
            flowOf(MediaSourceKind.WEB),
        )
        assertEquals(null, selected)
    }

    @Test
    fun `selects the first WEB when it has already finished`() = runTest {
        val test = createTestBuilder()
        val source1 = test.delayedMediaSource("1")
        val (_, session, selector) = test.create()

        lateinit var target: Media
        source1.complete(
            listOf(
                test.createMedia("1", MediaSourceKind.WEB).also { target = it },
            ),
        )

        val selected = selector.autoSelect.fastSelectSources(
            session,
            listOf("1"),
            flowOf(MediaSourceKind.WEB),
        )
        assertEquals(target, selected)
    }

    @Test
    fun `selects the first WEB when it finishes`() = runTest {
        val test = createTestBuilder()
        val source1 = test.delayedMediaSource("1")
        val (_, session, selector) = test.create()

        lateinit var target: Media

        val selectedDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            selector.autoSelect.fastSelectSources(
                session,
                listOf("1"),
                flowOf(MediaSourceKind.WEB),
            )
        }

        source1.complete(
            listOf(
                test.createMedia("1", MediaSourceKind.WEB).also { target = it },
            ),
        )
        val selected = selectedDeferred.await()
        assertEquals(target, selected)
    }

    @Test
    fun `selects none if finished source is not expected`() = runTest {
        val test = createTestBuilder()
        val source1 = test.delayedMediaSource("1")
        val (_, session, selector) = test.create()

        val selectedDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            selector.autoSelect.fastSelectSources(
                session,
                listOf("2"),
                flowOf(MediaSourceKind.WEB),
            )
        }

        source1.complete(
            listOf(
                test.createMedia("1", MediaSourceKind.WEB),
            ),
        )
        val selected = selectedDeferred.await()
        assertEquals(null, selected)
    }

    @Test
    fun `selects the second WEB when second source finishes first`() = runTest {
        val test = createTestBuilder()
        val source1 = test.delayedMediaSource("1")
        val source2 = test.delayedMediaSource("2")
        val (_, session, selector) = test.create()

        lateinit var target: Media

        val selectedDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            selector.autoSelect.fastSelectSources(
                session,
                listOf("1", "2"),
                flowOf(MediaSourceKind.WEB),
            )
        }

        source2.complete(
            listOf(
                test.createMedia("2", MediaSourceKind.WEB),
            ),
        )
        source1.complete(
            listOf(
                test.createMedia("1", MediaSourceKind.WEB).also { target = it },
            ),
        )
        val selected = selectedDeferred.await()
        assertEquals(target, selected)
    }

    @Test
    fun `selects the first WEB when second source finishes later`() = runTest {
        val test = createTestBuilder()
        val source1 = test.delayedMediaSource("1")
        val source2 = test.delayedMediaSource("2")
        val (_, session, selector) = test.create()

        lateinit var target: Media

        val selectedDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            selector.autoSelect.fastSelectSources(
                session,
                listOf("1", "2"),
                flowOf(MediaSourceKind.WEB),
            )
        }

        source1.complete(
            listOf(
                test.createMedia("1", MediaSourceKind.WEB).also { target = it },
            ),
        )
        source2.complete(
            listOf(
                test.createMedia("2", MediaSourceKind.WEB),
            ),
        )
        val selected = selectedDeferred.await()
        assertEquals(target, selected)
    }

    @Test
    fun `selects the second WEB when first has no response`() = runTest {
        val test = createTestBuilder()
        val source1 = test.delayedMediaSource("1")
        val source2 = test.delayedMediaSource("2")
        val (_, session, selector) = test.create()

        lateinit var target: Media

        val selectedDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            selector.autoSelect.fastSelectSources(
                session,
                listOf("1", "2"),
                flowOf(MediaSourceKind.WEB),
            )
        }

        source1.complete(emptyList())
        source2.complete(
            listOf(
                test.createMedia("2", MediaSourceKind.WEB).also { target = it },
            ),
        )
        val selected = selectedDeferred.await()
        assertEquals(target, selected)
    }

    @Test
    fun `order has more items than actual`() = runTest {
        val test = createTestBuilder()
        val source2 = test.delayedMediaSource("2")
        val (_, session, selector) = test.create()

        lateinit var target: Media

        val selectedDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            selector.autoSelect.fastSelectSources(
                session,
                listOf("0", "1", "2", "3"),
                flowOf(MediaSourceKind.WEB),
            )
        }

        source2.complete(
            listOf(
                test.createMedia("2", MediaSourceKind.WEB).also { target = it },
            ),
        )
        val selected = selectedDeferred.await()
        assertEquals(target, selected)
    }

    @Test
    fun `actual has more items than order but correct completion order`() = runTest {
        val test = createTestBuilder()
        val source1 = test.delayedMediaSource("1")
        val source2 = test.delayedMediaSource("2")
        val (_, session, selector) = test.create()

        lateinit var target: Media

        val selectedDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            selector.autoSelect.fastSelectSources(
                session,
                listOf("2"),
                flowOf(MediaSourceKind.WEB),
            )
        }

        source2.complete(
            listOf(
                test.createMedia("2", MediaSourceKind.WEB).also { target = it },
            ),
        )
        source1.complete(listOf(test.createMedia("1", MediaSourceKind.WEB)))
        val selected = selectedDeferred.await()
        assertEquals(target, selected)
    }

    @Test
    fun `actual has more items than order and wrong completion order`() = runTest {
        val test = createTestBuilder()
        val source1 = test.delayedMediaSource("1")
        val source2 = test.delayedMediaSource("2")
        val source3 = test.delayedMediaSource("3")
        val (_, session, selector) = test.create()

        lateinit var target: Media

        val selectedDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            selector.autoSelect.fastSelectSources(
                session,
                listOf("2"),
                flowOf(MediaSourceKind.WEB),
            )
        }

        source3.complete(listOf(test.createMedia("3", MediaSourceKind.WEB)))
        source1.complete(listOf(test.createMedia("1", MediaSourceKind.WEB)))
        source2.complete(
            listOf(
                test.createMedia("2", MediaSourceKind.WEB).also { target = it },
            ),
        )
        val selected = selectedDeferred.await()
        assertEquals(target, selected)
    }

    @Test
    fun `selects the second WEB when first source errored`() = runTest {
        val test = createTestBuilder()
        val source1 = test.delayedMediaSource("1")
        val source2 = test.delayedMediaSource("2")
        val (_, session, selector) = test.create()

        lateinit var target: Media

        val selectedDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            selector.autoSelect.fastSelectSources(
                session,
                listOf("1", "2"),
                flowOf(MediaSourceKind.WEB),
            )
        }

        source1.completeExceptionally(IOException())
        source2.complete(
            listOf(
                test.createMedia("2", MediaSourceKind.WEB).also { target = it },
            ),
        )
        val selected = selectedDeferred.await()
        assertEquals(target, selected)
    }

    @Test
    fun `selects the second WEB when first source disabled`() = runTest {
        val test = createTestBuilder()
        test.delayedMediaSource("1", enabled = false)
        val source2 = test.delayedMediaSource("2")
        val (_, session, selector) = test.create()

        lateinit var target: Media

        val selectedDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            selector.autoSelect.fastSelectSources(
                session,
                listOf("1", "2"),
                flowOf(MediaSourceKind.WEB),
            )
        }

        source2.complete(
            listOf(
                test.createMedia("2", MediaSourceKind.WEB).also { target = it },
            ),
        )
        val selected = selectedDeferred.await()
        assertEquals(target, selected)
    }

    @Test
    fun `uses unfiltered list`() = runTest {
        val test = createTestBuilder()
        test.savedUserPreference.value = test.savedUserPreference.value.copy(
            subtitleLanguageId = "dummy", // filters out all
        )
        test.delayedMediaSource("1", enabled = false)
        val source2 = test.delayedMediaSource("2")
        val (_, session, selector) = test.create()

        lateinit var target: Media

        val selectedDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            selector.autoSelect.fastSelectSources(
                session,
                listOf("1", "2"),
                flowOf(MediaSourceKind.WEB),
                allowNonPreferredFlow = flowOf(true),
            )
        }

        source2.complete(
            listOf(
                test.createMedia("2", MediaSourceKind.WEB).also { target = it },
            ),
        )
        val selected = selectedDeferred.await()
        assertEquals(target, selected)
    }

    @Test
    fun `allowNonPreferredFlow wont cancel`() = runTest {
        val test = createTestBuilder()
        test.savedUserPreference.value = test.savedUserPreference.value.copy(
            subtitleLanguageId = "dummy", // filters out all
        )
        test.delayedMediaSource("1", enabled = false)
        val source2 = test.delayedMediaSource("2")
        val (_, session, selector) = test.create()

        lateinit var target: Media

        val selectedDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            selector.autoSelect.fastSelectSources(
                session,
                listOf("1", "2"),
                flowOf(MediaSourceKind.WEB),
                allowNonPreferredFlow = flow {
                    emit(true)
                    awaitCancellation() // 永远不 cancel, 但 `fastSelectSources` 仍然需要在有限时间内返回 (当 session 查询完成时)
                },
            )
        }

        source2.complete(
            listOf(
                test.createMedia("2", MediaSourceKind.WEB).also { target = it },
            ),
        )
        val selected = selectedDeferred.await()
        assertEquals(target, selected)
    }
}