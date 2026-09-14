/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.watchtogether

import androidx.compose.runtime.Composable
import me.him188.ani.app.data.network.WatchTogetherJoinFailure
import me.him188.ani.app.domain.watchtogether.WatchTogetherRoomEndReason
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_framework_timeout
import me.him188.ani.app.ui.lang.watch_together_rejoin_failed
import me.him188.ani.app.ui.lang.watch_together_room_closed
import me.him188.ani.app.ui.lang.watch_together_session_replaced
import me.him188.ani.app.ui.watchtogether.watchTogetherJoinFailureMessage
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TvTogetherError.text(): String = when (this) {
    TvTogetherError.EmptyName -> watchTogetherJoinFailureMessage(WatchTogetherJoinFailure.INVALID_NAME)
    TvTogetherError.Timeout -> watchTogetherJoinFailureMessage(stringResource(Lang.settings_framework_timeout))
    TvTogetherError.RejoinFailed -> stringResource(Lang.watch_together_rejoin_failed)
    is TvTogetherError.Join -> watchTogetherJoinFailureMessage(failure ?: WatchTogetherJoinFailure.TEMPORARY)
    is TvTogetherError.Ended -> stringResource(when (reason) {
        WatchTogetherRoomEndReason.ROOM_CLOSED -> Lang.watch_together_room_closed
        WatchTogetherRoomEndReason.SESSION_REPLACED -> Lang.watch_together_session_replaced
    })
}
