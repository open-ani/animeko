/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.videoplayer.media

import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

class LibassExoPlayerMediampPlayerTest {
    private val mainExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, PLAYER_MAIN_THREAD_NAME)
    }
    private val mainDispatcher = mainExecutor.asCoroutineDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        mainDispatcher.close()
        mainExecutor.shutdownNow()
    }

    @Test
    fun `media replacement stops playback on main thread`() = runBlocking(Dispatchers.Default) {
        var stopThreadName: String? = null

        stopPlaybackOnMain {
            stopThreadName = Thread.currentThread().name
        }

        assertTrue(stopThreadName?.startsWith(PLAYER_MAIN_THREAD_NAME) == true)
    }

    private companion object {
        const val PLAYER_MAIN_THREAD_NAME = "MediampPlayer-main"
    }
}
