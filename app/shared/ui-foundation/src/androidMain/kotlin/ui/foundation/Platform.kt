package me.him188.ani.app.ui.foundation

import android.app.UiModeManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import me.him188.ani.utils.platform.Platform

@Composable
actual fun Platform.isTv(): Boolean {
    val context = LocalContext.current
    val uiModeManager = context.getSystemService(UiModeManager::class.java)
    return (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) ?: false
}
