/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.user.calculateDisplay
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.avatar.AvatarImage
import me.him188.ani.app.ui.foundation.text.ProvideTextStyleContentColor
import me.him188.ani.app.ui.user.SelfInfoUiState
import me.him188.ani.app.ui.user.TestSelfInfoUiState
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * 显示当前登录的用户信息的横幅, 或者是一个登录按钮.
 */
@Composable
internal fun SelfInfoBanner(
    state: SelfInfoUiState,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface
) {
    val isLogin = remember(state) { state.isSessionValid == true }

    Surface(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier,
        color = containerColor,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = CenterVertically) {
            if (!isLogin) {
                FilledTonalButton(onLoginClick, Modifier.fillMaxWidth()) {
                    Text("登录 / 注册")
                }
            } else {
                AvatarImage(
                    state.selfInfo?.avatarUrl?.takeIf { isLogin },
                    Modifier
                        .clip(CircleShape)
                        .size(64.dp)
                        .placeholder(visible = state.isSessionValid == null),
                )

                Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val (title, subtitle) = state.selfInfo.calculateDisplay()
                    ProvideTextStyleContentColor(
                        MaterialTheme.typography.titleMedium,
                        MaterialTheme.colorScheme.onSurface,
                    ) {
                        Text(
                            title,
                            modifier = Modifier.placeholder(visible = state.isSessionValid == null),
                        )
                    }

                    if (subtitle.isNotBlank()) {
                        ProvideTextStyleContentColor(
                            MaterialTheme.typography.bodyMedium,
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        ) {
                            Text(
                                subtitle,
                                modifier = Modifier.placeholder(visible = state.isSessionValid == null),
                            )
                        }
                    }
                }
            }
        }
    }
}


@OptIn(TestOnly::class)
@Composable
@Preview
private fun PreviewSelfInfoBanner() = ProvideCompositionLocalsForPreview {
    Surface {
        SelfInfoBanner(
            state = TestSelfInfoUiState,
            true,
            {},
            {},
        )
    }
}