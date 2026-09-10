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
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.him188.ani.app.ui.foundation.avatar.AvatarImage
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_episode_default_title
import me.him188.ani.app.ui.lang.watch_together_chip_following
import me.him188.ani.app.ui.lang.watch_together_chip_free
import me.him188.ani.app.ui.lang.watch_together_connection_connected
import me.him188.ani.app.ui.lang.watch_together_connection_degraded
import me.him188.ani.app.ui.lang.watch_together_connection_reconnecting
import me.him188.ani.app.ui.lang.watch_together_episode_label
import me.him188.ani.app.ui.lang.watch_together_host
import me.him188.ani.app.ui.lang.watch_together_host_empty_self
import me.him188.ani.app.ui.lang.watch_together_host_idle
import me.him188.ani.app.ui.lang.watch_together_members_label
import me.him188.ani.app.ui.lang.watch_together_you
import me.him188.ani.app.ui.lang.watch_together_you_are_host
import me.him188.ani.app.ui.watchtogether.WatchTogetherConnectionPresentation
import me.him188.ani.app.ui.watchtogether.WatchTogetherMemberPresence
import me.him188.ani.app.ui.watchtogether.WatchTogetherMemberPresentation
import me.him188.ani.app.ui.watchtogether.WatchTogetherPlaybackPresentation
import me.him188.ani.app.ui.watchtogether.stateIconAndText
import me.him188.ani.app.ui.watchtogether.watchTogetherStatusText
import me.him188.ani.leanback.ui.foundation.formatPlaybackTime
import me.him188.ani.leanback.ui.foundation.widgets.LocalTvOptionColors
import me.him188.ani.leanback.ui.foundation.widgets.TvOptionDefaults
import org.jetbrains.compose.resources.stringResource
import java.lang.Character.toChars

private object TvTogetherColors {
    val Connected = Color(0xFFA5D399)
    val Reconnecting = Color(0xFFFFCF86)
}

@Composable
internal fun TvTogetherIntro(title: String, description: String?, icon: ImageVector) {
    val colors = LocalTvOptionColors.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = colors.content)
            if (description != null) Text(description, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
        }
    }
}

