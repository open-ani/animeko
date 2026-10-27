/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.platform.NetworkEnvironment
import me.him188.ani.datasources.api.WebMediaRoute
import me.him188.ani.datasources.api.source.MediaSourceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 运行记录跨集共享，但线路、条目、源实例和解析配置的边界必须保留。 */
class PlaybackSourceMemoryTest {
    private fun media(
        episode: Int = 1,
        source: String = "web-a",
        subject: String = "https://site/subject/1",
        channel: String = "line-1",
        configuration: String = "config-1",
    ) = TestMediaList[0].copy(
        mediaId = "$source-$channel-$episode",
        mediaSourceId = source,
        kind = MediaSourceKind.WEB,
        properties = TestMediaList[0].properties.copy(webRoute = WebMediaRoute(subject, channel, configuration)),
    )

    @Test
    fun `failures survive episode changes and do not affect other routes`() {
        val memory = PlaybackSourceMemory()
        val failed = memory.beginAttempt(1, media())
        memory.recordFailure(failed, PlaybackSourceMemory.FailureReason.RESOLUTION)
        memory.finishAttempt(failed)

        assertFalse(memory.snapshot().allows(media(episode = 2)))
        assertTrue(memory.snapshot().allows(media(channel = "line-2")))
        assertTrue(memory.snapshot().allows(media(subject = "https://site/subject/2")))
        assertTrue(memory.snapshot().allows(media(source = "web-b")))
        assertTrue(memory.snapshot().allows(media(configuration = "config-2")))
    }

    @Test
    fun `successful retry restores only that route and records the latest success`() {
        val memory = PlaybackSourceMemory()
        memory.recordFailure(memory.beginAttempt(1, media(source = "web-a")), PlaybackSourceMemory.FailureReason.PLAYBACK)
        memory.recordFailure(memory.beginAttempt(1, media(source = "web-b")), PlaybackSourceMemory.FailureReason.PLAYBACK)
        val retry = memory.beginAttempt(2, media(source = "web-b"))
        // 点选本身不能恢复自动资格，必须由播放成功的证据调用 recordSuccess。
        assertFalse(memory.snapshot().allows(media(source = "web-b")))
        memory.recordSuccess(retry)
        assertTrue(memory.snapshot().isRecentSuccess(media(episode = 3, source = "web-b")))
        assertTrue(memory.snapshot().allows(media(source = "web-b")))
        assertFalse(memory.snapshot().allows(media(source = "web-a")))
        memory.recordFailure(retry, PlaybackSourceMemory.FailureReason.PLAYBACK)
        assertNull(memory.snapshot().recentSuccess)
    }

    @Test
    fun `leaving a resource does not record failure and stale callbacks cannot change memory`() {
        val memory = PlaybackSourceMemory()
        val old = memory.beginAttempt(1, media(source = "web-a"))
        val current = memory.beginAttempt(2, media(source = "web-b"))
        memory.recordSuccess(current)
        memory.recordFailure(old, PlaybackSourceMemory.FailureReason.PLAYBACK)
        memory.recordSuccess(old)
        memory.finishAttempt(old)
        assertTrue(memory.snapshot().failures.isEmpty())
        assertTrue(memory.snapshot().isRecentSuccess(media(source = "web-b")))
        memory.finishAttempt(current)
        memory.recordFailure(current, PlaybackSourceMemory.FailureReason.PLAYBACK)
        assertTrue(memory.snapshot().failures.isEmpty())
    }

    @Test
    fun `explicit retry preserves success and ignores callbacks from the previous generation`() {
        val memory = PlaybackSourceMemory()
        memory.recordSuccess(memory.beginAttempt(1, media(source = "web-c")))
        val failed = memory.beginAttempt(2, media())
        memory.recordFailure(failed, PlaybackSourceMemory.FailureReason.RESOLUTION)
        memory.retryFailures()
        memory.recordFailure(failed, PlaybackSourceMemory.FailureReason.PLAYBACK)
        memory.recordSuccess(failed)
        assertTrue(memory.snapshot().failures.isEmpty())
        assertTrue(memory.snapshot().isRecentSuccess(media(source = "web-c")))
    }

