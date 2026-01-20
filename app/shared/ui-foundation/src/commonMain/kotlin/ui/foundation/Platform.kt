package me.him188.ani.app.ui.foundation

import androidx.compose.runtime.Composable
import me.him188.ani.utils.platform.Platform

/**
 * Returns `true` if the current platform is a TV.
 */
@Composable
expect fun Platform.isTv(): Boolean
