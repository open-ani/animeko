/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.watchtogether

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.watch_together_cancel
import me.him188.ani.app.ui.lang.watch_together_disband
import me.him188.ani.app.ui.lang.watch_together_follow_host
import me.him188.ani.app.ui.lang.watch_together_follow_host_desc
import me.him188.ani.app.ui.lang.watch_together_join
import me.him188.ani.app.ui.lang.watch_together_joining
import me.him188.ani.app.ui.lang.watch_together_leave
import me.him188.ani.leanback.ui.foundation.widgets.LocalTvOptionColors
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionDefaults
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionRow
import org.jetbrains.compose.resources.stringResource

/** One stable TV button across join/retry/cancel states, so progress never removes its focus anchor. */
@Composable
internal fun TvTogetherAction(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    TvOptionRow(
        title = text,
        modifier = modifier.semantics { role = Role.Button },
        leadingContent = {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
            else icon?.let { Icon(it, null, Modifier.size(20.dp)) }
        },
        onClick = onClick,
    )
}

@Composable
internal fun TvTogetherJoinError(text: String) {
    val color = MaterialTheme.colorScheme.error
    Row(
        Modifier.fillMaxWidth().background(color.copy(alpha = .12f), TvOptionDefaults.ItemShape)
            .padding(12.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .testTag("tv-together-error"),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(20.dp), tint = color)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
internal fun TvTogetherJoinAction(joining: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (joining) Text(
        stringResource(Lang.watch_together_joining), Modifier.padding(horizontal = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.bodyMedium, color = LocalTvOptionColors.current.muted,
    )
    TvTogetherAction(
        stringResource(if (joining) Lang.watch_together_cancel else Lang.watch_together_join),
        modifier.testTag("tv-together-submit"),
        icon = Icons.Rounded.Groups, loading = joining, onClick = onClick,
    )
}

@Composable
internal fun TvTogetherLeaveAction(isHost: Boolean, modifier: Modifier = Modifier, confirm: Boolean = false, onClick: () -> Unit) {
    TvTogetherAction(
        stringResource(if (isHost) Lang.watch_together_disband else Lang.watch_together_leave),
        modifier.testTag(if (confirm) "tv-together-confirm-leave" else "tv-together-leave"),
        icon = Icons.AutoMirrored.Rounded.Logout,
        onClick = onClick,
    )
}

@Composable
internal fun TvTogetherFollowAction(following: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TvOptionRow(
        title = stringResource(Lang.watch_together_follow_host),
        supportingText = stringResource(Lang.watch_together_follow_host_desc),
        modifier = modifier,
        checked = following,
        onClick = onClick,
    )
}
