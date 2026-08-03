/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.rememberTvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor

/** 设置页焦点锚点 (统一焦点框架, 见 ui-foundation-tv/focus). */
private enum class TvSettingsFocus : TvFocusKey {
    /** 第一个开关项 (进页初始焦点). */
    FirstItem,
}

/**
 * TV 设置子集 (atv-architecture.md §7.6, M3 精简版).
 * 未覆盖的配置显示「请在手机端配置」占位 (§6.3).
 */
@Composable
fun TvSettingsScreen(
    viewModel: TvSettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val danmakuEnabled by viewModel.danmakuEnabled.collectAsState()
    val videoConfig by viewModel.videoConfig.collectAsState()

    // 统一焦点框架: 进页初始焦点落第一个开关项
    val focus = rememberTvFocusScope()
    focus.Resolver()
    focus.InitialFocus(TvSettingsFocus.FirstItem)

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.displaySmall)

        SectionTitle("弹幕")
        ToggleItem(
            "显示弹幕", danmakuEnabled,
            modifier = Modifier.tvFocusAnchor(focus, TvSettingsFocus.FirstItem),
        ) { viewModel.toggleDanmakuEnabled() }

        SectionTitle("播放")
        ToggleItem("自动连播", videoConfig?.autoPlayNext) { viewModel.toggleAutoPlayNext() }
        ToggleItem("自动跳过 OP/ED", videoConfig?.autoSkipOpEd) { viewModel.toggleAutoSkipOpEd() }
        ToggleItem("播放出错时自动换源", videoConfig?.autoSwitchMediaOnPlayerError) {
            viewModel.toggleAutoSwitchMediaOnError()
        }

        SectionTitle("其他")
        ListItem(
            selected = false,
            onClick = {},
            headlineContent = { Text("数据源 / 代理 / 主题 / 弹幕高级设置") },
            supportingContent = { Text("请在手机端配置 (与 TV 端独立存储)") },
        )
        ListItem(
            selected = false,
            onClick = {},
            headlineContent = { Text("版本") },
            supportingContent = { Text("Animeko TV ${currentAniBuildConfig.versionName}") },
        )
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(top = 18.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ToggleItem(
    title: String,
    checked: Boolean?,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
) {
    ListItem(
        selected = false,
        onClick = onToggle,
        modifier = modifier.fillMaxWidth(),
        headlineContent = { Text(title) },
        trailingContent = {
            Text(
                when (checked) {
                    true -> "已开启"
                    false -> "已关闭"
                    null -> "…"
                },
                color = if (checked == true) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
    )
}
