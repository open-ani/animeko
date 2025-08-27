/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediafetch

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.media.selector.MediaExclusionReason
import me.him188.ani.app.domain.media.selector.UnsafeOriginalMediaAccess
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.app.tools.formatDateTime
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_debug_copied
import me.him188.ani.app.ui.media.renderSubtitleLanguage
import me.him188.ani.app.ui.settings.rendering.MediaSourceIcon
import me.him188.ani.app.ui.settings.rendering.MediaSourceIcons
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.ResourceLocation
import org.jetbrains.compose.resources.getString


@OptIn(UnsafeOriginalMediaAccess::class)
@Composable
internal fun MediaSelectorItem(
    group: MediaGroup,
    groupState: MediaGroupState,
    mediaSourceInfoProvider: MediaSourceInfoProvider,
    selected: Boolean,
    onSelect: (Media) -> Unit,
    preferredResolution: () -> String?,
    onPreferResolution: (String) -> Unit,
    preferredSubtitleLanguageId: () -> String?,
    onPreferSubtitleLanguageId: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // We use the first media for display because the group has the same info.
    val media: Media = group.first.original
    val clipboard = LocalClipboardManager.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()

    // Determine the reason text, if any
    val reasonText = group.exclusionReason?.let { reason ->
        if (currentAniBuildConfig.isDebug) {
            reason.toString()
        } else {
            when (reason) {
                MediaExclusionReason.MediaWithoutSubtitle -> "无字幕"
                is MediaExclusionReason.SingleEpisodeForCompleteSubject -> "单集资源"
                MediaExclusionReason.UnsupportedByPlatformPlayer -> "不支持播放"
                MediaExclusionReason.FromSequelSeason -> "季度不匹配"
                MediaExclusionReason.FromSeriesSeason -> "季度不匹配(2)"
                MediaExclusionReason.SubjectNameMismatch -> "条目标题不匹配"
            }
        }
    }

    // Now we call the stateless layout, passing only the data and callbacks
    MediaSelectorItemLayout(
        selected = selected,
        onClick = { onSelect(media) },
        onLongClick = {
            if (media.download !is ResourceLocation.MagnetLink) {
                return@MediaSelectorItemLayout
            }
            scope.launch {
                val downloadUri = media.download.uri
                clipboard.setText(AnnotatedString(downloadUri))
                toaster.toast(getString(Lang.settings_debug_copied, downloadUri))
            }
        },
        title = { Text(media.originalTitle) },
        labels = {
            // Size chip
            if (media.properties.size != FileSize.Zero && media.properties.size != FileSize.Unspecified) {
                InputChip(
                    selected = false,
                    onClick = { /* no-op */ },
                    label = { Text(media.properties.size.toString()) },
                )
            }
            // Resolution chip
            InputChip(
                selected = false,
                onClick = { onPreferResolution(media.properties.resolution) },
                label = { Text(media.properties.resolution) },
                enabled = preferredResolution() != media.properties.resolution,
            )
            // Subtitle chips
            media.properties.subtitleLanguageIds.forEach { languageId ->
                InputChip(
                    selected = false,
                    onClick = { onPreferSubtitleLanguageId(languageId) },
                    label = { Text(renderSubtitleLanguage(languageId)) },
                    enabled = preferredSubtitleLanguageId() != languageId,
                )
            }
            // Exclusion reason chip
            reasonText?.let {
                InputChip(
                    selected = false,
                    onClick = { /* no-op */ },
                    label = { Text(it) },
                    colors = InputChipDefaults.inputChipColors(
                        labelColor = MaterialTheme.colorScheme.error,
                    ),
                    border = InputChipDefaults.inputChipBorder(
                        enabled = true,
                        selected = false,
                        borderColor = MaterialTheme.colorScheme.error,
                    ),
                )
            }
        },
        // The FlowRow chips in the top area become a slot
        exposedMediaSourceMenu = {
            ExposedMediaSourceMenu(
                group = group,
                groupState = groupState,
                mediaSourceInfoProvider = mediaSourceInfoProvider,
                onSelect = onSelect,
            )
        },
        alliance = {
            Text(
                media.properties.alliance,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        },
        publishedTime = {
            Text(
                formatDateTime(media.publishedTime, showTime = false),
                maxLines = 1,
                softWrap = false,
            )
        },
        modifier = modifier,
    )
}

/**
 * A "stateless" layout composable that receives slots ([labels], [bottomRow])
 * and just focuses on how to present them. It knows nothing about the actual
 * Media/State classes.
 */
@Composable
fun MediaSelectorItemLayout(
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    title: @Composable () -> Unit,
    labels: @Composable RowScope.() -> Unit,
    exposedMediaSourceMenu: @Composable () -> Unit,
    alliance: @Composable () -> Unit,
    publishedTime: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.width(IntrinsicSize.Min)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = if (!selected) BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline,
        ) else null,
    ) {
        val horizontalPadding = 16.dp

        Column(
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        ) {
            // Title
            ProvideTextStyle(MaterialTheme.typography.titleSmall) {
                Row(Modifier.padding(horizontal = horizontalPadding)) {
                    title()
                }
            }

            // Top chips
            FlowRow(
                modifier = Modifier
                    .padding(horizontal = horizontalPadding)
                    .padding(top = 8.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = labels,
            )

            // Bottom row
            ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                Row(
                    modifier = Modifier
                        .padding(
                            start = horizontalPadding - 8.dp,
                            end = horizontalPadding,
                        )
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        exposedMediaSourceMenu()
                        Box(
                            Modifier.weight(1f, fill = false),
                            contentAlignment = Alignment.Center,
                        ) {
                            alliance()
                        }
                    }

                    Box(Modifier.padding(start = 16.dp)) {
                        publishedTime()
                    }
                }
            }
        }
    }
}

@OptIn(UnsafeOriginalMediaAccess::class)
@Composable
private fun ExposedMediaSourceMenu(
    group: MediaGroup,
    groupState: MediaGroupState,
    mediaSourceInfoProvider: MediaSourceInfoProvider,
    onSelect: (Media) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(showMenu, { showMenu = it }, modifier) {
        val currentItem = groupState.selectedItem ?: group.first.original
        val currentSourceInfo by mediaSourceInfoProvider.rememberMediaSourceInfo(currentItem.mediaSourceId)
        TextField(
            value = currentSourceInfo?.displayName ?: "未知",
            onValueChange = {},
            Modifier
                .widthIn(min = 48.dp) // override default
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            maxLines = 1,
            leadingIcon = {
                Icon(MediaSourceIcons.location(currentItem.location, currentItem.kind), null)
            },
            trailingIcon = if (group.list.size > 1) {
                {
                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded = showMenu,
                        Modifier.menuAnchor(ExposedDropdownMenuAnchorType.SecondaryEditable),
                    )
                }
            } else null,
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
            ),
        )
        ExposedDropdownMenu(showMenu, { showMenu = false }) {
            for (maybeExcluded in group.list) {
                val item = maybeExcluded.original
                val sourceInfo by mediaSourceInfoProvider.rememberMediaSourceInfo(item.mediaSourceId)
                DropdownMenuItem(
                    text = { Text(sourceInfo?.displayName ?: "未知") },
                    leadingIcon = { MediaSourceIcon(sourceInfo, Modifier.size(24.dp)) },
                    onClick = {
                        groupState.selectedItem = item
                        onSelect(item)
                        showMenu = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
