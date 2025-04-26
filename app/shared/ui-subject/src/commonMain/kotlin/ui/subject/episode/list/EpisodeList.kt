/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.him188.ani.app.data.models.preference.EpisodeListProgressTheme
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.theme.stronglyWeaken
import me.him188.ani.app.ui.foundation.theme.weaken
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.ui.tooling.preview.PreviewLightDark

@Composable
fun EpisodeListDialog(
    state: EpisodeListUiState,
    onDismissRequest: () -> Unit,
    onCacheClick: () -> Unit,
    onEpisodeClick: (episode: EpisodeListItem) -> Unit,
    onCollectionUpdate: (episode: EpisodeListItem) -> Unit,
    onSubjectDetailsClick: (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    Dialog(onDismissRequest, properties) {
        Card {
            Box {
                Column(Modifier.padding(16.dp)) {
                    Row {
                        Text("选集播放", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.weight(1f))
                    }

                    Row(Modifier.padding(top = 8.dp)) {
                        ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
                            Text(state.subjectTitle)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Column(
                        Modifier.weight(1f, fill = false)
                            .heightIn(max = 360.dp) // 特别长需要限制高度并且滚动, #182
                            .verticalScroll(rememberScrollState()),
                    ) {
                        EpisodeListFlowRow(
                            state.mainEpisodes,
                            onEpisodeClick,
                            onCollectionUpdate,
                        )

                        if (state.otherEpisodes.isNotEmpty()) {
                            HorizontalDivider(Modifier.padding(vertical = 16.dp))

                            EpisodeListFlowRow(
                                state.otherEpisodes,
                                onEpisodeClick,
                                onCollectionUpdate,
                            )
                        }

                        Spacer(Modifier.height(16.dp))
                    }

                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Lightbulb, null)

                        Text("长按还可以标记为已看", Modifier.padding(start = 4.dp))
                    }

                    Row(Modifier.padding(top = 16.dp).align(Alignment.End)) {
                        onSubjectDetailsClick?.let {
                            TextButton(
                                {
                                    onDismissRequest()
                                    it()
                                },
                            ) {
                                Text("条目详情")
                            }
                        }

                        TextButton(onDismissRequest, Modifier.padding(start = 8.dp)) {
                            Text("关闭")
                        }
                    }
                }

                IconButton(onCacheClick, Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Icon(Icons.Rounded.Download, "缓存")
                }
            }
        }
    }
}


@Immutable
class EpisodeListColors(
    /**
     * 看过或抛弃的颜色
     */
    val doneOrDroppedColor: Color,
    /**
     * 可以看但还没看
     */
    val canWatchColor: Color,
    /**
     * 未开播颜色
     */
    val notPublishedColor: Color,
)

object EpisodeListDefaults {
    @Composable
    fun colors(
        theme: EpisodeListProgressTheme = EpisodeListProgressTheme.Default,
        action: Color = MaterialTheme.colorScheme.primary,
        disabled: Color = MaterialTheme.colorScheme.onSurface.stronglyWeaken(),
    ): EpisodeListColors {
        val dark = action.weaken()
        return when (theme) {
            EpisodeListProgressTheme.ACTION -> EpisodeListColors(
                doneOrDroppedColor = dark,
                canWatchColor = action,
                notPublishedColor = disabled,
            )

            EpisodeListProgressTheme.LIGHT_UP -> EpisodeListColors(
                doneOrDroppedColor = action,
                canWatchColor = dark,
                notPublishedColor = disabled,
            )
        }
    }
}

///////////////////////////////////////////////////////////////////////////
// Previews
///////////////////////////////////////////////////////////////////////////


@OptIn(TestOnly::class)
@PreviewLightDark
@Composable
private fun PreviewEpisodeProgressDialog() {
    ProvideCompositionLocalsForPreview {
        EpisodeListDialog(
            TestEpisodeListUiState,
            {}, {}, {}, {},
        )
    }
}


// 特别长需要限制高度并且滚动, #182
@OptIn(TestOnly::class)
@PreviewLightDark
@Composable
private fun PreviewEpisodeProgressDialogVeryLong() {
    ProvideCompositionLocalsForPreview {
        EpisodeListDialog(
            TestEpisodeListUiStateVeryLong,
            {}, {}, {}, {},
        )
    }
}

@Composable
private fun PreviewEpisodeListFlowRowImpl(
    episodes: List<EpisodeListItem>,
    theme: EpisodeListProgressTheme = EpisodeListProgressTheme.Default,
) {
    EpisodeListFlowRow(
        episodes = episodes,
        onClick = {},
        onLongClick = {},
        theme = theme,
    )
}
