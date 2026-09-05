/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector.engine

import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.datasources.api.Media
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger

/**
 * WEB 自动选择的执行循环: 唯一的非纯部分.
 *
 * 观察 (一致快照, 当前阶段, 当前选择), 每次变化调用一次纯函数 [decideWebAutoSelect] 并执行它的决定.
 * 时间只在这里出现: 记忆源放行后启动两个计时器推进阶段. 选择通过 [MediaSelector.selectAutomatically] 以 CAS 提交,
 * 基准是 [WebAutoSelectConfig.currentSelection]; 等待期间选择被别人改掉就放弃.
 *
 * @return 选中的 [Media]; 选不出、或选择被外部改变、或提交时 CAS 失败时返回 `null`.
 */
internal suspend fun MediaSelector.runWebAutoSelect(
    session: MediaFetchSession,
    config: WebAutoSelectConfig,
): Media? = coroutineScope {
    val expected = config.currentSelection
    if (selected.value != expected) return@coroutineScope null

    val stage = MutableStateFlow(
        if (config.preferredWebSourceId != null) WebAutoSelectStage.PREFERRED_SOURCE else WebAutoSelectStage.INSTANT,
    )
    var timers: Job? = null
    fun startTimers() {
        timers = launch {
            delay(config.lowTierToleranceDuration)
            stage.value = WebAutoSelectStage.EXACT_ONLY
            delay(maxOf(config.fuzzyFallbackDuration, config.lowTierToleranceDuration) - config.lowTierToleranceDuration)
            stage.value = WebAutoSelectStage.FUZZY
        }
    }
    if (stage.value != WebAutoSelectStage.PREFERRED_SOURCE) startTimers()

    var result: Media? = null
    try {
        combine(autoSelectSnapshots(session.sourceSnapshots()), stage, selected) { snapshot, currentStage, currentSelection ->
            if (currentSelection != expected) null else decideWebAutoSelect(snapshot, config, currentStage)
        }.first { decision ->
            when (decision) {
                null -> {
                    logger.debug { "webAutoSelect: selection changed externally, give up" }
                    true
                }

                WebAutoSelectDecision.Wait -> false

                WebAutoSelectDecision.ReleasePreferredSourceGate -> {
                    if (stage.compareAndSet(WebAutoSelectStage.PREFERRED_SOURCE, WebAutoSelectStage.INSTANT)) {
                        logger.debug { "webAutoSelect: preferred web source gate released" }
                        startTimers()
                    }
                    false
                }

                WebAutoSelectDecision.Finish -> {
                    logger.debug { "webAutoSelect: nothing selectable, finish" }
                    true
                }

                is WebAutoSelectDecision.Select -> {
                    result = selectAutomatically(decision.media, expected)
                    logger.info { "webAutoSelect: ${decision.reason} -> ${decision.media.mediaId} (committed=${result != null})" }
                    true
                }
            }
        }
    } finally {
        timers?.cancel()
    }
    result
}

private val logger = logger("WebAutoSelect")
