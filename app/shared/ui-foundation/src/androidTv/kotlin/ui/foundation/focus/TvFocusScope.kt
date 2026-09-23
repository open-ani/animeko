/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.foundation.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 页面内的具名焦点位置。 */
interface TvFocusKey
fun TvFocusKey(name: String): TvFocusKey = NamedFocusKey(name)
private data class NamedFocusKey(val name: String) : TvFocusKey {
    override fun toString(): String = name
}

/** Delivery can precede a scroll animation; both remain part of the same cancellable request. */
class TvFocusPreparationScope internal constructor(private val deliver: (TvFocusKey) -> Unit) {
    fun focusNow(key: TvFocusKey) = deliver(key)
}

/**
 * Owns one focus request, including preparation and optional memory restoration.
 * Navigation, replacement, and user input cancel the whole request. All methods run on the UI thread.
 */
@Stable
class TvFocusScope internal constructor(private val boundary: TvFocusBoundaryState?) {
    constructor() : this(null)

    val isActive: Boolean get() = boundary?.isActive != false
    private val targets = mutableMapOf<TvFocusKey, TvFocusTarget>()
    private val focusedKeys = mutableSetOf<TvFocusKey>()
    private val initialBoundaryNavigation = boundary?.userNavGeneration ?: 0
    private var localUserNavigation by mutableIntStateOf(0)
    val userNavGeneration: Int get() = localUserNavigation + (boundary?.userNavGeneration ?: 0) - initialBoundaryNavigation

    internal class Request(
        val target: () -> TvFocusTarget?,
        val generation: Int,
        val relevant: () -> Boolean = { true },
        val fallback: TvFocusTarget? = null,
    ) {
        var usedFallback by mutableStateOf(false)
        var delivered by mutableStateOf(false)
        var preparing = false
    }

    internal var pending: Request? by mutableStateOf(null)
        private set

    internal fun targetOf(key: TvFocusKey): TvFocusTarget = targets.getOrPut(key) { TvFocusTarget(boundary) }
    fun requesterOf(key: TvFocusKey): FocusRequester = targetOf(key).requester
    fun isAnchorAttached(key: TvFocusKey): Boolean = targetOf(key).attached
    fun isFocused(key: TvFocusKey): Boolean = key in focusedKeys
    fun onAnchorFocusChanged(key: TvFocusKey, focused: Boolean) {
        if (focused) focusedKeys.add(key) else focusedKeys.remove(key)
    }

    private fun submit(request: Request) {
        if (isActive && request.relevant()) pending = request
    }
    private fun valid(request: Request): Boolean =
        isActive && request.generation == userNavGeneration && request.relevant()

    /** Resolves once the target is placed and the window can accept focus. */
    fun request(key: TvFocusKey) = submit(Request({ targetOf(key) }, userNavGeneration))

    /** The fallback is offered once while the saved identity waits for its live node. */
    fun restore(memory: TvFocusMemory, fallback: TvFocusKey) {
        val target = memory.restoration()
        if (target == null) request(fallback)
        else submit(Request(target, userNavGeneration, fallback = targetOf(fallback)))
    }

    fun notifyUserNavigation() {
        localUserNavigation++
        pending = null
    }

    /** Preparation and delivery share the same request identity and cancellation rules. */
    suspend fun requestPrepared(
        isRelevant: () -> Boolean = { true },
        prepare: suspend TvFocusPreparationScope.() -> TvFocusKey?,
    ) = coroutineScope {
        if (!isActive || !isRelevant()) return@coroutineScope
        var destination by mutableStateOf<TvFocusKey?>(null)
        val request = Request({ destination?.let(::targetOf) }, userNavGeneration, isRelevant)
        request.preparing = true
        submit(request)
        val context = TvFocusPreparationScope { key ->
            if (pending === request && valid(request) && destination != key) {
                request.delivered = false
                destination = key
            }
        }
        val preparation = launch(start = CoroutineStart.UNDISPATCHED) {
            val key = prepare(context)
            if (pending === request && valid(request)) {
                if (key != null) context.focusNow(key)
                request.preparing = false
                if (destination == null || request.delivered) pending = null
            }
        }
        val cancellation = launch {
            snapshotFlow { pending !== request || !valid(request) }.first { it }
            preparation.cancel()
        }
        try {
            preparation.join()
        } finally {
            cancellation.cancel()
            if ((preparation.isCancelled || !valid(request)) && pending === request) pending = null
        }
    }

    private data class Resolution(
        val request: Request,
        val target: TvFocusTarget? = null,
        val fallback: Boolean = false,
        val cancel: Boolean = false,
        val attachmentGeneration: Int = target?.attachmentGeneration ?: 0,
    )

    /** A single dispatcher for initial focus, explicit requests, and remembered destinations. */
    @Composable
    fun Resolver() {
        val window = LocalWindowInfo.current
        LaunchedEffect(this, window) {
            snapshotFlow {
                pending?.let { request ->
                    when {
                        !valid(request) -> Resolution(request, cancel = true)
                        request.delivered -> Resolution(request)
                        !window.isWindowFocused -> null
                        else -> {
                            val target = request.target()?.takeIf { it.ready }
                            Resolution(
                                request,
                                target ?: request.fallback?.takeIf { !request.usedFallback && it.ready },
                                fallback = target == null,
                            )
                        }
                    }
                }
            }.filterNotNull().collect { result ->
                if (pending !== result.request) return@collect
                if (result.cancel) {
                    pending = null
                } else {
                    val target = result.target ?: return@collect
                    if (runCatching { target.requester.requestFocus() }.getOrDefault(false)) {
                        if (result.fallback) result.request.usedFallback = true
                        else if (pending === result.request) {
                            result.request.delivered = true
                            if (!result.request.preparing) pending = null
                        }
                    }
                }
            }
        }
    }

    /** Each entry activation chooses the saved identity, or [key] when no saved node is ready. */
    @Composable
    fun InitialFocus(key: TvFocusKey) {
        val memory = LocalTvFocusMemory.current
        val currentKey by rememberUpdatedState(key)
        LaunchedEffect(this, isActive) {
            if (memory == null) request(currentKey) else restore(memory, currentKey)
        }
    }

    /** Data and scroll preparation for an entry activation, or a change of [inputs]. */
    @Composable
    fun InitialFocus(
        vararg inputs: Any?,
        isRelevant: () -> Boolean = { true },
        prepare: suspend TvFocusPreparationScope.() -> TvFocusKey?,
    ) {
        val currentPrepare by rememberUpdatedState(prepare)
        val currentRelevant by rememberUpdatedState(isRelevant)
        LaunchedEffect(this, isActive, *inputs) {
            requestPrepared(isRelevant = { currentRelevant() }) { currentPrepare() }
        }
    }
}

@Composable
fun rememberTvFocusScope(): TvFocusScope {
    val boundary = LocalTvFocusBoundary.current
    return remember(boundary) { TvFocusScope(boundary) }
}
