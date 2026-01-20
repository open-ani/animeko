package me.him188.ani.app.ui.foundation

import androidx.compose.runtime.Composable
import me.him188.ani.utils.platform.Platform

@Composable
actual fun Platform.isTv(): Boolean = false
