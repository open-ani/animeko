/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.watchtogether

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.transformLatest
import me.him188.ani.app.domain.watchtogether.HostAlignment
import me.him188.ani.app.domain.watchtogether.HostAlignmentUnavailableReason
import me.him188.ani.client.models.AniWatchTogetherWatchingInfo
import me.him188.ani.utils.platform.currentTimeMillis

/** Emits at cooldown expiry even when every playback input remains paused and unchanged. */
internal fun watchTogetherAlignmentAvailabilityFlow(
    host: Flow<AniWatchTogetherWatchingInfo?>,
    local: Flow<AniWatchTogetherWatchingInfo?>,
    aligning: Flow<Boolean>,
    cooldownUntilMillis: Flow<Long>,
    localNowMillis: () -> Long = ::currentTimeMillis,
): Flow<HostAlignmentUnavailableReason?> {
    val coolingDown = cooldownUntilMillis.transformLatest { until ->
        val remaining = (until - localNowMillis()).coerceAtLeast(0L)
        emit(remaining > 0L)
        if (remaining > 0L) {
            delay(remaining)
            emit(false)
        }
    }
    return combine(host, local, aligning, coolingDown) { hostInfo, localInfo, active, cooling ->
        HostAlignment.unavailableReason(hostInfo, localInfo, active, if (cooling) 1L else 0L)
    }
}
