/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache.details

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.datasources.mikan.MikanCNMediaSource
import me.him188.ani.utils.platform.annotations.TestOnly

@OptIn(TestOnly::class)
@Composable
@Preview
fun PreviewCacheGroupDetailsColumn() = ProvideCompositionLocalsForPreview {
    MediaDetailsLazyGrid(
        TestMediaList[0],
        MikanCNMediaSource.INFO,
    )
}