    @Test
    fun `network identity changes clear failures while transient loss keeps them`() {
        val memory = PlaybackSourceMemory()
        memory.updateEnvironment(NetworkEnvironment("wifi-1", true))
        memory.recordSuccess(memory.beginAttempt(1, media(source = "web-c")))
        val old = memory.beginAttempt(1, media())
        memory.recordFailure(old, PlaybackSourceMemory.FailureReason.RESOLUTION)
        memory.updateEnvironment(NetworkEnvironment(null, false))
        memory.updateEnvironment(NetworkEnvironment("wifi-1", true))
        assertFalse(memory.snapshot().allows(media()))
        memory.updateEnvironment(NetworkEnvironment("mobile-2", true))
        memory.recordFailure(old, PlaybackSourceMemory.FailureReason.PLAYBACK)
        assertTrue(memory.snapshot().failures.isEmpty())
        assertTrue(memory.snapshot().isRecentSuccess(media(source = "web-c")))
        assertEquals(1L, memory.snapshot().generation)
    }

    @Test
    fun `offline errors do not poison routes`() {
        val memory = PlaybackSourceMemory()
        memory.updateEnvironment(NetworkEnvironment(null, false))
        memory.recordFailure(memory.beginAttempt(1, media()), PlaybackSourceMemory.FailureReason.PLAYBACK)
        assertTrue(memory.snapshot().failures.isEmpty())
    }

    @Test
    fun `connected unvalidated networks are unknown rather than offline`() {
        val memory = PlaybackSourceMemory()
        memory.updateEnvironment(NetworkEnvironment("vpn", isConnected = true, isValidated = false))
        assertNull(memory.snapshot().networkAvailable)
        memory.updateEnvironment(NetworkEnvironment("vpn", isConnected = true, isValidated = true))
        assertEquals(true, memory.snapshot().networkAvailable)
        assertEquals(0L, memory.snapshot().generation)
    }

    @Test
    fun `configuration replacement and source removal invalidate only related records`() {
        val memory = PlaybackSourceMemory()
        memory.recordFailure(memory.beginAttempt(1, media()), PlaybackSourceMemory.FailureReason.RESOLUTION)
        memory.recordSuccess(memory.beginAttempt(1, media(source = "web-c")))
        memory.retainConfigurations(mapOf("web-a" to "config-2", "web-c" to "config-1"))
        assertTrue(memory.snapshot().failures.isEmpty())
        assertTrue(memory.snapshot().isRecentSuccess(media(source = "web-c")))
        memory.retainConfigurations(emptyMap())
        assertNull(memory.snapshot().recentSuccess)
    }

    @Test
    fun `resources without reliable route metadata do not create cross episode records`() {
        val memory = PlaybackSourceMemory()
        val attempt = memory.beginAttempt(1, TestMediaList[0])
        memory.recordFailure(attempt, PlaybackSourceMemory.FailureReason.PLAYBACK)
        memory.recordSuccess(attempt)
        assertTrue(memory.snapshot().failures.isEmpty())
        assertNull(memory.snapshot().recentSuccess)
    }

    @Test
    fun `ongoing playback can renew its observation after an environment change`() {
        val memory = PlaybackSourceMemory()
        memory.updateEnvironment(NetworkEnvironment("wifi-1", true))
        val old = memory.beginAttempt(1, media())
        memory.updateEnvironment(NetworkEnvironment("mobile-2", true))
        val renewed = memory.renewAttempt(old)!!
        memory.recordSuccess(old)
        assertNull(memory.snapshot().recentSuccess)
        memory.recordSuccess(renewed)
        assertTrue(memory.snapshot().isRecentSuccess(media()))
        memory.finishAttempt(old)
        memory.recordFailure(renewed, PlaybackSourceMemory.FailureReason.PLAYBACK)
        assertFalse(memory.snapshot().allows(media()))
        memory.clear()
        memory.recordSuccess(renewed)
        assertTrue(memory.snapshot().failures.isEmpty())
        assertNull(memory.snapshot().recentSuccess)
    }
}
