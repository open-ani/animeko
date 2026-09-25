/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.video.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.episode.danmaku.DanmakuServiceIcon
import me.him188.ani.app.ui.episode.danmaku.DanmakuSourceItem
import me.him188.ani.app.ui.episode.danmaku.DanmakuSourceSettingsDropdown
import me.him188.ani.app.ui.episode.danmaku.DanmakuTimeShiftDialog
import me.him188.ani.app.ui.episode.danmaku.formatDanmakuShiftMillis
import me.him188.ani.app.ui.episode.danmaku.renderDanmakuMatchMethod
import me.him188.ani.app.ui.episode.danmaku.renderDanmakuServiceId
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.subject_episode_danmaku_disabled
import me.him188.ani.app.ui.lang.subject_episode_danmaku_sources_empty
import me.him188.ani.app.ui.lang.subject_episode_danmaku_sources_title
import me.him188.ani.app.ui.lang.subject_episode_danmaku_time_shift_item
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.components.TextItem
import me.him188.ani.danmaku.api.DanmakuServiceId
import org.jetbrains.compose.resources.stringResource

/** 播放器设置中的弹幕来源管理。配置由播放会话持有，组件仅保存正在编辑的来源。 */
@Composable
fun DanmakuSourceSettings(
    sourceItems: List<DanmakuSourceItem>,
    isLoading: Boolean,
    onSetEnabled: (DanmakuServiceId, Boolean) -> Unit,
    onManualMatch: (DanmakuServiceId) -> Unit,
    onAdjustShift: (DanmakuServiceId, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingServiceId by rememberSaveable { mutableStateOf<String?>(null) }
    SettingsTab(modifier.testTag("danmaku-source-settings")) {
        Column {
            TextItem { Text(stringResource(Lang.subject_episode_danmaku_sources_title)) }
            if (sourceItems.isNotEmpty()) {
                sourceItems.forEach { source ->
                    key(source.serviceId) {
                        var showMenu by rememberSaveable { mutableStateOf(false) }
                        Box {
                            TextItem(
                                modifier = Modifier.testTag("danmaku-source-${source.serviceId.value}"),
                                title = { Text(renderDanmakuServiceId(source.serviceId)) },
                                description = {
                                    val status = if (source.enabled) {
                                        stringResource(Lang.subject_episode_danmaku_time_shift_item, formatDanmakuShiftMillis(source.shiftMillis))
                                    } else stringResource(Lang.subject_episode_danmaku_disabled)
                                    val match = if (source.isExactMatch) "" else " · ${renderDanmakuMatchMethod(source.matchMethod)}"
                                    Text("${source.count} · $status$match")
                                },
                                icon = { DanmakuServiceIcon(source.serviceId, size = 24) },
                                action = { Icon(Icons.Outlined.MoreVert, contentDescription = null) },
                                onClick = { showMenu = true },
                            )
                            DanmakuSourceSettingsDropdown(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                enabled = source.enabled,
                                onSetEnabled = { onSetEnabled(source.serviceId, it) },
                                currentShiftMillis = source.shiftMillis,
                                onClickAdjustShift = { editingServiceId = source.serviceId.value },
                                onClickChange = if (source.serviceId == DanmakuServiceId.Animeko) null
                                else ({ onManualMatch(source.serviceId) }),
                                serviceId = source.serviceId,
                            )
                        }
                    }
                }
            } else if (isLoading) {
                CircularProgressIndicator(Modifier.padding(16.dp).testTag("danmaku-sources-loading"))
            } else {
                TextItem { Text(stringResource(Lang.subject_episode_danmaku_sources_empty)) }
            }
        }
    }

    sourceItems.find { it.serviceId.value == editingServiceId }?.let { source ->
        key(source.serviceId) {
            DanmakuTimeShiftDialog(
                serviceName = renderDanmakuServiceId(source.serviceId),
                currentShiftMillis = source.shiftMillis,
                onDismissRequest = { editingServiceId = null },
                onConfirm = {
                    onAdjustShift(source.serviceId, it)
                    editingServiceId = null
                },
            )
        }
    }
}
