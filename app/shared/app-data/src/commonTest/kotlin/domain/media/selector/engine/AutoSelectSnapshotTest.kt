/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:OptIn(UnsafeOriginalMediaAccess::class)

package me.him188.ani.app.domain.media.selector.engine

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchState
import me.him188.ani.app.domain.media.selector.MaybeExcludedMedia
import me.him188.ani.app.domain.media.selector.UnsafeOriginalMediaAccess
import me.him188.ani.app.domain.media.selector.implicit
import me.him188.ani.app.domain.media.selector.testFramework.runFetchMediaSelectorTestSuite
import me.him188.ani.app.domain.media.selector.testFramework.tier
import me.him188.ani.datasources.api.source.MediaSourceKind.BitTorrent
import me.him188.ani.datasources.api.source.MediaSourceKind.WEB
import me.him188.ani.test.DisabledOnNative
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * 决策快照必须与 UI 看到的候选流口径一致: 同样的过滤、排序、偏好筛选.
 */
@DisabledOnNative // TODO: ContextParameters crashes on Native
class AutoSelectSnapshotTest {
    @Test
    fun `snapshot candidates and preferred match filteredCandidates and preferredCandidates`() = runFetchMediaSelectorTestSuite {
        initSubject("test")
        preferenceApi.savedUserPreference.value = MediaPreference.Any.copy(resolution = "1080P")
        val (_, session, sources) = configureFetchSession {
            object {
                val web2 by web { tier = 2 }
                val web1 by web { tier = 0 }
                val bt1 by bt()
            }
        }
        sources.web2.complete(
            media(kind = WEB, subjectName = "${initApi.subjectName} fuzzy season", resolution = "720P"),
            media(kind = WEB, subjectName = initApi.subjectName),
        )
        sources.web1.complete(
            media(kind = WEB, subjectName = initApi.subjectName, resolution = "720P"),
            media(kind = WEB, subjectName = "Invalid subject name"), // 被排除
        )
        sources.bt1.complete(media(kind = BitTorrent, subjectName = initApi.subjectName))
        testScope().runCurrent()

        val snapshot = selector.autoSelectSnapshots(session.sourceSnapshots()).first { s -> s.sources.all { it.isFinal } }

        val uiCandidates = selector.filteredCandidates.first()
        assertEquals(5, uiCandidates.size)
        assertEquals(
            uiCandidates.map { it.original.mediaId to (it is MaybeExcludedMedia.Included) },
            snapshot.candidates.map { it.original.mediaId to (it is MaybeExcludedMedia.Included) },
        )
        assertEquals(
            selector.preferredCandidates.first().filterIsInstance<MaybeExcludedMedia.Included>().map { it.result.mediaId },
            snapshot.preferred.map { it.result.mediaId },
        )
        assertEquals(selector.alliance.available.first(), snapshot.availableAlliances)
    }

    @Test
    fun `succeeded source snapshot always carries its full result list`() = runFetchMediaSelectorTestSuite {
        initSubject("test")
        val (_, session, sources) = configureFetchSession {
            object {
                val web1 by web { tier = 0 }
            }
        }
        sources.web1.complete(
            media(kind = WEB, subjectName = initApi.subjectName),
            media(kind = WEB, subjectName = initApi.subjectName),
        )
        // 第一次观察到 Succeed 的快照就必须带着完整结果, 不能是 (Succeed, 空列表)
        val first = session.sourceSnapshots().first { list -> list.single().isSucceed }.single()
        assertIs<MediaSourceFetchState.Succeed>(first.state)
        assertEquals(2, first.results.size)
    }

    context(scope: TestScope)
    private fun testScope(): TestScope = implicit()
}
