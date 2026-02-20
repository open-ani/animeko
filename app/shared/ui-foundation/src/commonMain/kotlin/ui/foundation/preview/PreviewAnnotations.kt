/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.preview

import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "Compact")
@Preview(
    name = "Medium",
    device = "spec:width=800dp,height=800dp,dpi=240",
)
@Preview(
    name = "Expanded",
    device = "spec:width=1200dp,height=800dp,dpi=240",
)
annotation class PreviewSizeClasses
