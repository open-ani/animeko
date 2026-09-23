/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.watchtogether

import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.him188.ani.app.data.network.WatchTogetherApiService
import me.him188.ani.app.data.network.WatchTogetherServerEvent
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.data.repository.user.PreferencesRepositoryImpl
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.data.repository.user.TokenRepository
import me.him188.ani.app.data.repository.user.TokenSave
import me.him188.ani.app.data.repository.user.UserRepository
import me.him188.ani.app.domain.session.SessionEvent
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.domain.watchtogether.LocalPlaybackBridge
import me.him188.ani.app.domain.watchtogether.PlaybackAutomationGate
import me.him188.ani.app.domain.watchtogether.WatchTogetherManager
import me.him188.ani.app.domain.watchtogether.WatchTogetherState
import me.him188.ani.client.models.AniReportWatchTogetherStateRequest
import me.him188.ani.client.models.AniWatchTogetherJoinResponse
import me.him188.ani.client.models.AniWatchTogetherReportResponse
import me.him188.ani.utils.ktor.ApiInvoker
import me.him188.ani.utils.platform.annotations.TestOnly
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(TestOnly::class)
class TvWatchTogetherViewModelTest {
    @BeforeTest
    fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }

    @AfterTest
    fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun cancellingAnInFlightJoinLeavesTheRoomAndAllowsRetry() = runTest {
        withViewModel { vm, manager, api ->
            vm.onIntent(TvTogetherIntent.RoomName("Room"))
            vm.onIntent(TvTogetherIntent.Password("secret"))
            runCurrent()
            vm.onIntent(TvTogetherIntent.Join)
            runCurrent()
            assertIs<WatchTogetherState.Joining>(manager.state.value)
            vm.onIntent(TvTogetherIntent.RoomName("Room"))
            vm.onIntent(TvTogetherIntent.Password("secret"))
            runCurrent()
            vm.onIntent(TvTogetherIntent.Join)
            runCurrent()
            assertEquals(1, api.joinCalls)

            vm.onIntent(TvTogetherIntent.CancelJoin)
            runCurrent()
            assertEquals(1, api.cancelledCalls)
            assertIs<WatchTogetherState.Idle>(manager.state.value)
            assertNull(vm.uiState.value.error)

            vm.onIntent(TvTogetherIntent.RoomName("Room"))
            vm.onIntent(TvTogetherIntent.Password("secret"))
            runCurrent()
            vm.onIntent(TvTogetherIntent.Join)
            runCurrent()
            assertEquals(2, api.joinCalls)
            vm.onIntent(TvTogetherIntent.Leave)
            runCurrent()
            assertIs<WatchTogetherState.Idle>(manager.state.value)
        }
    }

    @Test
    fun joinTimeoutClearsThePendingRoomAndExposesAnEditableError() = runTest {
        withViewModel { vm, manager, api ->
            vm.onIntent(TvTogetherIntent.RoomName("Room"))
            vm.onIntent(TvTogetherIntent.Password("secret"))
            runCurrent()
            vm.onIntent(TvTogetherIntent.Join)
            runCurrent()
            advanceTimeBy(30_001)
            runCurrent()
            assertEquals(1, api.cancelledCalls)
            assertIs<WatchTogetherState.Idle>(manager.state.value)
            assertEquals(TvTogetherError.Timeout, vm.uiState.value.error)
            vm.onIntent(TvTogetherIntent.RoomName("Edited room"))
            runCurrent()
            assertNull(vm.uiState.value.error)
        }
    }

    @Test
    fun clearingTheRememberedRoomNameIsValidatedBeforeJoining() = runTest {
        withViewModel(lastRoomName = "Remembered room") { vm, _, api ->
            runCurrent()
            assertEquals("Remembered room", vm.uiState.value.roomName)
            vm.onIntent(TvTogetherIntent.RoomName(""))
            vm.onIntent(TvTogetherIntent.Join)
            runCurrent()
            assertEquals(0, api.joinCalls)
            assertEquals("", vm.uiState.value.roomName)
            assertEquals(TvTogetherError.EmptyName, vm.uiState.value.error)
        }
    }

    private suspend fun TestScope.withViewModel(
        lastRoomName: String = "",
        block: suspend (TvWatchTogetherViewModel, WatchTogetherManager, PendingApi) -> Unit,
    ) {
        val settings = PreferencesRepositoryImpl(MemoryDataStore(emptyPreferences()))
        settings.watchTogetherSettings.update { copy(lastRoomName = lastRoomName) }
        val sessions = object : SessionStateProvider {
            override val stateFlow = MutableStateFlow<SessionState>(SessionState.Valid(false))
            override val eventFlow = emptyFlow<SessionEvent>()
        }
        val bridge = LocalPlaybackBridge()
        val api = PendingApi()
        val manager = WatchTogetherManager(backgroundScope, api, settings.watchTogetherSettings, sessions, bridge, PlaybackAutomationGate())
        val app = koinApplication {
            modules(module {
                single<SettingsRepository> { settings }
                single<SessionStateProvider> { sessions }
                single { bridge }
                single { manager }
                single {
                    UserRepository(
                        dataStore = MemoryDataStore(null),
                        sessionStateProvider = get(),
                        userApi = pendingApi(), authApi = pendingApi(), profileApi = pendingApi(),
                        bangumiApi = pendingApi(), oauthApi = pendingApi(),
                        sessionManager = SessionManager(
                            TokenRepository(MemoryDataStore(TokenSave.Initial)),
                            backgroundScope,
                            refreshSession = { awaitCancellation() },
                        ),
                        coroutineContext = backgroundScope.coroutineContext,
                    )
                }
            })
        }
        val vm = TvWatchTogetherViewModel(app.koin, StandardTestDispatcher(testScheduler))
        try {
            block(vm, manager, api)
        } finally {
            vm.backgroundScope.cancel()
            app.close()
        }
    }

    private class PendingApi : WatchTogetherApiService {
        var joinCalls = 0
        var cancelledCalls = 0
        override suspend fun join(roomName: String, password: String, following: Boolean): AniWatchTogetherJoinResponse {
            joinCalls++
            try {
                awaitCancellation()
            } finally {
                cancelledCalls++
            }
        }
        override suspend fun report(roomId: String, request: AniReportWatchTogetherStateRequest): AniWatchTogetherReportResponse =
            error("No room joined")
        override suspend fun leave(roomId: String, sessionNonce: String) = Unit
        override fun events(roomId: String, sessionNonce: String) = emptyFlow<WatchTogetherServerEvent>()
    }

    private fun <Api> pendingApi() = object : ApiInvoker<Api> {
        override suspend fun <R> invoke(action: suspend Api.() -> R): R = awaitCancellation()
    }
}
