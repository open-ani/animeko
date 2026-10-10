/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.install

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.data.persistent.database.dao.PlaybackHistoryRecordEntity
import me.him188.ani.app.data.persistent.database.dao.createMemoryPlaybackHistoryDao
import me.him188.ani.app.data.repository.player.EpisodeHistories
import me.him188.ani.app.data.repository.player.EpisodePlayHistoryRepositoryImpl
import me.him188.ani.app.domain.session.InvalidSessionReason
import me.him188.ani.app.domain.session.SessionEvent
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.client.apis.UserProfileAniApi
import me.him188.ani.client.infrastructure.HttpResponse
import me.him188.ani.client.infrastructure.wrap
import me.him188.ani.client.models.AniReportInstallRequest
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant
import io.ktor.client.statement.HttpResponse as KtorHttpResponse

class InstallReporterTest {
    private val nowMillis = 1_800_000_000_000L

    private val historyDao = createMemoryPlaybackHistoryDao()
    private val historyStore = MemoryDataStore(EpisodeHistories.Empty)
    private val playHistory = EpisodePlayHistoryRepositoryImpl(
        dataStore = historyStore,
        playbackHistoryDao = historyDao,
        nowMillis = { nowMillis },
    )
    private val installStore = MemoryDataStore(InstallInfo.Initial)
    private val session = FakeSession()
    private val currentUserId = MutableStateFlow<String?>(null)

    private class FakeSession : SessionStateProvider {
        override val stateFlow = MutableStateFlow<SessionState>(SessionState.Invalid(InvalidSessionReason.NO_TOKEN))
        override val eventFlow: Flow<SessionEvent> = emptyFlow()
    }

    /** 记录 reportInstall 的请求, 按 [failures] 依次失败, 之后返回 [okResponse]. */
    private class FakeUserProfileApi(
        private val okResponse: KtorHttpResponse,
        vararg failures: Throwable,
    ) : UserProfileAniApi(httpClientEngine = MockEngine { respond("") }) {
        private val failures = ArrayDeque(failures.toList())
        val requests = mutableListOf<AniReportInstallRequest>()

        override suspend fun reportInstall(
            userAgent: String,
            aniReportInstallRequest: AniReportInstallRequest,
        ): HttpResponse<Any> {
            requests += aniReportInstallRequest
            failures.removeFirstOrNull()?.let { throw it }
            return okResponse.wrap()
        }
    }

    private class FakeInvoker(val api: UserProfileAniApi) : ApiInvoker<UserProfileAniApi> {
        override suspend fun <R> invoke(action: suspend UserProfileAniApi.() -> R): R = api.action()
    }

