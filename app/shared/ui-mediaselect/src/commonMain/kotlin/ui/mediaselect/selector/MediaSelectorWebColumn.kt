/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediaselect.selector

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.domain.mediasource.instance.MediaSourceInstance
import me.him188.ani.app.ui.foundation.IconButton
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.mediaselect.common.SourceIcon
import me.him188.ani.datasources.api.Media
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.ui.tooling.preview.Preview


data class WebSourceChannel(
    val name: String,
    val original: Media? = null,
)

data class WebSource(
    /**
     * @see MediaSourceInstance.instanceId
     */
    val instanceId: String,
    val mediaSourceId: String,
    val iconUrl: String,
//    val iconResourceId: String?,
    val name: String,
    val channels: List<WebSourceChannel>,
    val isLoading: Boolean,
    val isError: Boolean,
)

/**
 * https://www.figma.com/design/LET1n9mmDa6npDTIlUuJjU/Animeko?node-id=1054-13751&t=OSgRmNiOHpUGBYYu-0
 */
@Composable
fun MediaSelectorWebSourcesColumn(
    list: List<WebSource>,
    selectedSource: () -> WebSource?,
    selectedChannel: () -> WebSourceChannel?,
    onSelect: (WebSource, WebSourceChannel) -> Unit,
    onRefresh: (WebSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val card = @Composable { source: WebSource ->
        WebSourceCard(
            source,
            selectedChannel = { if (selectedSource() == source) selectedChannel() else null },
            onSelect = {
                onSelect(source, it)
            },
            onRefresh = {
                onRefresh(source)
            },
            Modifier.fillMaxWidth(),
        )
    }
//    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
//        items(list, key = { it.instanceId }) { source ->
//            card(source)
//        }
//    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // not scrollable. 否则会跟 bottom sheet 的 scroll 冲突. 
        list.forEach { source ->
            card(source)
        }
    }
}

@Composable
private fun WebSourceCard(
    source: WebSource,
    selectedChannel: () -> WebSourceChannel?,
    onSelect: (WebSourceChannel) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val minHeight = 48.dp
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            Modifier.heightIn(min = minHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SourceIcon(
                source.iconUrl, source.name,
                Modifier.size(24.dp),
            )
            Box(Modifier.padding(start = 8.dp)) {
                Text(
                    "五个字占位",
                    Modifier.alpha(0f).width(IntrinsicSize.Max),
                    softWrap = true,
                    maxLines = 2,
                )
                Text(
                    source.name,
                    Modifier.matchParentSize(),
                    softWrap = true,
                    maxLines = 2,
                    overflow = TextOverflow.Clip,
                )
            }
        }

        FlowRow(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy((-8).dp),
        ) {
            for (channel in source.channels) {
                InputChip(
                    selected = channel == selectedChannel(),
                    onClick = { onSelect(channel) },
                    label = { Text(channel.name) },
                )
            }

            if (source.isLoading) {
                Box(
                    Modifier.minimumInteractiveComponentSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }

            if (source.isError) {
                Box(
                    Modifier.minimumInteractiveComponentSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Close, null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        if (source.isError) {
            IconButton(onRefresh) {
                Icon(Icons.Rounded.Refresh, "刷新")
            }
        }
    }
}

@OptIn(TestOnly::class)
@Composable
@Preview
private fun PreviewMediaSelectorWebColumn() {
    ProvideCompositionLocalsForPreview {
        Surface {
            MediaSelectorWebSourcesColumn(
                TestWebSources,
                selectedSource = { TestWebSources[0] },
                selectedChannel = { TestWebSources[0].channels[1] },
                onSelect = { _, _ -> },
                onRefresh = {},
            )
        }
    }
}

@OptIn(TestOnly::class)
@Composable
@Preview
private fun PreviewWebSourceCard() {
    ProvideCompositionLocalsForPreview {
        Surface {
            WebSourceCard(
                TestWebSources[0],
                selectedChannel = { TestWebSourceChannels2[0] },
                onSelect = {},
                onRefresh = {},
            )
        }
    }
}


@TestOnly
internal val TestWebSources
    get() = (0..10).mapTo(mutableListOf()) {
        WebSource(
            instanceId = "source$it",
            mediaSourceId = "source$it",
            iconUrl = "https://example.com/example.png",
            name = "数据源 $it",
            channels = if (it % 2 == 0) {
                TestWebSourceChannels1
            } else {
                TestWebSourceChannels2
            },
            isError = it % 4 == 0,
            isLoading = it % 4 == 3,
        )
    }.apply {
        add(
            1,
            WebSource(
                instanceId = "source none",
                mediaSourceId = "source none",
                iconUrl = "https://example.com/example.png",
                name = "初始",
                channels = emptyList(),
                isError = false,
                isLoading = true,
            ),
        )
        add(
            1,
            WebSource(
                instanceId = "source error",
                mediaSourceId = "source error",
                iconUrl = "https://example.com/example.png",
                name = "查询错误的数据源",
                channels = emptyList(),
                isError = true,
                isLoading = false,
            ),
        )
    }

@TestOnly
private val TestWebSourceChannels1
    get() = listOf(
        WebSourceChannel(name = "线路1"),
        WebSourceChannel(name = "线路2"),
        WebSourceChannel(name = "线路3"),
        WebSourceChannel(name = "线路4"),
        WebSourceChannel(name = "线路5"),
    )

@TestOnly
private val TestWebSourceChannels2
    get() = listOf(
        WebSourceChannel(name = "主线"),
        WebSourceChannel(name = "备线"),
    )
