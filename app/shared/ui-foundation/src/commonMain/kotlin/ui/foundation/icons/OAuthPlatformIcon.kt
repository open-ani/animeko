/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.icons

import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.him188.ani.app.domain.session.auth.OAuthPlatform

/**
 * 第三方登录平台的标志. Bangumi 使用品牌色, 其他平台按内容颜色着色.
 */
@Composable
fun OAuthPlatformIcon(
    platform: OAuthPlatform,
    modifier: Modifier = Modifier,
) {
    when (platform) {
        OAuthPlatform.BANGUMI -> Image(Icons.Default.BangumiNext, contentDescription = platform.displayName, modifier)
        OAuthPlatform.GITHUB -> Icon(AniIcons.GithubMark, contentDescription = platform.displayName, modifier)
    }
}
