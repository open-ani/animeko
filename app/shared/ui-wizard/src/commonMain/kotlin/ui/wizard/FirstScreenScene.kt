/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets

/**
 * 首次启动 APP 的欢迎界面, 在向导之前显示.
 */
@Composable
internal fun FirstScreenScene(
    onLinkStart: () -> Unit,
    contactActions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
    layoutParams: WizardLayoutParams = WizardLayoutParams.Default
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(windowInsets)
                .padding(
                    horizontal = layoutParams.horizontalPadding,
                    vertical = layoutParams.verticalPadding,
                )
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "欢迎使用 Animeko",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "一站式在线弹幕追番平台 (简称 Ani)",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),

                ) {
                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.bodyMedium,
                ) {
                    Text("Ani 由爱好者组成的组织 OpenAni 和社区贡献者维护，完全免费，在 GitHub 上开源。")
                    Text("Ani 的目标是提供尽可能简单且舒适的追番体验。")
                }
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                contactActions()
            }

            Box(modifier = Modifier.padding(horizontal = 64.dp, vertical = 32.dp)) {
                Button(
                    onClick = onLinkStart,
                    modifier = Modifier.widthIn(300.dp),
                ) {
                    Text("继续")
                }
            }
        }
    }
}