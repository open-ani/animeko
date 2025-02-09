/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import me.him188.ani.app.domain.torrent.service.ServiceStartException
import me.him188.ani.app.domain.torrent.service.TorrentServiceStarter
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

abstract class AbstractTorrentServiceConnectionTest {
    protected val fakeBinder = "FAKE_BINDER_OBJECT"

    protected fun createStarter(
        expectSuccess: Boolean
    ): Pair<TorrentServiceStarter<String>, CompletableDeferred<Unit>> {
        val deferred = CompletableDeferred<Unit>()
        return object : TorrentServiceStarter<String> {
            override suspend fun start(): String {
                deferred.await()
                if (expectSuccess) {
                    return fakeBinder
                } else {
                    throw ServiceStartException.NullBinder()
                }
            }
        } to deferred
    }
    
    @BeforeTest
    fun installDispatcher() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun resetDispatcher() {
        Dispatchers.resetMain()
    }
}
