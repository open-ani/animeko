/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.wizard

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp", showSystemUi = false)
@Preview(
    showBackground = true,
    device = "spec:width=1920px,height=1080px,dpi=240",
    showSystemUi = false
)
@Composable
fun PreviewOnboardingCompleteScene() {
    ProvideCompositionLocalsForPreview {
        OnboardingCompleteScreen(
            state = OnboardingCompleteState("SG", ""),
            onClickContinue = { },
            backNavigation = { }
        )
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp", showSystemUi = false)
@Preview(
    showBackground = true,
    device = "spec:width=1920px,height=1080px,dpi=240",
    showSystemUi = false
)
@Composable
fun PreviewOnboardingCompleteSceneLongUsername() {
    ProvideCompositionLocalsForPreview {
        OnboardingCompleteScreen(
            state = OnboardingCompleteState("SG has long username12312321321321321", ""),
            onClickContinue = { },
            backNavigation = { }
        )
    }
}