@Composable
internal fun TvTogetherRoomHeader(name: String, connection: WatchTogetherConnectionPresentation) {
    val colors = LocalTvOptionColors.current
    val connected = connection == WatchTogetherConnectionPresentation.CONNECTED
    val statusColor = if (connected) TvTogetherColors.Connected else TvTogetherColors.Reconnecting
    Row(
        Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            name, Modifier.weight(1f).semantics { heading() }.testTag("tv-together-room-name"),
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
            color = colors.content, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Row(
            Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                .testTag("tv-together-connection"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(if (connected) Icons.Rounded.CheckCircle else Icons.Rounded.Sync, null, Modifier.size(16.dp), tint = statusColor)
            Text(
                when (connection) {
                    WatchTogetherConnectionPresentation.CONNECTED -> stringResource(Lang.watch_together_connection_connected)
                    WatchTogetherConnectionPresentation.RECONNECTING -> stringResource(Lang.watch_together_connection_reconnecting)
                    WatchTogetherConnectionPresentation.DEGRADED -> stringResource(Lang.watch_together_connection_degraded)
                },
                style = MaterialTheme.typography.bodySmall, color = statusColor, maxLines = 1,
            )
        }
    }
}

/** Read-only D-pad stops scroll into view and never advertise a click action. */
@Composable
private fun TvTogetherReadSurface(
    modifier: Modifier,
    containerColor: Color = Color.Transparent,
    content: @Composable ColumnScope.(focused: Boolean) -> Unit,
) {
    val colors = LocalTvOptionColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    // Change the two colors together; interpolating inverse colors would lose contrast mid-transition.
    CompositionLocalProvider(LocalContentColor provides if (focused) colors.focusedContent else colors.content) {
        Column(
            modifier.fillMaxWidth()
                .background(if (focused) colors.focusedContainer else containerColor, TvOptionDefaults.ItemShape)
                .border(2.dp, if (focused) colors.focusedContainer else Color.Transparent, TvOptionDefaults.ItemShape)
                .semantics(mergeDescendants = true) {}
                .focusable(interactionSource = interactionSource)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) { content(focused) }
    }
}

@Composable
internal fun TvTogetherPlaybackCard(playback: WatchTogetherPlaybackPresentation?, isHost: Boolean, modifier: Modifier = Modifier) {
    TvTogetherReadSurface(modifier.testTag("tv-together-playback"), LocalTvOptionColors.current.raised) { focused ->
        val contentColor = LocalContentColor.current
        if (isHost && playback != null) {
            Text(stringResource(Lang.watch_together_you_are_host), style = MaterialTheme.typography.labelMedium, color = contentColor.copy(alpha = .8f))
        }
        if (playback == null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Tv, null, Modifier.size(20.dp))
                Text(stringResource(if (isHost) Lang.watch_together_you_are_host else Lang.watch_together_host_idle), style = MaterialTheme.typography.titleMedium)
            }
            if (isHost) Text(
                stringResource(Lang.watch_together_host_empty_self),
                style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = .8f),
            )
        } else {
            Text(
                playback.subjectName, Modifier.then(if (focused) Modifier.basicMarquee() else Modifier),
                style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (playback.episodeName.isBlank()) stringResource(Lang.subject_episode_default_title, playback.episodeSort)
                else stringResource(Lang.watch_together_episode_label, playback.episodeSort, playback.episodeName),
                style = MaterialTheme.typography.bodyMedium, color = contentColor.copy(alpha = .8f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val (stateIcon, stateText) = playback.stateIconAndText()
                if (playback.loading || playback.buffering) {
                    CircularProgressIndicator(
                        Modifier.size(16.dp).testTag("tv-together-playback-loading"), color = contentColor, strokeWidth = 2.dp,
                    )
                } else Icon(stateIcon, null, Modifier.size(16.dp))
                Text(
                    stateText, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (!playback.loading) Text(
                    "${formatPlaybackTime(playback.positionMillis)} / ${formatPlaybackTime(playback.durationMillis)}",
                    style = MaterialTheme.typography.labelMedium, color = contentColor.copy(alpha = .8f),
                    maxLines = 1,
                )
            }
            // Same as the shared NowPlayingCard: loading has no meaningful duration/progress yet.
            if (!playback.loading) {
                val progress = if (playback.durationMillis > 0) {
                    (playback.positionMillis.toFloat() / playback.durationMillis).coerceIn(0f, 1f)
                } else 0f
                Box(
                    Modifier.fillMaxWidth().padding(top = 2.dp).height(4.dp).clip(CircleShape)
                        .background(contentColor.copy(alpha = .15f)).testTag("tv-together-progress")
                        .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) },
                ) {
                    Box(Modifier.fillMaxWidth(progress).height(4.dp).background(contentColor, CircleShape))
                }
            }
        }
    }
}

@Composable
internal fun TvTogetherMembersHeading(count: Int) {
    Text(
        stringResource(Lang.watch_together_members_label, count), Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp).semantics { heading() },
        style = MaterialTheme.typography.titleSmall, color = LocalTvOptionColors.current.muted,
    )
}

/** A continuous two-line TV list: only the focused row gets a container. */
@Composable
internal fun TvTogetherMemberRow(member: WatchTogetherMemberPresentation, modifier: Modifier = Modifier) {
    TvTogetherReadSurface(modifier.testTag("tv-together-member-${member.userId}")) { focused ->
        val color = LocalContentColor.current
        val disconnected = member.state == WatchTogetherMemberPresence.DISCONNECTED
        Row(
            Modifier.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = .12f))
                    .alpha(if (disconnected) .6f else 1f).clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                if (member.avatarUrl == null) {
                    val initial = member.nickname.trim().takeIf { it.isNotEmpty() }
                        ?.let { String(toChars(it.codePointAt(0))) } ?: "?"
                    Text(initial, style = MaterialTheme.typography.titleMedium)
                } else AvatarImage(member.avatarUrl, Modifier.size(36.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        listOfNotNull(member.nickname, stringResource(Lang.watch_together_you).takeIf { member.isSelf }).joinToString(" "),
                        Modifier.weight(1f).then(if (focused) Modifier.basicMarquee() else Modifier),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when {
                            member.isHost -> stringResource(Lang.watch_together_host)
                            member.following -> stringResource(Lang.watch_together_chip_following)
                            else -> stringResource(Lang.watch_together_chip_free)
                        },
                        style = MaterialTheme.typography.labelMedium, color = color.copy(alpha = .75f), maxLines = 1,
                    )
                }
                Text(
                    member.watchTogetherStatusText(),
                    Modifier.then(if (focused) Modifier.basicMarquee() else Modifier),
                    style = MaterialTheme.typography.bodyMedium,
                    color = color.copy(alpha = if (disconnected) .7f else .8f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
