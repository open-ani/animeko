/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediaselect.summary

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.text.ProvideContentColor
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediaSelectorSummaryBanner(
    summary: MediaSelectorSummary,
    onClickSwitchSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionScheme = LocalAniMotionScheme.current
    val transitionSpec: AnimatedContentTransitionScope<MediaSelectorSummary>.() -> ContentTransform = {
        val default = motionScheme.animatedContent.standard(this)
        if (initialState.typeId == targetState.typeId) {
            ContentTransform(
                EnterTransition.None,
                ExitTransition.None,
                sizeTransform = default.sizeTransform,
            )
        } else {
            default
        }
    }

    Card(
        onClick = onClickSwitchSource,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                summary,
                modifier = Modifier.weight(1f),
                transitionSpec = transitionSpec,
                contentAlignment = Alignment.CenterStart,
            ) { state ->
                when (state) {
                    is MediaSelectorSummary.AutoSelecting -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            LoadingIndicator(Modifier.size(20.dp))
                            Text(
                                "正在查询数据源",
                                style = MaterialTheme.typography.titleSmall,
                                softWrap = false,
                            )
                        }
                    }

                    is MediaSelectorSummary.RequiresManualSelection -> {
                        ProvideContentColor(MaterialTheme.colorScheme.error) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    Modifier.size(20.dp),
                                )
                                Text(
                                    "请选择数据源",
                                    style = MaterialTheme.typography.titleSmall,
                                    softWrap = false,
                                )
                            }
                        }
                    }

                    is MediaSelectorSummary.Selected -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            MediaSelectorSummaryBannerSourceIcon(
                                state.source,
                                Modifier.size(20.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    state.source.sourceName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (!state.isPerfectMatch) {
                                    Text(
                                        state.mediaTitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Icon(
                Icons.Rounded.SyncAlt,
                contentDescription = "切换数据源",
                Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun MediaSelectorSummaryBannerSourceIcon(source: MediaSelectorSourceSummary, modifier: Modifier) {
    me.him188.ani.app.ui.mediaselect.common.SourceIcon(
        iconUrl = source.sourceIconUrl,
        sourceName = source.sourceName,
        modifier,
    )
}

@OptIn(TestOnly::class)
@Composable
@Preview
private fun PreviewMediaSelectorSummaryBanner() {
    ProvideCompositionLocalsForPreview {
        Column(
            Modifier.width(360.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MediaSelectorSummaryBanner(
                summary = MediaSelectorSummary.AutoSelecting(
                    sources = TestMediaSelectorSourceSummaries,
                    estimate = 5.seconds,
                ),
                onClickSwitchSource = {},
            )
            MediaSelectorSummaryBanner(
                summary = MediaSelectorSummary.Selected(
                    source = TestMediaSelectorSourceSummaries[0],
                    mediaTitle = "葬送的芙莉莲 第 01 话",
                    isPerfectMatch = true,
                ),
                onClickSwitchSource = {},
            )
            MediaSelectorSummaryBanner(
                summary = MediaSelectorSummary.Selected(
                    source = TestMediaSelectorSourceSummaries[1],
                    mediaTitle = "Sousou no Frieren - 01 [1080p][CHS][WebRip]",
                    isPerfectMatch = false,
                ),
                onClickSwitchSource = {},
            )
            MediaSelectorSummaryBanner(
                summary = MediaSelectorSummary.RequiresManualSelection(
                    sources = TestMediaSelectorSourceSummaries,
                ),
                onClickSwitchSource = {},
            )
        }
    }
}
