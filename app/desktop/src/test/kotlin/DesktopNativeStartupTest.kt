/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.desktop

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.yield
import me.him188.ani.utils.platform.Arch
import me.him188.ani.utils.platform.Platform
import org.junit.jupiter.api.Timeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@Timeout(10)
class DesktopNativeStartupTest {
    @Test
    fun `player is prepared after JCEF when requested`() = runBlocking<Unit> {
        val calls = mutableListOf<String>()

        initializeJcefAndPlayerBackend(
            preparePlayerBeforeJcef = false,
            preparePlayer = { calls += "player" },
            initializeJcef = { calls += "JCEF" },
        )

        assertEquals(listOf("JCEF", "player"), calls)
    }

    @Test
    fun `player is prepared before JCEF when requested`() = runBlocking<Unit> {
        val calls = mutableListOf<String>()

        initializeJcefAndPlayerBackend(
            preparePlayerBeforeJcef = true,
            preparePlayer = { calls += "player" },
            initializeJcef = { calls += "JCEF" },
        )

        assertEquals(listOf("player", "JCEF"), calls)
    }

    @Test
    fun `Intel macOS prepares player after JCEF`() {
        assertFalse(shouldPreparePlayerBeforeJcef(Platform.MacOS(Arch.X86_64)))
    }

    @Test
    fun `other desktop platforms prepare player before JCEF`() {
        assertTrue(shouldPreparePlayerBeforeJcef(Platform.MacOS(Arch.AARCH64)))
        assertTrue(shouldPreparePlayerBeforeJcef(Platform.Windows(Arch.X86_64)))
        assertTrue(shouldPreparePlayerBeforeJcef(Platform.Windows(Arch.AARCH64)))
        assertTrue(shouldPreparePlayerBeforeJcef(Platform.Linux(Arch.X86_64)))
    }

    @Test
    fun `playback entry waits for prerequisites then JCEF then player preparation`() = runBlocking<Unit> {
        val prerequisites = CompletableDeferred<Unit>()
        val jcefInitialized = CompletableDeferred<Unit>()
        val playerPrepared = CompletableDeferred<Unit>()
        val calls = mutableListOf<String>()
        val ready = launchDesktopNativeStartup(
            preparePlayerBeforeJcef = false,
            beforeInitialization = { prerequisites.await() },
            preparePlayer = {
                calls += "prepare player"
                playerPrepared.await()
            },
            initializeJcef = {
                calls += "initialize JCEF"
                jcefInitialized.await()
            },
        )
        val playback = async(start = CoroutineStart.UNDISPATCHED) {
            ready.await()
            calls += "create player"
        }

        yield()
        assertEquals(emptyList(), calls)
        assertFalse(playback.isCompleted)

        prerequisites.complete(Unit)
        yield()
        assertEquals(listOf("initialize JCEF"), calls)
        assertFalse(playback.isCompleted)

        jcefInitialized.complete(Unit)
        yield()
        assertEquals(listOf("initialize JCEF", "prepare player"), calls)
        assertFalse(playback.isCompleted)

        playerPrepared.complete(Unit)
        playback.await()
        assertEquals(listOf("initialize JCEF", "prepare player", "create player"), calls)
    }

    @Test
    fun `player first platforms can open playback while JCEF is initializing`() = runBlocking<Unit> {
        val jcefInitialized = CompletableDeferred<Unit>()
        val ready = launchDesktopNativeStartup(
            preparePlayerBeforeJcef = true,
            beforeInitialization = {},
            preparePlayer = {},
            initializeJcef = { jcefInitialized.await() },
        )

        ready.await()
        assertFalse(jcefInitialized.isCompleted)
        jcefInitialized.complete(Unit)
    }

    @Test
    fun `JCEF failure releases waiters without preparing player on Intel macOS`() = runBlocking<Unit> {
        supervisorScope {
            val failure = IllegalStateException("JCEF failed")
            val reportedFailure = CompletableDeferred<Throwable>()
            val scope = CoroutineScope(coroutineContext + CoroutineExceptionHandler { _, cause ->
                reportedFailure.complete(cause)
            })
            var playerPrepared = false
            val ready = scope.launchDesktopNativeStartup(
                preparePlayerBeforeJcef = false,
                beforeInitialization = {},
                preparePlayer = { playerPrepared = true },
                initializeJcef = { throw failure },
            )

            assertEquals(failure.message, assertFailsWith<IllegalStateException> { ready.await() }.message)
            assertSame(failure, reportedFailure.await())
            assertFalse(playerPrepared)
        }
    }

    @Test
    fun `player preparation failure never reports readiness`() = runBlocking<Unit> {
        for (playerFirst in listOf(false, true)) {
            supervisorScope {
                val failure = UnsatisfiedLinkError("mpv failed")
                val reportedFailure = CompletableDeferred<Throwable>()
                val scope = CoroutineScope(coroutineContext + CoroutineExceptionHandler { _, cause ->
                    reportedFailure.complete(cause)
                })
                val ready = scope.launchDesktopNativeStartup(
                    preparePlayerBeforeJcef = playerFirst,
                    beforeInitialization = {},
                    preparePlayer = { throw failure },
                    initializeJcef = {},
                )

                assertEquals(failure.message, assertFailsWith<UnsatisfiedLinkError> { ready.await() }.message)
                assertSame(failure, reportedFailure.await())
            }
        }
    }

    @Test
    fun `prerequisite failure also releases readiness waiters`() = runBlocking<Unit> {
        supervisorScope {
            val failure = IllegalStateException("prerequisite failed")
            val reportedFailure = CompletableDeferred<Throwable>()
            val scope = CoroutineScope(coroutineContext + CoroutineExceptionHandler { _, cause ->
                reportedFailure.complete(cause)
            })
            var nativeInitializationStarted = false
            val ready = scope.launchDesktopNativeStartup(
                preparePlayerBeforeJcef = false,
                beforeInitialization = { throw failure },
                preparePlayer = { nativeInitializationStarted = true },
                initializeJcef = { nativeInitializationStarted = true },
            )

            assertEquals(failure.message, assertFailsWith<IllegalStateException> { ready.await() }.message)
            assertSame(failure, reportedFailure.await())
            assertFalse(nativeInitializationStarted)
        }
    }

    @Test
    fun `cancellation before startup runs releases readiness waiters`() = runBlocking<Unit> {
        val scope = CoroutineScope(coroutineContext + Job())
        scope.cancel()
        val ready = scope.launchDesktopNativeStartup(
            preparePlayerBeforeJcef = false,
            beforeInitialization = { error("Must not initialize after cancellation") },
            preparePlayer = { error("Must not prepare player after cancellation") },
            initializeJcef = { error("Must not initialize JCEF after cancellation") },
        )

        assertFailsWith<CancellationException> { ready.await() }
    }

    @Test
    fun `cancellation while waiting for JCEF releases readiness waiters`() = runBlocking<Unit> {
        val scope = CoroutineScope(coroutineContext + Job())
        val jcefStarted = CompletableDeferred<Unit>()
        var playerPrepared = false
        val ready = scope.launchDesktopNativeStartup(
            preparePlayerBeforeJcef = false,
            beforeInitialization = {},
            preparePlayer = { playerPrepared = true },
            initializeJcef = {
                jcefStarted.complete(Unit)
                CompletableDeferred<Unit>().await()
            },
        )

        jcefStarted.await()
        scope.cancel()
        assertFailsWith<CancellationException> { ready.await() }
        assertFalse(playerPrepared)
    }
}