    private class FixedClock(private val millis: Long) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(millis)
    }

    private suspend fun okResponse(): KtorHttpResponse {
        return HttpClient(MockEngine { respond("") }).get("http://test/")
    }

    private suspend fun clientRequestException(status: HttpStatusCode): ClientRequestException {
        val client = HttpClient(MockEngine { respond("rejected", status) }) { expectSuccess = true }
        return runCatching { client.get("http://test/") }.exceptionOrNull() as ClientRequestException
    }

    private fun TestScope.createReporter(
        api: UserProfileAniApi,
        scope: CoroutineScope = backgroundScope,
    ) = InstallReporter(
        store = installStore,
        playHistoryRepository = playHistory,
        api = FakeInvoker(api),
        sessionStateProvider = session,
        currentUserId = currentUserId,
        scope = scope,
        clock = FixedClock(nowMillis),
    )

    private suspend fun addRecord(episodeId: Int, updatedAtMillis: Long, deletedAtMillis: Long? = null) {
        historyDao.upsertRecord(
            PlaybackHistoryRecordEntity(
                episodeId = episodeId,
                positionMillis = 1_000,
                updatedAtMillis = updatedAtMillis,
                deletedAtMillis = deletedAtMillis,
            ),
        )
    }

    private fun request(firstLaunchAtMillis: Long, localEpisodes: Int, hadPreviousLogin: Boolean) =
        AniReportInstallRequest(
            firstLaunchAt = Instant.fromEpochMilliseconds(firstLaunchAtMillis).toString(),
            localEpisodesBeforeLogin = localEpisodes,
            hadPreviousLogin = hadPreviousLogin,
        )

    // region first launch

    @Test
    fun `new install records now as first launch`() = runTest {
        createReporter(FakeUserProfileApi(okResponse())).start()
        runCurrent()

        assertEquals(InstallInfo(firstLaunchAtMillis = nowMillis), installStore.data.value)
    }

    @Test
    fun `existing install estimates first launch from the earliest playback record`() = runTest {
        addRecord(1, updatedAtMillis = nowMillis - 1_000)
        addRecord(2, updatedAtMillis = nowMillis - 5_000, deletedAtMillis = nowMillis - 100)
        addRecord(3, updatedAtMillis = 0, deletedAtMillis = nowMillis - 9_000)

        createReporter(FakeUserProfileApi(okResponse())).start()
        runCurrent()

        assertEquals(InstallInfo(firstLaunchAtMillis = nowMillis - 5_000), installStore.data.value)
    }

    @Test
    fun `install that synced playback history ignores records for first launch`() = runTest {
        addRecord(1, updatedAtMillis = nowMillis - 5_000)
        historyStore.data.value = EpisodeHistories(lastSyncAtMillis = nowMillis - 1)

        createReporter(FakeUserProfileApi(okResponse())).start()
        runCurrent()

        assertEquals(InstallInfo(firstLaunchAtMillis = nowMillis, hasLoggedIn = true), installStore.data.value)
    }

    @Test
    fun `install logged in at startup ignores records for first launch`() = runTest {
        addRecord(1, updatedAtMillis = nowMillis - 5_000)
        session.stateFlow.value = SessionState.Valid(bangumiConnected = false)

        createReporter(FakeUserProfileApi(okResponse())).start()
        runCurrent()

        assertEquals(InstallInfo(firstLaunchAtMillis = nowMillis, hasLoggedIn = true), installStore.data.value)
    }

    @Test
    fun `first launch is kept once initialized`() = runTest {
        installStore.data.value = InstallInfo(firstLaunchAtMillis = 123)
        addRecord(1, updatedAtMillis = 100)

        createReporter(FakeUserProfileApi(okResponse())).start()
        runCurrent()

        assertEquals(InstallInfo(firstLaunchAtMillis = 123), installStore.data.value)
    }

    // endregion

    // region building report

    @Test
    fun `login report counts all local records including deleted ones`() = runTest {
        addRecord(1, updatedAtMillis = nowMillis - 3_000)
        addRecord(2, updatedAtMillis = nowMillis - 2_000)
        addRecord(3, updatedAtMillis = nowMillis - 1_000, deletedAtMillis = nowMillis - 500)
        val reporter = createReporter(FakeUserProfileApi(okResponse()))
        reporter.start()
        runCurrent()

        reporter.beforeNewLogin("u1")

        assertEquals(
            InstallInfo(
                firstLaunchAtMillis = nowMillis - 3_000,
                hasLoggedIn = true,
                pendingReport = PendingInstallReport(
                    userId = "u1",
                    firstLaunchAtMillis = nowMillis - 3_000,
                    localEpisodesBeforeLogin = 3,
                    hadPreviousLogin = false,
                ),
            ),
            installStore.data.value,
        )
    }

    @Test
    fun `login before startup initialization still initializes first launch`() = runTest {
        addRecord(1, updatedAtMillis = nowMillis - 3_000)

        createReporter(FakeUserProfileApi(okResponse())).beforeNewLogin("u1")

        assertEquals(
            PendingInstallReport("u1", nowMillis - 3_000, localEpisodesBeforeLogin = 1, hadPreviousLogin = false),
            installStore.data.value.pendingReport,
        )
        assertEquals(nowMillis - 3_000, installStore.data.value.firstLaunchAtMillis)
    }

    @Test
    fun `another user's login replaces the pending report and marks previous login`() = runTest {
        val reporter = createReporter(FakeUserProfileApi(okResponse()))
        reporter.start()
        runCurrent()

        reporter.beforeNewLogin("u1")
        addRecord(1, updatedAtMillis = nowMillis)
        reporter.beforeNewLogin("u2")

        assertEquals(
            PendingInstallReport("u2", nowMillis, localEpisodesBeforeLogin = 1, hadPreviousLogin = true),
            installStore.data.value.pendingReport,
        )
    }

    @Test
    fun `same user's login keeps the first pending report`() = runTest {
        val reporter = createReporter(FakeUserProfileApi(okResponse()))
        reporter.start()
        runCurrent()

        reporter.beforeNewLogin("u1")
        addRecord(1, updatedAtMillis = nowMillis)
        reporter.beforeNewLogin("u1")

        assertEquals(
            PendingInstallReport("u1", nowMillis, localEpisodesBeforeLogin = 0, hadPreviousLogin = false),
            installStore.data.value.pendingReport,
        )
    }

    // endregion

    // region sending

    @Test
    fun `report is sent once the logged in user is confirmed`() = runTest {
        val api = FakeUserProfileApi(okResponse())
        val reporter = createReporter(api)
        reporter.start()
        runCurrent()

        reporter.beforeNewLogin("u1")
        runCurrent()
        assertEquals(emptyList(), api.requests)

        currentUserId.value = "u1"
        runCurrent()

        assertEquals(listOf(request(nowMillis, localEpisodes = 0, hadPreviousLogin = false)), api.requests)
        assertEquals(
            InstallInfo(firstLaunchAtMillis = nowMillis, hasLoggedIn = true, reportedUserIds = setOf("u1")),
            installStore.data.value,
        )
    }

    @Test
    fun `reported user logging in again creates no report`() = runTest {
        val api = FakeUserProfileApi(okResponse())
        val reporter = createReporter(api)
        reporter.start()
        currentUserId.value = "u1"
        runCurrent()
        reporter.beforeNewLogin("u1")
        runCurrent()

        reporter.beforeNewLogin("u1")
        runCurrent()

        assertEquals(1, api.requests.size)
        assertEquals(null, installStore.data.value.pendingReport)
    }

    @Test
    fun `failed report is kept and sent on next start for the same user`() = runTest {
        currentUserId.value = "u1"
        val firstRun = CoroutineScope(backgroundScope.coroutineContext + Job())
        val failingApi = FakeUserProfileApi(okResponse(), IOException("offline"))
        val reporter = createReporter(failingApi, firstRun)
        reporter.start()
        runCurrent()
        reporter.beforeNewLogin("u1")
        runCurrent()

        assertEquals(1, failingApi.requests.size)
        val pending = PendingInstallReport("u1", nowMillis, localEpisodesBeforeLogin = 0, hadPreviousLogin = false)
        assertEquals(pending, installStore.data.value.pendingReport)
        firstRun.cancel()

        val api = FakeUserProfileApi(okResponse())
        createReporter(api).start()
        runCurrent()

        assertEquals(listOf(request(nowMillis, localEpisodes = 0, hadPreviousLogin = false)), api.requests)
        assertEquals(null, installStore.data.value.pendingReport)
        assertEquals(setOf("u1"), installStore.data.value.reportedUserIds)
    }

    @Test
    fun `pending report is not sent while another user is logged in`() = runTest {
        installStore.data.value = InstallInfo(
            firstLaunchAtMillis = nowMillis,
            hasLoggedIn = true,
            pendingReport = PendingInstallReport("u1", nowMillis, localEpisodesBeforeLogin = 5, hadPreviousLogin = false),
        )
        session.stateFlow.value = SessionState.Valid(bangumiConnected = false)
        currentUserId.value = "u2"
        val api = FakeUserProfileApi(okResponse())

        createReporter(api).start()
        runCurrent()

        assertEquals(emptyList(), api.requests)
        assertEquals("u1", installStore.data.value.pendingReport?.userId)
    }

    @Test
    fun `server rejection drops the report`() = runTest {
        currentUserId.value = "u1"
        val api = FakeUserProfileApi(okResponse(), clientRequestException(HttpStatusCode.BadRequest))
        val reporter = createReporter(api)
        reporter.start()
        runCurrent()

        reporter.beforeNewLogin("u1")
        runCurrent()

        assertEquals(1, api.requests.size)
        assertEquals(null, installStore.data.value.pendingReport)
        assertEquals(setOf("u1"), installStore.data.value.reportedUserIds)
    }

    @Test
    fun `other client errors keep the report for retry`() = runTest {
        currentUserId.value = "u1"
        val api = FakeUserProfileApi(okResponse(), clientRequestException(HttpStatusCode.NotFound))
        val reporter = createReporter(api)
        reporter.start()
        runCurrent()

        reporter.beforeNewLogin("u1")
        runCurrent()

        assertEquals(1, api.requests.size)
        assertEquals("u1", installStore.data.value.pendingReport?.userId)
        assertEquals(emptySet(), installStore.data.value.reportedUserIds)
    }

    @Test
    fun `user already logged in at startup is not reported`() = runTest {
        session.stateFlow.value = SessionState.Valid(bangumiConnected = false)
        currentUserId.value = "u1"
        val api = FakeUserProfileApi(okResponse())

        createReporter(api).start()
        runCurrent()

        assertEquals(emptyList(), api.requests)
        assertEquals(null, installStore.data.value.pendingReport)
    }

    // endregion
}
