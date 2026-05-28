package me.him188.ani.app.ui.foundation.navigation

import androidx.navigation.NavHostController as JBNavHostController
import me.him188.ani.app.platform.Context

@Suppress("FunctionName")
actual fun NavHostController(context: Context): JBNavHostController = JBNavHostController()
