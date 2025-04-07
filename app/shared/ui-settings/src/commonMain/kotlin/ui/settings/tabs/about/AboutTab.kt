/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.DelicateCoroutinesApi
import me.him188.ani.app.data.network.protocol.ReleaseClass
import me.him188.ani.app.platform.LocalContext
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.Res
import me.him188.ani.app.ui.foundation.a
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.icons.AniIcons
import me.him188.ani.app.ui.foundation.icons.DeployedCodeAccount
import me.him188.ani.app.ui.foundation.icons.News
import me.him188.ani.app.ui.foundation.icons.QqRoundedOutline
import me.him188.ani.app.ui.foundation.icons.Telegram
import me.him188.ani.app.ui.foundation.widgets.HeroIcon
import me.him188.ani.app.ui.foundation.widgets.HeroIconDefaults
import me.him188.ani.app.ui.settings.rendering.ReleaseClassIcon
import me.him188.ani.app.ui.settings.rendering.guessReleaseClass
import me.him188.ani.app.ui.settings.tabs.AniHelpNavigator
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Immutable
data class AboutTabInfo(
    val version: String,
    val releaseClass: ReleaseClass = guessReleaseClass(version),
)

@OptIn(DelicateCoroutinesApi::class, TestOnly::class)
@Composable
fun AboutTab(
    state: AboutTabInfo,
    onTriggerDebugMode: () -> Unit,
    onClickReleaseNotes: () -> Unit,
    onClickWebsite: () -> Unit,
    onClickFeedback: () -> Unit,
    onClickSource: () -> Unit,
    onClickDevelopers: () -> Unit,
    onClickAcknowledgements: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        // Centered HeroIcon at the top
        Column(Modifier.padding(vertical = 36.dp)) {
            HeroIcon(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                ),
            ) {
                Icon(
                    painterResource(Res.drawable.a),
                    contentDescription = "Animeko Icon",
                    Modifier
                        .clip(CircleShape)
                        .size(HeroIconDefaults.iconSize),
                    tint = Color.Unspecified,
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Animeko",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "集找番、追番、看番的一站式弹幕追番平台",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(36.dp))

        // Menu items

        val listItemColors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
        )

        ListItem(
            headlineContent = { Text("版本号") },
            modifier = Modifier.clickable(onClick = onTriggerDebugMode, role = Role.Button),
            leadingContent = { ReleaseClassIcon(state.releaseClass) },
            supportingContent = { Text(state.version) },
            colors = listItemColors,
        )
        ListItem(
            headlineContent = { Text("更新说明") },
            modifier = Modifier.clickable(onClick = onClickReleaseNotes, role = Role.Button),
            leadingContent = {
                Icon(Icons.Outlined.News, contentDescription = null)
            },
            colors = listItemColors,
        )
        ListItem(
            headlineContent = { Text("官网") },
            modifier = Modifier.clickable(onClick = onClickWebsite),
            leadingContent = {
                Icon(Icons.Outlined.Home, contentDescription = null)
            },
            colors = listItemColors,
        )
        ListItem(
            headlineContent = { Text("反馈建议") },
            modifier = Modifier.clickable(onClick = onClickFeedback),
            leadingContent = {
                Icon(Icons.Outlined.Feedback, contentDescription = null)
            },
            colors = listItemColors,
        )
        ListItem(
            headlineContent = { Text("源代码") },
            modifier = Modifier.clickable(onClick = onClickSource),
            leadingContent = {
                Icon(Icons.Outlined.Code, contentDescription = null)
            },
            colors = listItemColors,
        )
        ListItem(
            headlineContent = { Text("开发者名单") },
            modifier = Modifier.clickable(onClick = onClickDevelopers),
            leadingContent = {
                Icon(Icons.Outlined.DeployedCodeAccount, contentDescription = null)
            },
            colors = listItemColors,
        )
//        ListItem(
//            headlineContent = { Text("鸣谢") },
//            modifier = Modifier.clickable(onClick = onClickAcknowledgements),
//            leadingContent = {
//                Icon(Icons.Outlined.AwardStar, null)
//            },
//            colors = listItemColors,
//        )

        var showChatGroups by rememberSaveable { mutableStateOf(false) }
        ListItem(
            headlineContent = { Text("交流群") },
            modifier = Modifier.clickable { showChatGroups = !showChatGroups },
            leadingContent = {
                Icon(Icons.Outlined.Forum, null)
            },
            colors = listItemColors,
        )
        AniAnimatedVisibility(
            showChatGroups,
            label = "ChatGroups",
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(start = (24 + 16).dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val context = LocalContext.current
                SuggestionChip(
                    { AniHelpNavigator.openJoinQQGroup(context) },
                    icon = {
                        Icon(
                            AniIcons.QqRoundedOutline, "QQ 群",
                            Modifier.size(20.dp),
                        )
                    },
                    label = { Text("927170241") },
                )

                SuggestionChip(
                    { AniHelpNavigator.openTelegram(context) },
                    icon = {
                        Image(
                            AniIcons.Telegram, "Telegram",
                            Modifier.size(20.dp),
                        )
                    },
                    label = { Text("Telegram") },
                )
            }
        }
    }
}

@TestOnly
val TestAboutTabInfo
    get() = AboutTabInfo(
        version = "4.8.0-alpha02",
        releaseClass = ReleaseClass.ALPHA,
    )

@OptIn(TestOnly::class)
@Composable
@Preview
private fun PreviewAboutTab() {
    ProvideCompositionLocalsForPreview {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            AboutTab(
                TestAboutTabInfo, {}, {}, {}, {}, {}, {}, {},
            )
        }
    }
}
