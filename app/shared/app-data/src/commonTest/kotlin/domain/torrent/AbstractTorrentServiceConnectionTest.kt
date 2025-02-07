/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import me.him188.ani.utils.coroutines.childScope
import kotlin.coroutines.CoroutineContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

abstract class AbstractTorrentServiceConnectionTest {
    @BeforeTest
    fun installDispatcher() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun resetDispatcher() {
        Dispatchers.resetMain()
    }
}

class TestTorrentServiceConnection(
    private val shouldStartServiceSucceed: Boolean = true,
    coroutineContext: CoroutineContext = Dispatchers.Default,
) : LifecycleAwareTorrentServiceConnection<String>(coroutineContext) {
    private val scope = coroutineContext.childScope()
    private val fakeBinder = "FAKE_BINDER_OBJECT"

    override suspend fun startService(): TorrentServiceConnection.StartResult {
        delay(100)
        return if (shouldStartServiceSucceed) {
            scope.launch { // simulate automatic connection after start service succeeded.
                delay(500)
                onServiceConnected(fakeBinder)
            }
            TorrentServiceConnection.StartResult.STARTED
        } else {
            TorrentServiceConnection.StartResult.FAILED
        }
    }

    fun triggerServiceDisconnected() {
        onServiceDisconnected()
    }
}