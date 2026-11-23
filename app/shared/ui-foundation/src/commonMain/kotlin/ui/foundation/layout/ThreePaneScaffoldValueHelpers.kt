/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.layout

import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldValue
import androidx.compose.runtime.Stable

/**
 * 调整 [ListDetailPaneScaffold] 的行为
 */
@Stable
sealed class ThreePaneScaffoldValueConverter {
    abstract fun convert(value: ThreePaneScaffoldValue): ThreePaneScaffoldValue

    /**
     * 当 navigate 到 [ListDetailPaneScaffoldRole.Extra] 时,
     * 在屏幕左边显示 [ListDetailPaneScaffoldRole.Detail] 而不是默认的 [ListDetailPaneScaffoldRole.List].
     *
     * 适合显示二级详情: Extra pane 作为 Detail pane 的扩展, 例如 detail pane 中有一些卡片, 点击卡片显示详情.
     */
    @Stable
    data object ExtraPaneForNestedDetails : ThreePaneScaffoldValueConverter() {
        override fun convert(value: ThreePaneScaffoldValue): ThreePaneScaffoldValue {
            return if (value.tertiary == PaneAdaptedValue.Expanded && value.secondary == PaneAdaptedValue.Expanded) {
                // 手机上三级导航, PC 上将 detail pane (test) 移动到左边, 隐藏 list (edit)
                ThreePaneScaffoldValue(
                    primary = PaneAdaptedValue.Expanded, // detail
                    secondary = PaneAdaptedValue.Hidden, // list
                    tertiary = PaneAdaptedValue.Expanded,
                )
            } else {
                value
            }
        }
    }
}

/**
 * @see ThreePaneScaffoldValueConverter
 */
@Stable
fun ThreePaneScaffoldValue.convert(
    converter: ThreePaneScaffoldValueConverter,
): ThreePaneScaffoldValue = converter.convert(this@convert)

/**
 * 关闭 `adaptive 1.3` 默认开启的 [PaneScaffoldDirective.shouldAutoFocusCurrentDestination].
 *
 * 开启该项会在进入或切换 pane 时对整个 pane 调用 `requestFocus()`;
 * 触摸模式下 `clickable`/`Switch` 等控件不可聚焦, 焦点会跳过页面上方内容落到首个 `Slider`/`RangeSlider`/`TextField` 上并触发滚屏或弹键盘.
 */
@Stable
fun PaneScaffoldDirective.withoutAutoFocus(): PaneScaffoldDirective = PaneScaffoldDirective(
    maxHorizontalPartitions = maxHorizontalPartitions,
    horizontalPartitionSpacerSize = horizontalPartitionSpacerSize,
    maxVerticalPartitions = maxVerticalPartitions,
    verticalPartitionSpacerSize = verticalPartitionSpacerSize,
    defaultPanePreferredWidth = defaultPanePreferredWidth,
    defaultPanePreferredHeight = defaultPanePreferredHeight,
    excludedBounds = excludedBounds,
    shouldAutoFocusCurrentDestination = false,
)
