/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.foundation.focus

import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Navigation cancels preparation as well as an already submitted focus request. */
suspend fun TvFocusScope.requestPrepared(
    isRelevant: () -> Boolean = { true },
    prepare: suspend () -> TvFocusKey?,
) = coroutineScope {
    val generation = userNavGeneration
    val preparation = launch(start = CoroutineStart.UNDISPATCHED) {
        val target = prepare()
        if (target != null && generation == userNavGeneration && isRelevant()) request(target)
    }
    val cancellation = launch {
        snapshotFlow { userNavGeneration != generation || !isRelevant() }.first { it }
        preparation.cancel()
    }
    try {
        preparation.join()
    } finally {
        cancellation.cancel()
    }
}
