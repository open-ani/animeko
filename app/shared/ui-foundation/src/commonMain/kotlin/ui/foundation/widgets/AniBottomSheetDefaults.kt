/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.widgets

import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import me.him188.ani.app.ui.foundation.LocalAniUiBehavior

object AniBottomSheetDefaults {
    /**
     * [ModalBottomSheet][androidx.compose.material3.ModalBottomSheet] 的最大宽度.
     *
     * 默认用 M3 的 640.dp; 声明了 [AniUiBehavior.sheetMaxWidthFraction][me.him188.ani.app.ui.foundation.AniUiBehavior.sheetMaxWidthFraction]
     * 的设备按窗口宽度的比例算, 让自适应内容 (如详情页) 按宽布局渲染.
     */
    @Composable
    fun sheetMaxWidth(): Dp {
        val fraction = LocalAniUiBehavior.current.sheetMaxWidthFraction
            ?: return BottomSheetDefaults.SheetMaxWidth
        val containerWidthPx = LocalWindowInfo.current.containerSize.width
        return with(LocalDensity.current) { (containerWidthPx * fraction).toDp() }
    }
}
