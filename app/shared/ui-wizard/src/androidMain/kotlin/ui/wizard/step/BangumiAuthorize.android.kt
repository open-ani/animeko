/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.wizard.step

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import me.him188.ani.app.ui.foundation.ProvideFoundationCompositionLocalsForPreview

@Preview(showBackground = true)
@Composable
fun PreviewBangumiAuthorizeStepInitial() {
    ProvideFoundationCompositionLocalsForPreview {
        BangumiAuthorize(
            authorizeState = AuthorizeUIState.Idle,
            contactActions = { },
            onClickAuthorize = { },
            onCancelAuthorize = { },
            onAuthorizeViaToken = { },
            onClickNavigateToBangumiDev = { },
            onRefreshAuthorizeStatus = { },
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewBangumiAuthorizeStepAwaitingResult() {
    ProvideFoundationCompositionLocalsForPreview {
        BangumiAuthorize(
            authorizeState = AuthorizeUIState.AwaitingResult(""),
            contactActions = { },
            onClickAuthorize = { },
            onCancelAuthorize = { },
            onAuthorizeViaToken = { },
            onClickNavigateToBangumiDev = { },
            onRefreshAuthorizeStatus = { },
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewBangumiAuthorizeStepError() {
    ProvideFoundationCompositionLocalsForPreview {
        BangumiAuthorize(
            authorizeState = AuthorizeUIState.Error("", "error message"),
            contactActions = { },
            onClickAuthorize = { },
            onCancelAuthorize = { },
            onAuthorizeViaToken = { },
            onClickNavigateToBangumiDev = { },
            onRefreshAuthorizeStatus = { },
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewBangumiAuthorizeStepSuccess() {
    ProvideFoundationCompositionLocalsForPreview {
        BangumiAuthorize(
            authorizeState = AuthorizeUIState.Success(
                "StageGuard has long username",
                "https://lain.bgm.tv/pic/cover/l/44/7d/467461_HHw4K.jpg",
            ),
            onClickAuthorize = { },
            onAuthorizeViaToken = { },
            onCancelAuthorize = { },
            contactActions = { },
            onClickNavigateToBangumiDev = { },
            onRefreshAuthorizeStatus = { },
        )
    }
}