/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.session.auth

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.repository.RepositoryNetworkException
import me.him188.ani.app.data.repository.user.QrLoginPollResult
import me.him188.ani.app.data.repository.user.QrLoginRepository
import me.him188.ani.app.data.repository.user.QrLoginScanResult
import me.him188.ani.app.data.repository.user.QrLoginSession
import me.him188.ani.app.domain.foundation.LoadError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

class QrLoginPollerTest {
    private class FakeRepository(
        private val pollResults: MutableList<() -> QrLoginPollResult>,
    ) : QrLoginRepository {
        var created = 0
        var failCreate = false

        override suspend fun createSession(deviceName: String?): QrLoginSession {
            if (failCreate) throw RepositoryNetworkException()
            created++
            return QrLoginSession("request-$created", "secret", "qr-$created", lifetime = 10.seconds)
        }

        override suspend fun poll(session: QrLoginSession): QrLoginPollResult =
            pollResults.removeFirstOrNull()?.invoke() ?: QrLoginPollResult.Pending

        override suspend fun scan(requestId: String): QrLoginScanResult = error("unused")
        override suspend fun confirm(requestId: String, approve: Boolean): Boolean = error("unused")
    }

    private fun TestScope.poller(repository: QrLoginRepository) =
        QrLoginPoller(repository, deviceName = "TV", timeSource = testScheduler.timeSource, pollInterval = 2.seconds)

    @Test
    fun `counts down, reports the scan, then finishes when approved`() = runTest {
        val repository = FakeRepository(
            mutableListOf(
                { QrLoginPollResult.Pending },
                { QrLoginPollResult.Scanned("小明") },
                { QrLoginPollResult.Approved },
            ),
        )
        val progress = poller(repository).run().toList()

        assertEquals(
            listOf(
                QrLoginProgress.Loading,
                QrLoginProgress.Waiting("qr-1", 10.seconds),
                QrLoginProgress.Waiting("qr-1", 9.seconds),
                QrLoginProgress.Waiting("qr-1", 8.seconds), // polled: pending
                QrLoginProgress.Waiting("qr-1", 7.seconds),
                QrLoginProgress.Scanned("qr-1", "小明"),
                QrLoginProgress.Scanned("qr-1", "小明"),
                QrLoginProgress.Approved,
            ),
            progress,
        )
    }

    @Test
    fun `rejected is final`() = runTest {
        val repository = FakeRepository(mutableListOf({ QrLoginPollResult.Rejected }))
        assertEquals(QrLoginProgress.Rejected, poller(repository).run().toList().last())
        assertEquals(1, repository.created)
    }

    @Test
    fun `expired session is replaced by a new one`() = runTest {
        val repository = FakeRepository(mutableListOf())
        val third = poller(repository).run().first { it is QrLoginProgress.Waiting && it.qrContent == "qr-3" }

        assertEquals(QrLoginProgress.Waiting("qr-3", 10.seconds), third)
        assertEquals(3, repository.created)
    }

    @Test
    fun `server reporting expiry early also replaces the session`() = runTest {
        val repository = FakeRepository(mutableListOf({ QrLoginPollResult.Expired }, { QrLoginPollResult.Approved }))
        val progress = poller(repository).run().toList()

        assertEquals(2, repository.created)
        assertEquals(2, progress.count { it == QrLoginProgress.Loading })
        assertEquals(QrLoginProgress.Approved, progress.last())
    }

    @Test
    fun `poll errors are retried but create errors fail`() = runTest {
        val repository = FakeRepository(
            mutableListOf({ throw RepositoryNetworkException() }, { QrLoginPollResult.Approved }),
        )
        assertEquals(QrLoginProgress.Approved, poller(repository).run().toList().last())

        repository.failCreate = true
        val failed = poller(repository).run().toList()
        assertEquals(QrLoginProgress.Loading, failed.first())
        assertIs<LoadError.NetworkError>(assertIs<QrLoginProgress.Failed>(failed.last()).error)
    }
}
