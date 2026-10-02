/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.him188.ani.app.domain.session.auth.OAuthPlatform
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.icons.OAuthPlatformIcon
import me.him188.ani.app.ui.foundation.text.ProvideTextStyleContentColor
import me.him188.ani.app.ui.lang.*
import org.jetbrains.compose.resources.*


/**
 * "其他登录方式" 的按钮列表. [platforms] 为空时什么都不显示.
 */
@Composable
internal fun ThirdPartyLoginMethods(
    platforms: List<OAuthPlatform>,
    onClick: (OAuthPlatform) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (platforms.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextDivider(
            modifier = Modifier.heightIn(min = 56.dp),
        ) {
            Text(stringResource(Lang.login_other_methods))
        }

        for (platform in platforms) {
            FilledTonalButton(
                { onClick(platform) },
                Modifier.fillMaxWidth().testTag("thirdPartyLogin-${platform.id}"),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                OAuthPlatformIcon(platform, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(platform.displayName)
            }
        }
    }
}

@Composable
private fun TextDivider(
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleSmall,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    Surface(color = containerColor) {
        Box(modifier, contentAlignment = Alignment.Center) {
            HorizontalDivider()
            Row(
                Modifier.background(color = containerColor).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProvideTextStyleContentColor(textStyle, color = MaterialTheme.colorScheme.onSurfaceVariant) {
                    content()
                }
            }
        }
    }
}

@Composable
@Preview
private fun PreviewThirdPartyLoginMethods() = ProvideCompositionLocalsForPreview {
    Surface {
        ThirdPartyLoginMethods(listOf(OAuthPlatform.BANGUMI, OAuthPlatform.GITHUB), onClick = {})
    }
}
