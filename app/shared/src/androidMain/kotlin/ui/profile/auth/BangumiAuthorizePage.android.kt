/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.profile.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import me.him188.ani.app.domain.session.AuthState
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview

@PreviewLightDark
@Composable
private fun PreviewBangumiAuthorizePage() {
    ProvideCompositionLocalsForPreview {
        BangumiAuthorizePage(
            authState = AuthState.Idle,
            onCheckCurrentToken = { },
            onClickNavigateAuthorize = { },
            onCancelAuthorize = { },
            onAuthorizeByToken = { },
            onClickNavigateToBangumiDev = { },
            onClickBack = { },
        )
    }
}