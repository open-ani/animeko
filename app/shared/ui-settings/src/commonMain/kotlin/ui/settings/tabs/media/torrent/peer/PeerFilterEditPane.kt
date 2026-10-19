/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media.torrent.peer

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import me.him188.ani.app.data.models.preference.TorrentPeerConfig
import me.him188.ani.app.domain.torrent.peer.PeerFilterSubscription
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
import me.him188.ani.app.ui.foundation.rememberBackgroundScope
import me.him188.ani.app.ui.foundation.text.ProvideTextStyleContentColor
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_media_torrent_peer_blacklist_description
import me.him188.ani.app.ui.lang.settings_media_torrent_peer_blacklist_title
import me.him188.ani.app.ui.lang.settings_media_torrent_peer_filter_always_block_invalid_fingerprint
import me.him188.ani.app.ui.lang.settings_media_torrent_peer_filter_auto_save_hint
import me.him188.ani.app.ui.lang.settings_media_torrent_peer_filter_client_fingerprint
import me.him188.ani.app.ui.lang.settings_media_torrent_peer_filter_client_type
import me.him188.ani.app.ui.lang.settings_media_torrent_peer_filter_ip
import me.him188.ani.app.ui.lang.settings_media_torrent_peer_filter_ip_blacklist
import me.him188.ani.app.ui.lang.settings_media_torrent_peer_filter_ip_blacklist_description
import me.him188.ani.app.ui.lang.settings_media_torrent_peer_filter_load_error
import me.him188.ani.app.ui.lang.settings_media_torrent_peer_filter_local
import me.him188.ani.app.ui.lang.settings_media_torrent_peer_filter_local_description
import me.him188.ani.app.ui.lang.settings_media_torrent_peer_filter_not_updated
import me.him188.ani.app.ui.lang.settings_media_torrent_peer_filter_refresh_all
import me.him188.ani.app.ui.lang.settings_media_torrent_peer_filter_rule
import me.him188.ani.app.ui.lang.settings_media_torrent_peer_filter_stats
import me.him188.ani.app.ui.lang.settings_media_torrent_peer_filter_subscriptions
import me.him188.ani.app.ui.lang.settings_media_torrent_peer_filter_update_error
import me.him188.ani.app.ui.lang.settings_media_torrent_peer_filter_use_builtin
import me.him188.ani.app.ui.richtext.RichText
import me.him188.ani.app.ui.richtext.rememberBBCodeRichTextState
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.components.SwitchItem
import me.him188.ani.app.ui.settings.framework.components.TextItem
import me.him188.ani.app.ui.settings.mediasource.rss.SaveableStorage
import org.jetbrains.compose.resources.stringResource

@Composable
private fun BBCodeSupportingText(text: String, modifier: Modifier = Modifier) {
    val richTextState = rememberBBCodeRichTextState(text)
    RichText(richTextState.elements, modifier)
}

