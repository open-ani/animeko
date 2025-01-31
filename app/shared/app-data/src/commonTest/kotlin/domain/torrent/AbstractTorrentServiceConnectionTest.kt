/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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

    private val fakeBinder = "FAKE_BINDER_OBJECT"

    override suspend fun startService(): TorrentServiceConnection.StartResult {
        delay(100)
        return if (shouldStartServiceSucceed) {
            TorrentServiceConnection.StartResult.STARTED
        } else {
            TorrentServiceConnection.StartResult.FAILED
        }
    }

    fun triggerServiceConnected() {
        onServiceConnected(fakeBinder)
    }

    fun triggerServiceDisconnected() {
        onServiceDisconnected()
    }
}

private class TestLifecycle(private val owner: LifecycleOwner) : Lifecycle() {
    private val observers = MutableStateFlow(persistentListOf<DefaultLifecycleObserver>())
    private val _currentState = MutableStateFlow(State.INITIALIZED)

    override val currentState: State
        get() = _currentState.value

    override fun addObserver(observer: LifecycleObserver) {
        check(observer is DefaultLifecycleObserver) {
            "$observer must implement androidx.lifecycle.DefaultLifecycleObserver."
        }
        observers.update { it.add(observer) }
    }

    override fun removeObserver(observer: LifecycleObserver) {
        check(observer is DefaultLifecycleObserver) {
            "$observer must implement androidx.lifecycle.DefaultLifecycleObserver."
        }
        observers.update { it.remove(observer) }
    }

    fun moveToState(state: State) {
        observers.value.forEach {
            when (state) {
                State.INITIALIZED -> {}
                State.CREATED -> it.onCreate(owner)
                State.STARTED -> it.onStart(owner)
                State.RESUMED -> it.onResume(owner)
                State.DESTROYED -> it.onDestroy(owner)
            }
        }
    }
}

class TestLifecycleOwner : LifecycleOwner {
    private val lifecycleRegistry = TestLifecycle(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    fun moveTo(state: Lifecycle.State) {
        lifecycleRegistry.moveToState(state)
    }
}
