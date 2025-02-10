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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.animation.LocalAniMotionScheme
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
    var isContentReady by rememberSaveable {
        mutableStateOf(false)
    }
    SideEffect {
        isContentReady = true
    }
    
    Box(
        modifier,
        contentAlignment = Alignment.Center,
    ) {
        AniAnimatedVisibility(
            isContentReady,
            Modifier.wrapContentSize(),
            // 从中间往上滑
            enter = LocalAniMotionScheme.current.animatedVisibility.screenEnter,
            exit = LocalAniMotionScheme.current.animatedVisibility.screenExit,
        ) {
            Column(
                Modifier
                    .windowInsetsPadding(windowInsets)
                    .padding(
                        horizontal = layoutParams.horizontalPadding,
                        vertical = layoutParams.verticalPadding,
                    )
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("欢迎使用 Animeko", style = MaterialTheme.typography.headlineMedium)

                    ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
                        Row(Modifier.padding(top = 8.dp).align(Alignment.Start)) {
                            Text(
                                """一站式在线弹幕追番平台 (简称 Ani)""",
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    Column(
                        Modifier.padding(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                            Text("""Ani 目前由爱好者组成的组织 OpenAni 和社区贡献者维护，完全免费，在 GitHub 上开源。""")

                            Text("""Ani 的目标是提供尽可能简单且舒适的追番体验。""")
                        }
                    }

                    contactActions()
                }

                Box(
                    modifier = Modifier
                        .padding(horizontal = 64.dp)
                        .padding(top = 16.dp, bottom = 36.dp),
                ) {
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
}