@Composable
private fun RuleEditItem(
    content: String,
    enabled: Boolean,
    supportingTextBBCode: String,
    onContentChange: (String) -> Unit,
    textFieldShape: Shape = MaterialTheme.shapes.extraSmall
) {
    val listItemColors = ListItemDefaults.colors(containerColor = Color.Transparent)
    ListItem(
        headlineContent = {
            OutlinedTextField(
                value = content,
                enabled = enabled,
                label = { Text(stringResource(Lang.settings_media_torrent_peer_filter_rule)) },
                maxLines = 8,
                onValueChange = onContentChange,
                shape = textFieldShape,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        colors = listItemColors,
        supportingContent = {
            BBCodeSupportingText(supportingTextBBCode, Modifier.padding(8.dp))
        },
    )
}

@Composable
private fun renderLastLoaded(lastLoaded: PeerFilterSubscription.LastLoaded?): String {
    val stat = lastLoaded?.ruleStat
    val error = lastLoaded?.error

    if (lastLoaded == null || (stat == null && error == null)) {
        return stringResource(Lang.settings_media_torrent_peer_filter_not_updated)
    }

    return buildString {
        if (stat != null) {
            append(
                stringResource(
                    Lang.settings_media_torrent_peer_filter_stats,
                    stat.ipRuleCount,
                    stat.idRuleCount,
                    stat.clientRuleCount,
                ),
            )
        }

        if (error != null) {
            if (stat == null) {
                append(stringResource(Lang.settings_media_torrent_peer_filter_load_error, error))
            } else {
                appendLine()
                append(stringResource(Lang.settings_media_torrent_peer_filter_update_error, error))
            }
        }
    }
}

@Composable
fun PeerFilterEditPane(
    state: PeerFilterSettingsState,
    showIpBlockingItem: Boolean,
    onClickIpBlockSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val list by state.subscriptions.collectAsStateWithLifecycle()

    SettingsTab(modifier.verticalScroll(rememberScrollState())) {
        Group(
            title = { Text(stringResource(Lang.settings_media_torrent_peer_filter_subscriptions)) },
            actions = {
                val updatingSubs by state.updatingSubs.collectAsStateWithLifecycle(false)
                AnimatedContent(
                    updatingSubs,
                    transitionSpec = LocalAniMotionScheme.current.animatedContent.standard,
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    if (it) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                    } else {
                        IconButton({ state.updateSubs() }) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = stringResource(
                                    Lang.settings_media_torrent_peer_filter_refresh_all,
                                ),
                            )
                        }
                    }
                }
            },
        ) {
            val builtInSub = list.firstOrNull()
            val onCheckedChange by rememberUpdatedState { it: Boolean ->
                if (builtInSub != null) state.toggleSub(builtInSub.subscriptionId, it)
            }

            SwitchItem(
                onClick = { onCheckedChange(!(builtInSub?.enabled ?: false)) },
                title = { Text(stringResource(Lang.settings_media_torrent_peer_filter_use_builtin)) },
                description = { Text(renderLastLoaded(list.firstOrNull()?.lastLoaded)) },
            ) {
                Switch(
                    builtInSub?.enabled ?: false,
                    onCheckedChange = onCheckedChange,
                    enabled = builtInSub != null,
                )
            }
        }

        Group(
            title = { Text(stringResource(Lang.settings_media_torrent_peer_filter_local)) },
            description = { Text(stringResource(Lang.settings_media_torrent_peer_filter_local_description)) },
        ) {
            SwitchItem(
                title = { Text(stringResource(Lang.settings_media_torrent_peer_filter_ip)) },
                checked = state.ipFilterEnabled,
                onCheckedChange = { state.ipFilterEnabled = it },
            )
            AniAnimatedVisibility(visible = state.ipFilterEnabled) {
                RuleEditItem(
                    content = state.ipFiltersPattern,
                    enabled = true,
                    supportingTextBBCode = """
                        每行一条过滤规则，支持 IPv4 和 IPv6
                        支持以下格式：
                        * 无类别域间路由（CIDR）
                          例如：[code]10.0.0.1/24[/code] 将过滤从 [code]10.0.0.0[/code] 至 [code]10.0.0.255[/code] 的所有 IP
                          [code]ff06:1234::/64[/code] 将过滤从 [code]ff06:1234::[/code] 至 [code]ff06:1234::ffff:ffff:ffff:ffff[/code] 的所有 IP
                        * 通配符
                          例如：[code]10.0.12.*[/code] 将过滤从 [code]10.0.12.0[/code] 至 [code]10.0.12.255[/code] 的所有 IP
                          [code]ff06:1234::*[/code] 将过滤从 [code]ff06:1234::[/code] 至 [code]ff06:1234::ffff[/code] 的所有 IP
                          支持多级通配符，例如 [code]10.0.*.*[/code]
                        * 范围表示
                          例如 [code]10.0.24.100-200[/code] 和 [code]ff06:1234::cafe-dead[/code]
                    """.trimIndent(),
                    onContentChange = { state.ipFiltersPattern = it },
                )
            }

            SwitchItem(
                title = { Text(stringResource(Lang.settings_media_torrent_peer_filter_client_fingerprint)) },
                checked = state.idFilterEnabled,
                onCheckedChange = { state.idFilterEnabled = it },
            )
            AniAnimatedVisibility(visible = state.idFilterEnabled) {
                Column {
                    RuleEditItem(
                        content = state.idFiltersRegex,
                        enabled = true,
                        supportingTextBBCode = """
                        每行一条过滤规则，仅支持使用正则表达式过滤
                        例如：[code]\-HP\d{4}\-[/code] 将封禁具有 -HPxxxx- 指纹的客户端
                    """.trimIndent(),
                        onContentChange = { state.idFiltersRegex = it },
                    )
                    SwitchItem(
                        title = {
                            Text(
                                stringResource(
                                    Lang.settings_media_torrent_peer_filter_always_block_invalid_fingerprint,
                                ),
                            )
                        },
                        checked = state.blockInvalidId,
                        onCheckedChange = { state.blockInvalidId = it },
                        description = {
                            BBCodeSupportingText("无论是否满足规则, 都会屏蔽指纹不符合 [code]-xxxxxx-[/code] 格式的客户端")
                        },
                    )
                }
            }

            SwitchItem(
                title = { Text(stringResource(Lang.settings_media_torrent_peer_filter_client_type)) },
                checked = state.clientFilterEnabled,
                onCheckedChange = { state.clientFilterEnabled = it },
            )
            AniAnimatedVisibility(visible = state.clientFilterEnabled) {
                RuleEditItem(
                    content = state.clientFiltersRegex,
                    enabled = true,
                    supportingTextBBCode = """
                        每行一条过滤规则，仅支持使用正则表达式过滤
                        例如：[code]go\.torrent(\sdev)?[/code] 将封禁百度网盘的离线下载客户端
                    """.trimIndent(),
                    onContentChange = { state.clientFiltersRegex = it },
                )
            }

            AniAnimatedVisibility(visible = showIpBlockingItem) {
                Group(
                    title = { Text(stringResource(Lang.settings_media_torrent_peer_blacklist_title)) },
                    description = { Text(stringResource(Lang.settings_media_torrent_peer_blacklist_description)) },
                ) {
                    TextItem(
                        title = { Text(stringResource(Lang.settings_media_torrent_peer_filter_ip_blacklist)) },
                        description = {
                            Text(
                                stringResource(
                                    Lang.settings_media_torrent_peer_filter_ip_blacklist_description,
                                ),
                            )
                        },
                        action = {
                            IconButton(onClickIpBlockSettings) {
                                Icon(Icons.Rounded.ArrowOutward, null)
                            }
                        },
                        onClick = onClickIpBlockSettings,
                    )
                }
            }

            TextItem {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End,
                ) {
                    ProvideTextStyleContentColor(
                        MaterialTheme.typography.labelMedium,
                        MaterialTheme.colorScheme.outline,
                    ) {
                        Text(stringResource(Lang.settings_media_torrent_peer_filter_auto_save_hint))
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun PreviewPeerFilterEditPane() {
    val config = remember { mutableStateOf(TorrentPeerConfig.Default) }
    val scope = rememberBackgroundScope()
    val state = remember {
        PeerFilterSettingsState(
            MutableStateFlow(emptyList()),
            SaveableStorage(config, { config.value = it }, MutableStateFlow(false)),
            scope.backgroundScope,
            { },
            { _, _ -> },
        )
    }
    PeerFilterEditPane(
        state = state,
        showIpBlockingItem = true,
        onClickIpBlockSettings = { },
    )
}