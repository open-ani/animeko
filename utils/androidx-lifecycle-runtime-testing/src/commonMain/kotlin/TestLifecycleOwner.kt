/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

// COPIED FROM AOSP androidx-lifecycle v2.8.7
// Keep until Compose Multiplatform commonizes this module

// Source
// https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:lifecycle/lifecycle-runtime-testing/src/commonMain/kotlin/androidx/lifecycle/testing/TestLifecycleOwner.kt;bpv=0;bpt=1

// COPIED FROM AOSP
// https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:lifecycle/lifecycle-runtime-testing/src/commonMain/kotlin/androidx/lifecycle/testing/TestLifecycleOwner.kt;bpv=0;bpt=1

@file:Suppress("MemberVisibilityCanBePrivate")

package androidx.lifecycle.testing

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.jvm.JvmOverloads


/**
 * Create a [LifecycleOwner] that allows changing the state via the [handleLifecycleEvent] method or
 * [currentState] property.
 *
 * Under the hood, this uses a [LifecycleRegistry]. However, it uses
 * [Dispatchers.Main.immediate][kotlinx.coroutines.MainCoroutineDispatcher.immediate] as the default
 * [coroutineDispatcher] to ensure that all mutations to the [current state][currentState] are run
 * on that dispatcher, no matter what thread you mutate the state from.
 *
 * @param initialState The initial [Lifecycle.State].
 * @param coroutineDispatcher A [CoroutineDispatcher] to use when dispatching work from this class.
 */
public class TestLifecycleOwner
@JvmOverloads
constructor(
    initialState: Lifecycle.State = Lifecycle.State.STARTED,
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) : LifecycleOwner {
    // it is in test artifact
    @Suppress("VisibleForTests")
    private val lifecycleRegistry =
        LifecycleRegistry.createUnsafe(this).apply { currentState = initialState }

    override val lifecycle: LifecycleRegistry
        get() = lifecycleRegistry

    /**
     * Update the [currentState] by moving it to the state directly after the given [event]. This is
     * safe to mutate on any thread, but will block that thread during execution.
     */
    public fun handleLifecycleEvent(event: Lifecycle.Event) {
        runBlocking(coroutineDispatcher) { lifecycleRegistry.handleLifecycleEvent(event) }
    }

    /**
     * The current [Lifecycle.State] of this owner. This is safe to call on any thread but is
     * thread-blocking and should not be called from within a coroutine (use [setCurrentState]
     * instead).
     */
    public var currentState: Lifecycle.State
        get() = runBlocking(coroutineDispatcher) { lifecycleRegistry.currentState }
        set(value) {
            runBlocking(coroutineDispatcher) { lifecycleRegistry.currentState = value }
        }

    /**
     * Updates the [currentState]. This suspending function is safe to call on any thread and will
     * not block that thread. If the state should be updated from outside of a suspending function,
     * use [currentState] property syntax instead.
     */
    public suspend fun setCurrentState(state: Lifecycle.State) {
        withContext(coroutineDispatcher) { lifecycleRegistry.currentState = state }
    }

    /** Get the number of observers. */
    public val observerCount: Int
        get() = lifecycleRegistry.observerCount
}