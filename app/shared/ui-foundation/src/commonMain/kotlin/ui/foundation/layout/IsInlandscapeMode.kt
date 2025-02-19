package me.him188.ani.app.ui.foundation.layout

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.window.currentWindowConfiguration


/**
 * 横屏模式. 横屏模式不一定是全屏.
 *
 * PC 一定处于横屏模式.
 *
 */
@Composable
inline fun isInLandscapeMode(): Boolean = currentWindowConfiguration().isLandscape

@Stable
fun BoxWithConstraintsScope.showTabletUI(): Boolean {
    // https://android-developers.googleblog.com/2023/06/detecting-if-device-is-foldable-tablet.html
    // 99.96% of phones have a built-in screen with a width smaller than 600dp when in portrait, 
    // but that same screen size could be the result of a freeform/split-screen window on a tablet or desktop device.

    return maxWidth >= 600.dp && maxHeight >= 600.dp
}
