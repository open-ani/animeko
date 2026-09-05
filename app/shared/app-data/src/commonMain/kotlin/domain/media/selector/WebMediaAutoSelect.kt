/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.datasources.api.Media
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import kotlin.time.Duration

/** One event loop owns the phase transitions and is the only automatic selection writer. */
internal suspend fun MediaSelector.runWebAutoSelect(
    session: MediaFetchSession,
    config: WebAutoSelectConfig,
    expectedSelection: Media? = null,
): Media? = coroutineScope {
    val stage = MutableStateFlow(WebAutoSelectStage.PreferredSource)
    var result: Media? = null
    var deadlines: Job? = null
    try {
        combine(autoSelectSnapshots(session.selectionSnapshots()), stage, selected) { snapshot, phase, currentSelection ->
            if (currentSelection != expectedSelection) WebAutoSelectDecision.Exhausted
            else decideWebAutoSelect(snapshot, config, phase)
        }.first { decision ->
            when (decision) {
                WebAutoSelectDecision.StartFallback -> {
                    if (stage.value == WebAutoSelectStage.PreferredSource) {
                        stage.value = if (config.fastSelect) WebAutoSelectStage.Instant else WebAutoSelectStage.Exact
                        deadlines = launch {
                            val exactAfter = if (config.fastSelect) config.exactMatchAfter else Duration.ZERO
                            delay(exactAfter)
                            stage.value = WebAutoSelectStage.Exact
                            // Both deadlines are relative to release of the remembered-source gate.
                            delay((config.fuzzyMatchAfter - exactAfter).coerceAtLeast(Duration.ZERO))
                            stage.value = WebAutoSelectStage.Fuzzy
                        }
                    }
                    false
                }
                WebAutoSelectDecision.Wait -> false
                WebAutoSelectDecision.Exhausted -> true
                is WebAutoSelectDecision.Select -> {
                    logger<WebAutoSelectConfig>().info {
                        "Web auto select: reason=${decision.reason}, source=${decision.media.mediaSourceId}, media=${decision.media.mediaId}"
                    }
                    result = selectAutomatically(decision.media, expectedSelection)
                    true
                }
            }
        }
    } finally {
        deadlines?.cancel()
    }
    result
}
