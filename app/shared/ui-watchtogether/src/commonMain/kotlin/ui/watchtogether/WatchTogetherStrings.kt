/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.watchtogether

import androidx.compose.runtime.Composable
import me.him188.ani.app.data.network.WatchTogetherJoinFailure
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.watch_together_error_invalid_name
import me.him188.ani.app.ui.lang.watch_together_error_invalid_password
import me.him188.ani.app.ui.lang.watch_together_error_rate_limited
import me.him188.ani.app.ui.lang.watch_together_error_room_closed
import me.him188.ani.app.ui.lang.watch_together_error_room_full
import me.him188.ani.app.ui.lang.watch_together_error_temporary
import me.him188.ani.app.ui.lang.watch_together_error_wrong_password
import me.him188.ani.app.ui.lang.watch_together_join_failed
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun watchTogetherJoinFailureMessage(failure: WatchTogetherJoinFailure): String =
    watchTogetherJoinFailureMessage(stringResource(failure.messageResource()))

@Composable
fun watchTogetherJoinFailureMessage(reason: String): String =
    stringResource(Lang.watch_together_join_failed, reason)

fun WatchTogetherJoinFailure.messageResource(): StringResource = when (this) {
    WatchTogetherJoinFailure.WRONG_PASSWORD -> Lang.watch_together_error_wrong_password
    WatchTogetherJoinFailure.ROOM_FULL -> Lang.watch_together_error_room_full
    WatchTogetherJoinFailure.ROOM_CLOSED -> Lang.watch_together_error_room_closed
    WatchTogetherJoinFailure.INVALID_NAME -> Lang.watch_together_error_invalid_name
    WatchTogetherJoinFailure.INVALID_PASSWORD -> Lang.watch_together_error_invalid_password
    WatchTogetherJoinFailure.RATE_LIMITED -> Lang.watch_together_error_rate_limited
    WatchTogetherJoinFailure.TEMPORARY -> Lang.watch_together_error_temporary
}
