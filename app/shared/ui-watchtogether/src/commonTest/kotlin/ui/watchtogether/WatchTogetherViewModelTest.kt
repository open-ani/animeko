/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.watchtogether

import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.network.WatchTogetherApiService
import me.him188.ani.app.data.network.WatchTogetherServerEvent
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.data.repository.user.PreferencesRepositoryImpl
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.session.SessionEvent
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.domain.watchtogether.LocalPlaybackBridge
import me.him188.ani.app.domain.watchtogether.PlaybackAutomationGate
import me.him188.ani.app.domain.watchtogether.WatchTogetherManager
import me.him188.ani.app.domain.watchtogether.WatchTogetherState
import me.him188.ani.client.models.AniReportWatchTogetherStateRequest
import me.him188.ani.client.models.AniWatchTogetherJoinResponse
import me.him188.ani.client.models.AniWatchTogetherReportResponse
import me.him188.ani.utils.platform.annotations.TestOnly
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(TestOnly::class)
class WatchTogetherViewModelTest {
    @Test
    fun cancellingAnInFlightJoinLeavesTheRoomAndAllowsRetry() = runTest {
        withViewModel { vm, manager, api ->
            vm.joinRoom("Room", "secret")
            runCurrent()
            assertIs<WatchTogetherState.Joining>(manager.state.value)
            vm.joinRoom("Room", "secret")
            runCurrent()
            assertEquals(1, api.joinCalls)

            vm.cancelJoin()
            runCurrent()
            assertEquals(1, api.cancelledCalls)
            assertIs<WatchTogetherState.Idle>(manager.state.value)
            assertNull(vm.joinFailure.value)

            vm.joinRoom("Room", "secret")
            runCurrent()
            assertEquals(2, api.joinCalls)
            vm.leaveRoom()
            runCurrent()
            assertIs<WatchTogetherState.Idle>(manager.state.value)
        }
    }

    @Test
    fun joinTimeoutClearsThePendingRoomAndExposesAnEditableError() = runTest {
        withViewModel { vm, manager, api ->
            vm.joinRoom("Room", "secret")
            runCurrent()
            advanceTimeBy(30_001)
            runCurrent()
            assertEquals(1, api.cancelledCalls)
            assertIs<WatchTogetherState.Idle>(manager.state.value)
            assertEquals(WatchTogetherJoinError.Timeout, vm.joinFailure.value)
            vm.clearJoinError()
            assertNull(vm.joinFailure.value)
        }
    }

    private suspend fun TestScope.withViewModel(
        block: suspend (WatchTogetherViewModel, WatchTogetherManager, PendingApi) -> Unit,
    ) {
        val settings = PreferencesRepositoryImpl(MemoryDataStore(emptyPreferences()))
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
            })
        }
        val vm = WatchTogetherViewModel(app.koin, StandardTestDispatcher(testScheduler))
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
}
