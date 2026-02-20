/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediafetch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.widgets.RichDialogLayout
import me.him188.ani.app.ui.settings.rendering.MediaSourceIcons


@Composable
fun MediaSelectorHelp(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    RichDialogLayout(
        title = { Text("数据源帮助") },
        buttons = {
            TextButton(onDismissRequest) {
                Text("关闭")
            }
        },
        modifier,
    ) {
        Text("数据源类型", style = MaterialTheme.typography.titleMedium)

        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExplainerCard(
                title = { Text("BT") },
                Modifier.weight(1f),
                icon = {
                    Icon(MediaSourceIcons.KindBT, null)
                },
            ) {
                Text("从 BitTorrent 网络获取资源，清晰度高，资源全面，加载速度可能不快")
            }
            ExplainerCard(
                title = { Text("在线") },
                Modifier.weight(1f),
                icon = {
                    Icon(MediaSourceIcons.KindWeb, null)
                },
            ) {
                Text("从在线视频网站获取资源，加载速度快，但清晰度通常不高")
            }
        }
    }
}

@Composable
fun ExplainerCard(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    OutlinedCard(modifier) {
        Column(Modifier.padding(all = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(
                Modifier.align(Alignment.CenterHorizontally),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                icon?.let {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                        it()
                    }
                }
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                    title()
                }
            }

            ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                content()
            }
        }
    }
}


@PreviewLightDark
@Composable
private fun PreviewMediaSelectorHelp() {
    ProvideCompositionLocalsForPreview {
        MediaSelectorHelp({})
    }
}
