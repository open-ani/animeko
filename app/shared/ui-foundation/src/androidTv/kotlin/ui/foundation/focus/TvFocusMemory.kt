/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.foundation.focus

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged

/** Saves the last destination and indexes live nodes by identity. Requests belong to [TvFocusScope]. */
@Stable
class TvFocusMemory {
    var lastId: Any? by mutableStateOf(null)
        private set
    private var lastTarget: TvFocusTarget? = null
    private val targets = mutableStateMapOf<Any, TvFocusTarget>()

    internal fun register(id: Any, target: TvFocusTarget) { targets[id] = target }
    internal fun unregister(id: Any, target: TvFocusTarget) {
        if (targets[id] === target) targets.remove(id)
    }
    internal fun reportFocused(id: Any?, target: TvFocusTarget) {
        lastId = id
        lastTarget = target
    }

    /** Saves a registered destination when its action is activated, such as a navigation launcher. */
    fun remember(id: Any) {
        targets[id]?.let { reportFocused(id, it) }
    }

    /** Capture the identity before a temporary fallback receives focus. */
    internal fun restoration(): (() -> TvFocusTarget?)? {
        val id = lastId
        val target = lastTarget
        if (id == null && target?.attached != true) return null
        return { if (id == null) target else targets[id] }
    }

    fun clear() {
        lastId = null
        lastTarget = null
    }
}

val LocalTvFocusMemory = staticCompositionLocalOf<TvFocusMemory?> { null }

/**
 * A stable [memoryId] survives node recreation; an absent ID supports same-node restoration only.
 * With [rememberOnFocus] disabled, the caller records activation through [TvFocusMemory.remember].
 */
fun Modifier.tvFocusMemorable(
    memoryId: Any? = null,
    memory: TvFocusMemory? = null,
    rememberOnFocus: Boolean = true,
): Modifier = composed {
    val resolved = memory ?: LocalTvFocusMemory.current ?: return@composed this
    val boundary = LocalTvFocusBoundary.current
    val target = remember(resolved, boundary, memoryId) { TvFocusTarget(boundary) }
    DisposableEffect(resolved, memoryId, target) {
        if (memoryId != null) resolved.register(memoryId, target)
        onDispose { if (memoryId != null) resolved.unregister(memoryId, target) }
    }
    tvFocusTarget(target).onFocusChanged {
        if (rememberOnFocus && it.isFocused) resolved.reportFocused(memoryId, target)
    }
}
