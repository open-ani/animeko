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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
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
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
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
    fun setUp() { Dispatchers.setMain(Dispatchers.Unconfined) }

    @AfterTest
    fun tearDown() { stopKoin(); Dispatchers.resetMain() }

    @Test
    fun cancellingAnInFlightJoinLeavesTheRoomAndAllowsRetry() = runBlocking {
        withTimeout(5_000) {
            withViewModel { vm, manager, api ->
                vm.onIntent(TvTogetherIntent.RoomName("Room"))
                vm.onIntent(TvTogetherIntent.Password("secret"))
                vm.onIntent(TvTogetherIntent.Join)
                api.joinCalls.first { it == 1 }
                assertIs<WatchTogetherState.Joining>(manager.state.value)

                vm.onIntent(TvTogetherIntent.CancelJoin)
                manager.state.first { it is WatchTogetherState.Idle }
                assertEquals(1, api.cancelledCalls.value)
                assertNull(vm.uiState.value.error)
                vm.uiState.first { !it.joining }

                vm.onIntent(TvTogetherIntent.Join)
                api.joinCalls.first { it == 2 }
                vm.onIntent(TvTogetherIntent.CancelJoin)
                manager.state.first { it is WatchTogetherState.Idle }
            }
        }
    }

    @Test
    fun clearingTheRememberedRoomNameIsValidatedBeforeJoining() = runBlocking {
        withTimeout(5_000) {
            withViewModel(lastRoomName = "Remembered room") { vm, _, api ->
                vm.uiState.first { it.roomName == "Remembered room" }
                vm.onIntent(TvTogetherIntent.RoomName(""))
                vm.onIntent(TvTogetherIntent.Join)
                val state = vm.uiState.first { it.error == TvTogetherError.EmptyName }
                assertEquals(0, api.joinCalls.value)
                assertEquals("", state.roomName)
            }
        }
    }

    private suspend fun withViewModel(
        lastRoomName: String = "",
        block: suspend (TvWatchTogetherViewModel, WatchTogetherManager, PendingApi) -> Unit,
    ) {
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val settings = PreferencesRepositoryImpl(MemoryDataStore(emptyPreferences()))
        settings.watchTogetherSettings.update { copy(lastRoomName = lastRoomName) }
        val sessions = object : SessionStateProvider {
            override val stateFlow = MutableStateFlow<SessionState>(SessionState.Valid(false))
            override val eventFlow = emptyFlow<SessionEvent>()
        }
        val bridge = LocalPlaybackBridge()
        val api = PendingApi()
        val manager = WatchTogetherManager(appScope, api, settings.watchTogetherSettings, sessions, bridge, PlaybackAutomationGate())
        startKoin {
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
                            appScope,
                            refreshSession = { awaitCancellation() },
                        ),
                        coroutineContext = appScope.coroutineContext,
                    )
                }
            })
        }
        val vm = TvWatchTogetherViewModel(manager, settings, sessions)
        try {
            block(vm, manager, api)
        } finally {
            vm.backgroundScope.coroutineContext.job.cancelAndJoin()
            appScope.coroutineContext.job.cancelAndJoin()
        }
    }

    private class PendingApi : WatchTogetherApiService {
        val joinCalls = MutableStateFlow(0)
        val cancelledCalls = MutableStateFlow(0)
        override suspend fun join(roomName: String, password: String, following: Boolean): AniWatchTogetherJoinResponse {
            joinCalls.update { it + 1 }
            try {
                awaitCancellation()
            } finally {
                cancelledCalls.update { it + 1 }
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
