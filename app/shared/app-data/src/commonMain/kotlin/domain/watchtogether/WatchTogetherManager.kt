/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.watchtogether

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.data.models.preference.RememberedRoomSession
import me.him188.ani.app.data.models.preference.WatchTogetherSettings
import me.him188.ani.app.data.network.WatchTogetherApiService
import me.him188.ani.app.data.network.WatchTogetherJoinException
import me.him188.ani.app.data.network.WatchTogetherJoinFailure
import me.him188.ani.app.data.network.WatchTogetherServerEvent
import me.him188.ani.app.data.repository.user.Settings
import me.him188.ani.app.domain.session.InvalidSessionReason
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.navigation.EpisodeNavigationGuardHandle
import me.him188.ani.app.navigation.EpisodeNavigationGuardRegistry
import me.him188.ani.client.models.AniReportWatchTogetherStateRequest
import me.him188.ani.client.models.AniWatchTogetherMemberState
import me.him188.ani.client.models.AniWatchTogetherMembership
import me.him188.ani.client.models.AniWatchTogetherRoomSnapshot
import me.him188.ani.client.models.AniWatchTogetherRoomStatus
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import me.him188.ani.utils.platform.currentTimeMillis
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

class WatchTogetherManager(
    private val scope: CoroutineScope,
    private val api: WatchTogetherApiService,
    private val settings: Settings<WatchTogetherSettings>,
    private val sessionStateProvider: SessionStateProvider,
    private val playbackBridge: LocalPlaybackBridge,
    private val automationGate: PlaybackAutomationGate,
    private val localNowMillis: () -> Long = ::currentTimeMillis,
    private val reconnectJitterMillis: () -> Long = { Random.nextLong(0L, 1_001L) },
) {
    private val started = atomic(false)
    private val available = atomic(false)
    private val operationMutex = Mutex()
    private val reportMutex = Mutex()
    private val appForeground = MutableStateFlow(true)

    private val _state = MutableStateFlow<WatchTogetherState>(WatchTogetherState.Disabled)
    val state: StateFlow<WatchTogetherState> = _state

    private val effectChannel = Channel<WatchTogetherEffect>(capacity = Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    private var lifecycleJob: Job? = null
    private var sessionJob: Job? = null
    private var autoJoinJob: Job? = null
    private var navigationGuard: EpisodeNavigationGuardHandle? = null

    fun start() {
        if (!started.compareAndSet(expect = false, update = true)) return
        lifecycleJob = scope.launch(CoroutineName("WatchTogetherManager.lifecycle")) {
            combine(settings.flow, sessionStateProvider.stateFlow) { config, session ->
                config to session
            }.collect { (config, sessionState) ->
                val canRun = config.enabled && sessionState is SessionState.Valid
                available.value = canRun
                if (!canRun) {
                    val clearRemembered = !config.enabled ||
                            (sessionState is SessionState.Invalid && sessionState.reason == InvalidSessionReason.NO_TOKEN)
                    deactivate(clearRemembered)
                    return@collect
                }

                var shouldAutoJoin = false
                operationMutex.withLock {
                    when (val current = _state.value) {
                        WatchTogetherState.Disabled -> {
                            _state.value = WatchTogetherState.Idle
                            shouldAutoJoin = config.rememberedSession != null
                        }

                        is WatchTogetherState.InRoom -> current.session.updateFollowing(config.followHost)
                        WatchTogetherState.Idle, is WatchTogetherState.Joining -> Unit
                    }
                }
                if (shouldAutoJoin) scheduleAutoJoin()
            }
        }
    }

    suspend fun join(roomName: String, password: String): Result<Unit> {
        autoJoinJob?.cancelAndJoin()
        autoJoinJob = null
        return joinInternal(roomName.trim(), password, silent = false).map { Unit }
    }

    suspend fun leave() {
        autoJoinJob?.cancelAndJoin()
        autoJoinJob = null
        operationMutex.withLock {
            val session = (_state.value as? WatchTogetherState.InRoom)?.session
            stopSessionResources()
            clearRememberedSession()
            _state.value = if (available.value) WatchTogetherState.Idle else WatchTogetherState.Disabled
            if (session != null) {
                runCatching { api.leave(session.roomId, session.sessionNonce) }
                    .onFailure { logger.debug { "Failed to leave watch-together room: ${it.message}" } }
            }
        }
    }

    suspend fun setFollowing(following: Boolean) {
        val session = operationMutex.withLock {
            val currentSession = (_state.value as? WatchTogetherState.InRoom)?.session
            currentSession?.updateFollowing(following)
            settings.update { copy(followHost = following) }
            currentSession
        }
        if (session != null) reportOnce(session)
    }

    fun setAppForeground(foreground: Boolean) {
        appForeground.value = foreground
    }

    fun serverNowMillis(): Long =
        (_state.value as? WatchTogetherState.InRoom)?.session?.serverClock?.now() ?: localNowMillis()

    private suspend fun deactivate(clearRemembered: Boolean) {
        autoJoinJob?.cancel()
        autoJoinJob = null
        operationMutex.withLock {
            val session = (_state.value as? WatchTogetherState.InRoom)?.session
            stopSessionResources()
            _state.value = WatchTogetherState.Disabled
            if (clearRemembered) clearRememberedSession()
            if (session != null) {
                runCatching { api.leave(session.roomId, session.sessionNonce) }
            }
        }
    }

    private fun scheduleAutoJoin() {
        if (autoJoinJob?.isActive == true) return
        autoJoinJob = scope.launch(CoroutineName("WatchTogetherManager.autoJoin")) {
            var retryDelayMillis = AUTO_JOIN_INITIAL_RETRY_MILLIS
            while (currentCoroutineContext().isActive && available.value) {
                val remembered = settings.flow.first().rememberedSession ?: return@launch
                if (_state.value !is WatchTogetherState.Idle) return@launch
                val result = joinInternal(remembered.roomName, remembered.password, silent = true)
                if (result.isSuccess) {
                    if (result.getOrThrow().created) effectChannel.send(WatchTogetherEffect.Rejoined)
                    return@launch
                }
                when ((result.exceptionOrNull() as? WatchTogetherJoinException)?.failure) {
                    WatchTogetherJoinFailure.ROOM_CLOSED -> {
                        clearRememberedSession()
                        effectChannel.send(WatchTogetherEffect.RoomEnded(WatchTogetherRoomEndReason.ROOM_CLOSED))
                        return@launch
                    }

                    WatchTogetherJoinFailure.WRONG_PASSWORD,
                    WatchTogetherJoinFailure.INVALID_NAME,
                    WatchTogetherJoinFailure.INVALID_PASSWORD,
                        -> {
                        clearRememberedSession()
                        effectChannel.send(WatchTogetherEffect.RejoinFailed)
                        return@launch
                    }

                    WatchTogetherJoinFailure.ROOM_FULL,
                    WatchTogetherJoinFailure.RATE_LIMITED,
                    WatchTogetherJoinFailure.TEMPORARY,
                    null,
                        -> Unit
                }
                delay(retryDelayMillis)
                retryDelayMillis = min(retryDelayMillis * 2, AUTO_JOIN_MAX_RETRY_MILLIS)
            }
        }
    }

    private suspend fun joinInternal(
        roomName: String,
        password: String,
        silent: Boolean,
    ): Result<WatchTogetherJoinOutcome> {
        if (roomName.isBlank()) return Result.failure(IllegalArgumentException("roomName must not be blank"))
        return operationMutex.withLock {
            if (!available.value || _state.value !is WatchTogetherState.Idle) {
                return@withLock Result.failure(IllegalStateException("Watch Together is not ready to join"))
            }
            _state.value = WatchTogetherState.Joining(roomName)
            val config = settings.flow.first()
            val sentAt = localNowMillis()
            try {
                val response = api.join(roomName, password, config.followHost)
                val receivedAt = localNowMillis()
                val clock = ServerClock(localNowMillis)
                clock.recordSample(response.serverTime, sentAt, receivedAt)
                val session = RoomSession(
                    roomId = response.roomId,
                    roomName = response.snapshot.roomName,
                    isHost = response.isHost,
                    sessionNonce = response.sessionNonce,
                    initialSnapshot = response.snapshot,
                    initialFollowing = config.followHost,
                    serverClock = clock,
                )
                settings.update {
                    copy(
                        lastRoomName = session.roomName,
                        rememberedSession = RememberedRoomSession(
                            roomName = session.roomName,
                            password = password,
                            joinedAt = receivedAt,
                        ),
                    )
                }
                _state.value = WatchTogetherState.InRoom(session)
                startSession(session)
                Result.success(WatchTogetherJoinOutcome(created = response.created))
            } catch (throwable: Throwable) {
                currentCoroutineContext().ensureActive()
                _state.value = if (available.value) WatchTogetherState.Idle else WatchTogetherState.Disabled
                if (!silent) logger.warn(throwable) { "Failed to join watch-together room '$roomName'" }
                Result.failure(throwable)
            }
        }
    }

    private fun startSession(session: RoomSession) {
        stopSessionResources()
        sessionJob = scope.launch(CoroutineName("WatchTogetherRoom[${session.roomId}]")) {
            supervisorScope {
                launch(CoroutineName("sse")) { runEventSource(session) }
                launch(CoroutineName("report-periodic")) { runPeriodicReports(session) }
                launch(CoroutineName("report-discontinuities")) {
                    playbackBridge.discontinuities.collect {
                        if (appForeground.value || session.isHost) reportOnce(session)
                    }
                }
                launch(CoroutineName("report-on-foreground")) {
                    appForeground.drop(1).collect { foreground ->
                        if (foreground) reportOnce(session)
                    }
                }
                launch(CoroutineName("follower-mode")) { observeFollowerMode(session) }
                launch(CoroutineName("guidance")) { observeGuidance(session) }
                launch(CoroutineName("local-deviation")) { observeLocalDeviation(session) }
                launch(CoroutineName("correction")) { runContinuousCorrection(session) }
                launch(CoroutineName("correction-toast")) { observeCorrectionToasts(session) }
            }
        }
        scope.launch(CoroutineName("WatchTogetherRoom.initialReport")) { reportOnce(session) }
    }

    private fun stopSessionResources() {
        sessionJob?.cancel()
        sessionJob = null
        navigationGuard?.dispose()
        navigationGuard = null
        automationGate.setSuppressed(false)
        playbackBridge.setTargetPlayback(null)
    }

    private suspend fun runEventSource(session: RoomSession) {
        appForeground.collectLatest { foreground ->
            if (foreground) {
                runEventSourceWhileForeground(session)
            } else {
                session.updateConnection(WatchTogetherConnectionState.Reconnecting)
            }
        }
    }

    private suspend fun runEventSourceWhileForeground(session: RoomSession) {
        var consecutiveFailures = 0
        var degraded = false
        while (currentCoroutineContext().isActive && isCurrent(session)) {
            if (degraded) delay(SSE_DEGRADED_RETRY_MILLIS)
            session.updateConnection(
                if (degraded) WatchTogetherConnectionState.DegradedPolling
                else WatchTogetherConnectionState.Reconnecting,
            )

            var receivedAny = false
            var silenceTimeout = false
            var throttledBye = false
            try {
                val eventChannel = Channel<WatchTogetherServerEvent>(Channel.RENDEZVOUS)
                supervisorScope {
                    val collector = launch {
                        try {
                            api.events(session.roomId, session.sessionNonce).collect { eventChannel.send(it) }
                        } finally {
                            eventChannel.close()
                        }
                    }
                    try {
                        while (currentCoroutineContext().isActive) {
                            val result = withTimeoutOrNull(SSE_SILENCE_TIMEOUT_MILLIS) {
                                eventChannel.receiveCatching()
                            }
                            if (result == null) {
                                silenceTimeout = true
                                break
                            }
                            val event = result.getOrNull() ?: break
                            receivedAny = true
                            consecutiveFailures = 0
                            degraded = false
                            session.updateConnection(WatchTogetherConnectionState.ConnectedSse)
                            // Byes other than lifetime/shutdown/membership (e.g. RATE_LIMITED,
                            // UNAUTHORIZED) mean the server rejects us right now; reconnecting
                            // immediately would just hammer it.
                            if (event is WatchTogetherServerEvent.Bye &&
                                event.reason !in IMMEDIATE_RECONNECT_BYE_REASONS &&
                                event.reason.toMembershipOrNull() == null
                            ) {
                                throttledBye = true
                            }
                            if (!processServerEvent(session, event)) break
                        }
                    } finally {
                        collector.cancel()
                    }
                }
            } catch (throwable: Throwable) {
                currentCoroutineContext().ensureActive()
                logger.debug { "Watch-together SSE disconnected: ${throwable.message}" }
            }

            if (!isCurrent(session)) return
            if (silenceTimeout) {
                degraded = true
            } else if (!receivedAny) {
                consecutiveFailures++
                if (consecutiveFailures >= SSE_FAILURES_BEFORE_DEGRADED) degraded = true
            } else {
                consecutiveFailures = 0
            }

            if (degraded) {
                session.updateConnection(WatchTogetherConnectionState.DegradedPolling)
            } else {
                session.updateConnection(WatchTogetherConnectionState.Reconnecting)
                val backoff = when {
                    throttledBye -> SSE_THROTTLED_BYE_BACKOFF_MILLIS
                    receivedAny -> 0L
                    else -> min(
                        SSE_INITIAL_BACKOFF_MILLIS shl (consecutiveFailures - 1).coerceAtLeast(0),
                        SSE_MAX_BACKOFF_MILLIS,
                    )
                }
                delay(backoff + reconnectJitterMillis())
            }
        }
    }

    private fun processServerEvent(session: RoomSession, event: WatchTogetherServerEvent): Boolean {
        return when (event) {
            WatchTogetherServerEvent.Connected, WatchTogetherServerEvent.Ping -> true
            is WatchTogetherServerEvent.Snapshot -> {
                !processSnapshot(session, event.snapshot)
            }

            is WatchTogetherServerEvent.Bye -> {
                event.reason.toMembershipOrNull()?.let { membership ->
                    scheduleMembershipLoss(session, membership)
                }
                false
            }
        }
    }

    private suspend fun runPeriodicReports(session: RoomSession) {
        while (currentCoroutineContext().isActive && isCurrent(session)) {
            delay(REPORT_CHECK_INTERVAL_MILLIS)
            val now = localNowMillis()
            val watching = playbackBridge.localWatching.value
            if (!appForeground.value && !(session.isHost && watching != null)) continue
            val degraded = session.connection.value == WatchTogetherConnectionState.DegradedPolling
            val interval = when {
                degraded -> DEGRADED_REPORT_INTERVAL_MILLIS
                watching == null -> continue
                session.isHost -> HOST_REPORT_INTERVAL_MILLIS
                else -> MEMBER_REPORT_INTERVAL_MILLIS
            }
            // While paused the fix is frozen, so re-sending it is pure waste (spec 3.3: no
            // periodic fixes while paused). Only skip while SSE is connected — otherwise these
            // reports double as the presence signal (background host, reconnecting, degraded).
            if (!degraded && watching != null && watching.paused &&
                session.connection.value == WatchTogetherConnectionState.ConnectedSse &&
                watching == session.lastReportedWatching.value
            ) {
                continue
            }
            if (now - session.lastReportAtMillis.value >= interval) reportOnce(session)
        }
    }

    private suspend fun reportOnce(session: RoomSession) {
        if (!isCurrent(session)) return
        reportMutex.withLock {
            if (!isCurrent(session)) return@withLock
            val sentAt = localNowMillis()
            session.lastReportAtMillis.value = sentAt
            val watching = playbackBridge.localWatching.value
            try {
                val response = api.report(
                    session.roomId,
                    AniReportWatchTogetherStateRequest(
                        sessionNonce = session.sessionNonce,
                        memberState = if (watching == null) {
                            AniWatchTogetherMemberState.IDLE
                        } else {
                            AniWatchTogetherMemberState.WATCHING
                        },
                        following = session.following.value,
                        watching = watching,
                        knownVersion = session.knownVersion,
                    ),
                )
                val receivedAt = localNowMillis()
                session.lastReportedWatching.value = watching
                session.serverClock.recordSample(response.serverTime, sentAt, receivedAt)
                response.version?.let { session.noteServerVersion(it) }
                response.snapshot?.let { processSnapshot(session, it) }
                if (response.membership != AniWatchTogetherMembership.OK) {
                    scheduleMembershipLoss(session, response.membership)
                }
            } catch (throwable: Throwable) {
                currentCoroutineContext().ensureActive()
                logger.debug { "Failed to report watch-together state: ${throwable.message}" }
            }
        }
    }

    private fun processSnapshot(session: RoomSession, snapshot: AniWatchTogetherRoomSnapshot): Boolean {
        if (!isCurrent(session) || snapshot.roomId != session.roomId) return false
        val accepted = session.updateSnapshot(snapshot)
        if (accepted && snapshot.status == AniWatchTogetherRoomStatus.CLOSED) {
            scheduleMembershipLoss(session, AniWatchTogetherMembership.ROOM_CLOSED)
            return true
        }
        return false
    }

    private fun String.toMembershipOrNull(): AniWatchTogetherMembership? = when (this) {
        AniWatchTogetherMembership.KICKED_TIMEOUT.value -> AniWatchTogetherMembership.KICKED_TIMEOUT
        AniWatchTogetherMembership.NOT_MEMBER.value -> AniWatchTogetherMembership.NOT_MEMBER
        AniWatchTogetherMembership.REPLACED.value -> AniWatchTogetherMembership.REPLACED
        AniWatchTogetherMembership.ROOM_CLOSED.value -> AniWatchTogetherMembership.ROOM_CLOSED
        else -> null
    }

    private fun scheduleMembershipLoss(session: RoomSession, membership: AniWatchTogetherMembership) {
        if (membership == AniWatchTogetherMembership.OK || !isAttached(session) || !session.beginClosing()) return
        scope.launch { handleMembershipLoss(session, membership) }
    }

    private suspend fun handleMembershipLoss(session: RoomSession, membership: AniWatchTogetherMembership) {
        var rejoin = false
        operationMutex.withLock {
            if (!isAttached(session)) return
            stopSessionResources()
            _state.value = if (available.value) WatchTogetherState.Idle else WatchTogetherState.Disabled
            when (membership) {
                AniWatchTogetherMembership.OK -> return
                AniWatchTogetherMembership.KICKED_TIMEOUT,
                AniWatchTogetherMembership.NOT_MEMBER,
                    -> rejoin = available.value

                AniWatchTogetherMembership.REPLACED -> {
                    clearRememberedSession()
                    effectChannel.send(WatchTogetherEffect.RoomEnded(WatchTogetherRoomEndReason.SESSION_REPLACED))
                }

                AniWatchTogetherMembership.ROOM_CLOSED -> {
                    clearRememberedSession()
                    effectChannel.send(WatchTogetherEffect.RoomEnded(WatchTogetherRoomEndReason.ROOM_CLOSED))
                }
            }
        }
        if (rejoin) scheduleAutoJoin()
    }

    private suspend fun observeFollowerMode(session: RoomSession) {
        session.following.collect { following ->
            val active = following && !session.isHost && isCurrent(session)
            automationGate.setSuppressed(active)
            playbackBridge.setTargetPlayback(if (active) session.snapshot.value.playback else null)
            navigationGuard?.dispose()
            navigationGuard = if (active) {
                EpisodeNavigationGuardRegistry.register { subjectId, episodeId ->
                    val host = session.snapshot.value.playback?.info
                    when {
                        // A registration that lost the teardown race must never keep blocking.
                        !isCurrent(session) -> null
                        host != null && host.subjectId == subjectId && host.episodeId == episodeId -> null
                        else -> NAVIGATION_DENIED_MESSAGE
                    }
                }
            } else {
                null
            }
        }
    }

    private suspend fun observeGuidance(session: RoomSession) {
        combine(
            session.following,
            session.snapshot.map { snapshot ->
                snapshot.playback?.info?.let { it.subjectId to it.episodeId }
            }.distinctUntilChanged(),
        ) { following, _ -> following }
            .collect { following ->
                val active = following && !session.isHost && isCurrent(session)
                val playback = session.snapshot.value.playback
                playbackBridge.setTargetPlayback(if (active) playback else null)
                if (!active) return@collect
                when (val action = SyncGuidanceEngine.compute(
                    playback = playback,
                    local = playbackBridge.localPosition(),
                    nowMillis = session.serverClock.now(),
                )) {
                    null -> Unit
                    is SyncAction.PushEpisode,
                    is SyncAction.PopThenPushEpisode,
                        -> effectChannel.send(
                        WatchTogetherEffect.Navigate(
                            action,
                            subjectName = playback?.info?.subjectName,
                            episodeSort = playback?.info?.episodeSort,
                        ),
                    )

                    is SyncAction.SwitchEpisodeInPlace -> {
                        if (playback != null) playbackBridge.emitDirective(
                            PlaybackDirective.SwitchEpisode(action.episodeId, playback),
                        )
                    }

                    is SyncAction.SeekOnly -> {
                        if (playback != null) playbackBridge.emitDirective(
                            PlaybackDirective.Sync(playback, initial = true),
                        )
                    }
                }
            }
    }

    /**
     * Pulls a follower back when the episode they are actually playing deviates from the host's —
     * in-player episode switches do not pass [EpisodeNavigationGuardRegistry], and the guard is
     * advisory anyway. Leaving the player (localWatching becoming null) is deliberately not a
     * trigger: the decision table only guides on follow-start and host changes.
     */
    private suspend fun observeLocalDeviation(session: RoomSession) {
        playbackBridge.localWatching
            .map { watching -> watching?.let { it.subjectId to it.episodeId } }
            .distinctUntilChanged()
            .collect { localKey ->
                if (localKey == null || session.isHost || !session.following.value || !isCurrent(session)) {
                    return@collect
                }
                val playback = session.snapshot.value.playback ?: return@collect
                val host = playback.info
                if (host.subjectId == localKey.first && host.episodeId == localKey.second) return@collect
                when (
                    val action = SyncGuidanceEngine.compute(
                        playback = playback,
                        local = LocalPosition.InPlayer(localKey.first, localKey.second),
                        nowMillis = session.serverClock.now(),
                    )
                ) {
                    is SyncAction.SwitchEpisodeInPlace -> playbackBridge.emitDirective(
                        PlaybackDirective.SwitchEpisode(action.episodeId, playback),
                    )

                    is SyncAction.PopThenPushEpisode -> effectChannel.send(
                        WatchTogetherEffect.Navigate(
                            action,
                            subjectName = host.subjectName,
                            episodeSort = host.episodeSort,
                        ),
                    )

                    else -> Unit
                }
            }
    }

    private suspend fun runContinuousCorrection(session: RoomSession) {
        while (currentCoroutineContext().isActive && isCurrent(session)) {
            delay(CORRECTION_INTERVAL_MILLIS)
            if (session.isHost || !session.following.value) continue
            val playback = session.snapshot.value.playback ?: continue
            playbackBridge.setTargetPlayback(playback)
            val host = playback.info
            val local = playbackBridge.localWatching.value ?: continue
            if (host.subjectId != local.subjectId || host.episodeId != local.episodeId) continue
            val now = session.serverClock.now()
            val drift = abs(host.positionAt(now) - local.positionAt(now))
            val rateDiffers = abs((host.playbackRate ?: 1f) - (local.playbackRate ?: 1f)) > 0.01f
            if (drift > CORRECTION_THRESHOLD_MILLIS || host.paused != local.paused || rateDiffers) {
                playbackBridge.emitDirective(PlaybackDirective.Sync(playback))
            }
        }
    }

    private suspend fun observeCorrectionToasts(session: RoomSession) {
        var lastToastAtMillis = 0L
        playbackBridge.corrections.collect { deltaMillis ->
            if (session.isHost || !session.following.value || !isCurrent(session)) return@collect
            val now = localNowMillis()
            if (now - lastToastAtMillis < CORRECTION_TOAST_MIN_INTERVAL_MILLIS) return@collect
            lastToastAtMillis = now
            effectChannel.send(WatchTogetherEffect.ResyncedWithHost(deltaMillis))
        }
    }

    private suspend fun clearRememberedSession() {
        settings.update { copy(rememberedSession = null) }
    }

    private fun isCurrent(session: RoomSession): Boolean =
        isAttached(session) && !session.isClosing

    private fun isAttached(session: RoomSession): Boolean =
        (_state.value as? WatchTogetherState.InRoom)?.session === session

    private data class WatchTogetherJoinOutcome(val created: Boolean)

    private companion object {
        const val NAVIGATION_DENIED_MESSAGE = "WATCH_TOGETHER_FOLLOWING"
        const val SSE_FAILURES_BEFORE_DEGRADED = 3
        const val SSE_SILENCE_TIMEOUT_MILLIS = 60_000L
        const val SSE_DEGRADED_RETRY_MILLIS = 60_000L
        const val SSE_INITIAL_BACKOFF_MILLIS = 2_000L
        const val SSE_MAX_BACKOFF_MILLIS = 30_000L
        const val SSE_THROTTLED_BYE_BACKOFF_MILLIS = 30_000L
        val IMMEDIATE_RECONNECT_BYE_REASONS = setOf("LIFETIME", "SHUTDOWN")
        const val REPORT_CHECK_INTERVAL_MILLIS = 1_000L
        const val HOST_REPORT_INTERVAL_MILLIS = 5_000L
        const val MEMBER_REPORT_INTERVAL_MILLIS = 10_000L
        const val DEGRADED_REPORT_INTERVAL_MILLIS = 10_000L
        const val AUTO_JOIN_INITIAL_RETRY_MILLIS = 2_000L
        const val AUTO_JOIN_MAX_RETRY_MILLIS = 30_000L
        const val CORRECTION_INTERVAL_MILLIS = 1_000L
        const val CORRECTION_THRESHOLD_MILLIS = 3_000L
        const val CORRECTION_TOAST_MIN_INTERVAL_MILLIS = 5_000L

        val logger = logger<WatchTogetherManager>()
    }
}
